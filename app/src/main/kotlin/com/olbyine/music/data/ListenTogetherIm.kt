package com.olbyine.music.data

import android.content.Context
import com.netease.nimlib.sdk.AbortableFuture
import com.netease.nimlib.sdk.NIMClient
import com.netease.nimlib.sdk.Observer
import com.netease.nimlib.sdk.RequestCallback
import com.netease.nimlib.sdk.SDKOptions
import com.netease.nimlib.sdk.StatusCode
import com.netease.nimlib.sdk.auth.AuthService
import com.netease.nimlib.sdk.auth.AuthServiceObserver
import com.netease.nimlib.sdk.auth.LoginInfo
import com.netease.nimlib.sdk.chatroom.ChatRoomMessageBuilder
import com.netease.nimlib.sdk.chatroom.ChatRoomService
import com.netease.nimlib.sdk.chatroom.ChatRoomServiceObserver
import com.netease.nimlib.sdk.chatroom.model.ChatRoomMessage
import com.netease.nimlib.sdk.chatroom.model.EnterChatRoomData
import com.netease.nimlib.sdk.chatroom.model.EnterChatRoomResultData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ListenTogetherIm {
    private const val APP_KEY = "3a6a3e48f6854dfa4e4464f3bdaec3b4"
    const val TICKET_URL = "https://interface3.music.163.com/api/middle/im/token/get?bizName=music_listenTogether"
    const val BROWSER_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    data class Chat(val id: String, val from: String, val nick: String, val text: String, val time: Long, val mine: Boolean)

    data class Command(val songId: Long?, val playing: Boolean?, val progress: Long?, val seq: Long?, val type: String?, val from: String?)

    data class RoomEvent(
        val type: Int,
        val userId: Long,
        val nickname: String,
        val listenCount: Int,
        val duration: Long,
        val roomId: String,
        val unopenedUserIds: List<Long> = emptyList(),
        val encrypted: Boolean = false,
        val rtcType: String = "",
        val liveUserIds: List<Long> = emptyList(),
    )

    private val _messages = MutableStateFlow<List<Chat>>(emptyList())
    val messages: StateFlow<List<Chat>> = _messages
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    val commands = MutableSharedFlow<Command>(extraBufferCapacity = 64)
    val events = MutableSharedFlow<RoomEvent>(extraBufferCapacity = 16)

    private var chatRoomId: String? = null
    private var roomId: String? = null
    private var accountId: String? = null
    private var myUserId: Long = 0L
    private var myNick: String = ""
    private var myAvatar: String = ""
    private var observing = false
    private var statusObserving = false
    private var initialized = false
    private var loginState: Int = 0
    private var lastError: String = ""

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        runCatching {
            val root = java.io.File(context.applicationContext.filesDir, "nim").apply { mkdirs() }
            val options = SDKOptions.DEFAULT
            options.appKey = APP_KEY
            options.sdkStorageRootPath = root.absolutePath
            options.statusBarNotificationConfig = null
            options.checkManifestConfig = false
            options.preLoadServers = true
            NIMClient.init(context.applicationContext, null, options)
        }
        observe()
    }

    fun diagnostics(): String {
        val state = when (loginState) {
            1 -> "登录中"
            2 -> "已登录"
            3 -> "登录失败"
            else -> "未登录"
        }
        return buildString {
            append("IM 状态：").append(state)
            append("\n账号 accId：").append(accountId ?: "无")
            append("\nchatRoomId：").append(chatRoomId ?: "无")
            append("\n已进房：").append(_connected.value)
            append("\n收到消息：").append(_messages.value.size).append(" 条")
            if (lastError.isNotBlank()) append("\n最近错误：").append(lastError)
        }
    }

    fun connect(account: NeteaseClient.ListenImAccount, chatRoom: String, logicalRoom: String, me: NeteaseAccount?) {
        if (chatRoom.isBlank()) return
        myUserId = me?.userId ?: 0L
        myNick = me?.nickname.orEmpty()
        myAvatar = me?.avatarUrl.orEmpty()
        if (_connected.value && chatRoomId == chatRoom) return
        chatRoomId = chatRoom
        roomId = logicalRoom
        accountId = account.accId
        runCatching {
            observe()
            val login = NIMClient.getService(AuthService::class.java).login(LoginInfo(account.accId, account.token))
            if (login != null) login.setCallback(object : RequestCallback<LoginInfo> {
                override fun onSuccess(result: LoginInfo?) = enter(myNick, myAvatar)
                override fun onFailed(code: Int) {
                    lastError = "IM 登录失败，错误码 $code"
                    loginState = 3
                }

                override fun onException(exception: Throwable?) {
                    lastError = "IM 登录异常：${exception?.message ?: exception?.javaClass?.simpleName ?: "未知"}"
                    loginState = 3
                }
            })
        }
    }

    private fun enter(nick: String?, avatar: String?) {
        val room = chatRoomId ?: return
        runCatching {
            val data = EnterChatRoomData(room)
            val shownNick = nick?.takeIf { it.isNotBlank() } ?: myNick.takeIf { it.isNotBlank() }
            val shownAvatar = avatar?.takeIf { it.isNotBlank() } ?: myAvatar.takeIf { it.isNotBlank() }
            if (!shownNick.isNullOrBlank()) data.nick = shownNick
            if (!shownAvatar.isNullOrBlank()) data.avatar = shownAvatar
            val future: AbortableFuture<EnterChatRoomResultData> = NIMClient.getService(ChatRoomService::class.java).enterChatRoom(data)
            future.setCallback(object : RequestCallback<EnterChatRoomResultData> {
                override fun onSuccess(result: EnterChatRoomResultData?) {
                    _connected.value = true
                    lastError = ""
                    history()
                }

                override fun onFailed(code: Int) {
                    lastError = "进入房间失败，错误码 $code"
                    _connected.value = false
                }

                override fun onException(exception: Throwable?) {
                    lastError = "进入房间异常：${exception?.message ?: exception?.javaClass?.simpleName ?: "未知"}"
                    _connected.value = false
                    relogin()
                }
            })
        }
    }

    fun relogin() {
        val chatRoom = chatRoomId ?: return
        _connected.value = false
        runCatching {
            NIMClient.getService(AuthService::class.java).logout()
            pendingRelogin = true
        }
        pendingChatRoom = chatRoom
    }

    private var pendingRelogin = false
    private var pendingChatRoom: String? = null

    fun resumePendingLogin() {
        if (!pendingRelogin) return
        pendingRelogin = false
        val chatRoom = pendingChatRoom ?: chatRoomId ?: return
        runCatching { NIMClient.getService(ChatRoomService::class.java).exitChatRoom(chatRoom) }
    }

    private fun history() {
        val room = chatRoomId ?: return
        runCatching {
            NIMClient.getService(ChatRoomService::class.java).pullMessageHistory(room, System.currentTimeMillis(), 30)
                .setCallback(object : RequestCallback<List<ChatRoomMessage>> {
                    override fun onSuccess(result: List<ChatRoomMessage>?) {
                        result.orEmpty().sortedBy { it.time }.forEach(::handle)
                    }

                    override fun onFailed(code: Int) = Unit
                    override fun onException(exception: Throwable?) = Unit
                })
        }
    }

    private fun observe() {
        runCatching {
            NIMClient.getService(ChatRoomServiceObserver::class.java)
                .observeReceiveMessage(Observer<List<ChatRoomMessage>> { list -> list?.forEach(::handle) }, true)
        }
        if (statusObserving) return
        statusObserving = true
        runCatching {
            NIMClient.getService(AuthServiceObserver::class.java)
                .observeOnlineStatus(Observer<StatusCode> { code ->
                    loginState = when (code) {
                        StatusCode.LOGINED -> 2
                        StatusCode.LOGINING, StatusCode.SYNCING, StatusCode.CONNECTING -> 1
                        StatusCode.PWD_ERROR, StatusCode.FORBIDDEN, StatusCode.VER_ERROR, StatusCode.KICKOUT,
                        StatusCode.KICK_BY_OTHER_CLIENT, StatusCode.DATA_UPGRADE -> 3
                        else -> if (_connected.value) 2 else 0
                    }
                    if (code != StatusCode.LOGINED && code != StatusCode.LOGINING) {
                        lastError = "IM 在线状态：${code.name}"
                    }
                }, true)
        }
    }

    private fun handle(message: ChatRoomMessage) {
        val from = message.fromAccount.orEmpty()
        val nick = message.fromNick.orEmpty()
        val time = if (message.time > 0) message.time else System.currentTimeMillis()
        val id = message.uuid.orEmpty()
        if (id.isNotBlank() && _messages.value.any { it.id == id }) return
        val protocol = protocolEnvelope(message)
        if (protocol != null) {
            val envelopeType = protocol.optInt("type", -1)
            if (envelopeType == 20_000 || protocol.optString("content").isNotEmpty()) {
                command(protocol, from)?.let { commands.tryEmit(it) }
            } else {
                roomEvent(envelopeType, protocol)
            }
            return
        }
        val type = runCatching { message.msgType }.getOrNull()
        if (type == com.netease.nimlib.sdk.msg.constant.MsgTypeEnum.notification ||
            type == com.netease.nimlib.sdk.msg.constant.MsgTypeEnum.robot
        ) return
        val shown = chatText(message)
        if (shown.isBlank()) return
        val mine = accountId != null && from == accountId
        if (mine) {
            val echoed = _messages.value.lastOrNull()?.takeIf { it.mine && it.text == shown && kotlin.math.abs(time - it.time) < 6_000L }
            if (echoed != null) return
        }
        _messages.value = (_messages.value + Chat(id, from, nick, shown, time, mine)).takeLast(200)
    }

    private fun chatText(message: ChatRoomMessage): String {
        val direct = runCatching { message.content }.getOrNull().orEmpty()
        if (direct.isNotBlank() && !isProtocolJson(direct)) return direct
        val raw = message.attachStr.orEmpty()
        if (raw.isBlank()) return ""
        val parsed = parseAny(raw) ?: return ""
        if (parsed is String) return parsed
        val object0 = parsed as? JSONObject ?: return ""
        val envelopeType = object0.optInt("type", -1)
        if (envelopeType >= 20_000 && envelopeType < 30_000) return ""
        for (key in listOf("text", "content", "body", "msg")) {
            val value = object0.optString(key)
            if (value.isNotBlank() && value != "null") return value
        }
        return ""
    }

    private fun protocolEnvelope(message: ChatRoomMessage): JSONObject? {
        val candidates = mutableListOf<String>()
        candidates.add(message.attachStr.orEmpty())
        candidates.add(runCatching { message.content }.getOrNull().orEmpty())
        runCatching { message.remoteExtension }.getOrNull()?.let { ext ->
            candidates.add(ext.toString())
            (ext["ext"] as? String)?.let { candidates.add(it) }
            (ext["clientExt"] as? String)?.let { candidates.add(it) }
        }
        for (raw in candidates) {
            if (raw.isBlank()) continue
            val parsed = parseAny(raw) as? JSONObject ?: continue
            val type = parsed.optInt("type", -1)
            val looksProtocol = (type in 20_000..29_999) && (parsed.has("content") || parsed.has("bizType"))
            if (looksProtocol) return parsed
            parsed.optJSONObject("content")?.let { nested ->
                val nestedType = nested.optInt("type", -1)
                if (nestedType in 20_000..29_999 && (nested.has("content") || nested.has("bizType"))) return nested
            }
        }
        return null
    }

    private fun isProtocolJson(text: String): Boolean {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) return false
        val parsed = parseAny(trimmed) as? JSONObject ?: return false
        val envelope = parsed.optInt("type", -1)
        return envelope in 20_000..29_999 && (parsed.has("content") || parsed.has("bizType"))
    }

    private fun parseAny(text: String): Any? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { org.json.JSONTokener(trimmed).nextValue() }.getOrNull()
    }

    private fun findEnvelope(value: Any?, depth: Int = 0): JSONObject? {
        if (depth > 7) return null
        val node = if (value is String) parseAny(value) else value
        if (node is JSONArray) {
            for (index in 0 until node.length()) {
                findEnvelope(node.opt(index), depth + 1)?.let { return it }
            }
            return null
        }
        val object0 = node as? JSONObject ?: return null
        val marker = if (object0.has("event_type")) object0.optInt("event_type") else object0.optInt("type", -1)
        if (marker == 20_000) return object0
        for (key in object0.keys()) {
            findEnvelope(object0.opt(key), depth + 1)?.let { return it }
        }
        return null
    }

    private fun findCommand(value: Any?, depth: Int = 0): JSONObject? {
        if (depth > 7) return null
        val node = if (value is String) parseAny(value) else value
        if (node is JSONArray) {
            for (index in 0 until node.length()) {
                findCommand(node.opt(index), depth + 1)?.let { return it }
            }
            return null
        }
        val object0 = node as? JSONObject ?: return null
        if (object0.has("commandType") || object0.has("playStatus") || object0.has("targetSongId") || object0.has("progress")) return object0
        for (key in object0.keys()) {
            findCommand(object0.opt(key), depth + 1)?.let { return it }
        }
        return null
    }

    private fun roomEvent(type: Int, envelope: JSONObject) {
        val content = envelope.optJSONObject("content") ?: JSONObject()
        val unopened = content.optJSONArray("unopenedUserIds")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).toLongOrNull() }
        }.orEmpty()
        events.tryEmit(
            RoomEvent(
                type = type,
                userId = content.optString("userId").toLongOrNull() ?: 0L,
                nickname = content.optString("nickname").orEmpty(),
                listenCount = content.optInt("listenCount", 0),
                duration = content.optString("duration").toLongOrNull() ?: 0L,
                roomId = content.optString("roomId").orEmpty(),
                unopenedUserIds = unopened,
                encrypted = content.has("roomKey") || content.has("encrypted"),
                rtcType = content.optString("roomRTCType").orEmpty(),
                liveUserIds = content.optJSONArray("liveUserIds")?.let { array ->
                    (0 until array.length()).mapNotNull { array.optString(it).toLongOrNull() }
                }.orEmpty(),
            ),
        )
    }

    private fun command(envelope: JSONObject, from: String): Command? {
        val body = findCommand(envelope) ?: return null
        val status = body.optString("playStatus").uppercase()
        val result = Command(
            songId = body.optString("targetSongId").toLongOrNull() ?: body.optString("songId").toLongOrNull(),
            playing = when {
                body.has("playing") -> body.optBoolean("playing")
                status == "PLAY" -> true
                status == "PAUSE" -> false
                else -> null
            },
            progress = body.optString("progress").toLongOrNull()?.takeIf { it >= 0 },
            seq = body.optString("serverSeq").toLongOrNull() ?: envelope.optString("serverSeq").toLongOrNull(),
            type = body.optString("commandType").takeIf { it.isNotBlank() && it != "null" }?.uppercase(),
            from = from,
        )
        if (result.songId == null && result.playing == null && result.progress == null) return null
        commands.tryEmit(result)
        return result
    }

    fun send(text: String) {
        val room = chatRoomId ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        runCatching {
            val logical = roomId.orEmpty()
            val clientMsgId = UUID.randomUUID().toString()
            val message = ChatRoomMessageBuilder.createChatRoomTextMessage(room, body)
            val senderId = myUserId.takeIf { it > 0L } ?: accountId?.toLongOrNull() ?: 0L
            val seed = System.currentTimeMillis()
            val serverExt = LinkedHashMap<String, Any>()
            serverExt["userId"] = senderId
            serverExt["nickname"] = myNick
            serverExt["avatarUrl"] = myAvatar
            serverExt["msgId"] = seed % 90_000_000_000L + 10_000_000_000L
            val ext = LinkedHashMap<String, Any>()
            ext["serverExt"] = serverExt
            ext["appName"] = "music"
            ext["clientExt"] = LinkedHashMap<String, Any>().apply {
                put("bizType", "listenTogether")
                put("ltType", "FRIEND")
                put("roomId", logical)
                put("clientMsgId", clientMsgId)
            }
            message.remoteExtension = ext
            NIMClient.getService(ChatRoomService::class.java).sendMessage(message, false)
            _messages.value = (_messages.value + Chat(clientMsgId, accountId.orEmpty(), "", body, System.currentTimeMillis(), true)).takeLast(200)
        }
    }

    fun disconnect() {
        runCatching { chatRoomId?.let { NIMClient.getService(ChatRoomService::class.java).exitChatRoom(it) } }
        runCatching { NIMClient.getService(AuthService::class.java).logout() }
        chatRoomId = null
        roomId = null
        accountId = null
        _connected.value = false
        _messages.value = emptyList()
    }
}
