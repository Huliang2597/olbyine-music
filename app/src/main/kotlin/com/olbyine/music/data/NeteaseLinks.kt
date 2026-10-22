package com.olbyine.music.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

internal object NeteaseLinks {
    private val URL_IN_TEXT = Regex("""https?://[^\s，,。、"“”'）)】\]]+""")

    fun extractUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("http", true)) return trimmed
        val match = URL_IN_TEXT.find(trimmed)?.value ?: return trimmed
        return match.trimEnd('.', '。', ',', '，', ')', '）', ']', '】')
    }

    fun resolve(raw: String): NeteaseTarget? {
        val url = extractUrl(raw)
        if (url.isBlank()) return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme == "orpheus") {
            uri.getQueryParameter("url")?.let { inner -> return resolve(inner) ?: NeteaseTarget.Web(inner) }
            val id = uri.pathSegments.firstOrNull()?.toLongOrNull() ?: uri.getQueryParameter("id")?.toLongOrNull()
            return when (uri.host?.lowercase()) {
                "song" -> id?.let { NeteaseTarget.Song(it) }
                "playlist" -> id?.let { NeteaseTarget.Playlist(it) }
                "album" -> id?.let { NeteaseTarget.Album(it) }
                "artist" -> id?.let { NeteaseTarget.Artist(it) }
                "user" -> id?.let { NeteaseTarget.User(it) }
                "mv", "video" -> id?.let { NeteaseTarget.Mv(it) }
                else -> null
            }
        }
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase().orEmpty()
        if (!host.endsWith("163.com") && !host.endsWith("126.net")) return NeteaseTarget.Web(url)
        if (url.contains("listen-together")) {
            val room = uri.getQueryParameter("roomId")
            val inviter = uri.getQueryParameter("inviterId")?.toLongOrNull()
            if (!room.isNullOrBlank() && inviter != null) return NeteaseTarget.ListenTogether(room, inviter)
        }
        val fragment = uri.fragment.orEmpty()
        val path = (uri.path.orEmpty() + if (fragment.startsWith("/")) fragment.substringBefore('?') else "").lowercase()
        val query = if (fragment.contains('?')) Uri.parse("x://x?" + fragment.substringAfter('?')) else uri
        val id = query.getQueryParameter("id")?.toLongOrNull() ?: uri.getQueryParameter("id")?.toLongOrNull()
            ?: Regex("/(song|playlist|album|artist|user|mv)/(\\d+)").find(path)?.groupValues?.get(2)?.toLongOrNull()
        val kind = Regex("(song|playlist|album|artist|user/home|user|mv)(?:/|\\?|$)").findAll(path).lastOrNull()?.groupValues?.get(1)
        if (id != null) when (kind) {
            "song" -> return NeteaseTarget.Song(id)
            "playlist" -> return NeteaseTarget.Playlist(id)
            "album" -> return NeteaseTarget.Album(id)
            "artist" -> return NeteaseTarget.Artist(id)
            "user", "user/home" -> return NeteaseTarget.User(id)
            "mv" -> return NeteaseTarget.Mv(id)
        }
        return NeteaseTarget.Web(url)
    }

    fun card(body: JSONObject): NeteaseCard? {
        val room = findAny(body, listOf("roomId", "roomNo"))
        val inviter = findAny(body, listOf("inviterId", "inviterUid", "creatorId", "fromUserId"))?.toLongOrNull()
            ?: body.optJSONObject("fromUser")?.optLong("userId")?.takeIf { it > 0 }
        if (!room.isNullOrBlank() && inviter != null) {
            val song = findAny(body, listOf("songName", "name", "title"))
            return NeteaseCard(
                title = "邀请你一起听" + (song?.takeIf { it != body.optString("msg") }?.let { "《$it》" } ?: ""),
                subtitle = "点击加入房间，和对方同步收听",
                imageUrl = find(body, IMAGE_KEYS),
                target = NeteaseTarget.ListenTogether(room, inviter),
                label = "一起听",
            )
        }
        body.optJSONObject("song")?.let { song ->
            val artists = (song.optJSONArray("artists") ?: song.optJSONArray("ar")).names()
            val album = song.optJSONObject("album") ?: song.optJSONObject("al")
            return NeteaseCard(song.optString("name"), artists, album?.optString("picUrl")?.clean(), song.optLong("id").takeIf { it > 0 }?.let { NeteaseTarget.Song(it) }, "歌曲")
        }
        body.optJSONObject("playlist")?.let { list ->
            return NeteaseCard(
                list.optString("name"),
                "by " + list.optJSONObject("creator")?.optString("nickname").orEmpty() + " · ${list.optInt("trackCount")} 首",
                list.optString("coverImgUrl").clean(),
                list.optLong("id").takeIf { it > 0 }?.let { NeteaseTarget.Playlist(it) },
                "歌单",
            )
        }
        body.optJSONObject("album")?.let { album ->
            val artist = album.optJSONObject("artist")?.optString("name") ?: album.optJSONArray("artists").names()
            return NeteaseCard(album.optString("name"), artist.orEmpty(), album.optString("picUrl").clean(), album.optLong("id").takeIf { it > 0 }?.let { NeteaseTarget.Album(it) }, "专辑")
        }
        (body.optJSONObject("mv") ?: body.optJSONObject("video"))?.let { mv ->
            val id = mv.optLong("id")
            return NeteaseCard(mv.optString("name").ifBlank { mv.optString("title") }, mv.optJSONArray("artists").names(), (mv.optString("imgurl").clean() ?: mv.optString("coverUrl").clean()),
                if (id > 0) NeteaseTarget.Mv(id) else null, "视频")
        }
        val url = find(body, URL_KEYS)
        val title = find(body, TITLE_KEYS)
        val text = find(body, TEXT_KEYS)?.takeIf { it != title && it != body.optString("msg") }
        val image = find(body, IMAGE_KEYS)
        if (url == null && image == null && (title == null || title == body.optString("msg"))) return null
        val target = url?.let { resolve(it) }
        return NeteaseCard(
            title = title ?: text ?: "查看详情",
            subtitle = if (title != null) text.orEmpty() else "",
            imageUrl = image,
            target = target,
            label = if (target is NeteaseTarget.ListenTogether) "一起听" else "通知",
        )
    }

    private val URL_KEYS = listOf("url", "webUrl", "targetUrl", "jumpUrl", "link", "linkUrl", "orpheusUrl", "promotionUrl", "nativeUrl", "h5Url")
    private val TITLE_KEYS = listOf("title", "mainTitle", "noticeTitle", "name")
    private val TEXT_KEYS = listOf("text", "content", "subTitle", "subtitle", "desc", "description", "msg")
    private val IMAGE_KEYS = listOf("picUrl", "coverUrl", "imgUrl", "imageUrl", "image", "img", "cover", "pic")

    private fun findAny(root: JSONObject, keys: List<String>): String? {
        var level = listOf(root)
        repeat(4) {
            for (key in keys) for (node in level) {
                val value = node.opt(key)
                if (value is String && value.isNotBlank() && value != "null" && value != "0") return value
                if (value is Number && value.toLong() != 0L) return value.toLong().toString()
            }
            level = level.flatMap { node ->
                node.keys().asSequence().mapNotNull { key ->
                    when (val child = node.opt(key)) {
                        is JSONObject -> child
                        is String -> child.takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() }
                        is JSONArray -> child.optJSONObject(0)
                        else -> null
                    }
                }.toList()
            }
            if (level.isEmpty()) return null
        }
        return null
    }

    private fun find(root: JSONObject, keys: List<String>): String? {
        var level = listOf(root)
        repeat(4) {
            for (key in keys) for (node in level) {
                val value = node.opt(key)
                if (value is String) {
                    val clean = value.trim()
                    if (clean.isNotBlank() && clean != "null" && !(clean.startsWith("{") && clean.endsWith("}"))) {
                        return if (key in IMAGE_KEYS) clean.clean() ?: continue else clean
                    }
                }
            }
            level = level.flatMap { node ->
                node.keys().asSequence().mapNotNull { key ->
                    when (val child = node.opt(key)) {
                        is JSONObject -> child
                        is String -> child.takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() }
                        is JSONArray -> child.optJSONObject(0)
                        else -> null
                    }
                }.toList()
            }
            if (level.isEmpty()) return null
        }
        return null
    }

    private fun JSONArray?.names(): String = this?.let { array ->
        (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }.joinToString(" / ")
    }.orEmpty()

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotBlank() && it != "null" && (it.startsWith("http://") || it.startsWith("https://")) }
        ?.replace(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
}
