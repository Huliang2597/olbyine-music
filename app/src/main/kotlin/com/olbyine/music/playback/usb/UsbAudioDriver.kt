package com.olbyine.music.playback.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

enum class UsbDriverStatus { OFF, NO_DEVICE, NEEDS_PERMISSION, READY, STREAMING, UNSUPPORTED, ERROR }

data class UsbDriverState(
    val status: UsbDriverStatus = UsbDriverStatus.OFF,
    val deviceName: String? = null,
    val detail: String? = null,
    val volume: Int = DEFAULT_VOLUME,
    val hardwareVolume: Boolean = false,
    val format: String? = null,
    val bitPerfect: Boolean = false,
    val capabilities: String? = null,
)

internal const val DEFAULT_VOLUME = 35

object UsbAudioDriver {
    const val PREF_KEY = "usb_direct_driver"
    private const val PREF_VOLUME = "usb_direct_volume"
    private const val PREF_BIT_DEPTH = "usb_bit_depth"
    private const val PREF_DITHER = "usb_dither"
    private const val PREF_BUFFER = "usb_buffer_ms"
    private const val PREF_VOLUME_MODE = "usb_volume_mode"
    private const val PREF_MAX_VOLUME = "usb_max_volume"
    private const val ACTION_PERMISSION = "com.olbyine.music.USB_AUDIO_PERMISSION"
    private const val TAG = "OlbyineUsbDriver"

    private val _state = MutableStateFlow(UsbDriverState())
    val state: StateFlow<UsbDriverState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private lateinit var usbManager: UsbManager
    private lateinit var preferences: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private var initialized = false

    private var device: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var topology: UacTopology? = null
    private val claimed = mutableListOf<UsbInterface>()
    private var speed = 0
    private var stream: ActiveStream? = null
    private var volume: VolumeControl? = null
    private val clockRates = HashMap<Int, List<IntRange>>()
    private val readyListeners = CopyOnWriteArraySet<() -> Unit>()

    @Volatile var softwareGain: Float = 1f
        private set

    val enabled: Boolean get() = initialized && preferences.getBoolean(PREF_KEY, false)

    val isReady: Boolean
        @Synchronized get() = connection != null && topology != null &&
            (_state.value.status == UsbDriverStatus.READY || _state.value.status == UsbDriverStatus.STREAMING)

    internal class ActiveStream(
        val handle: Long,
        val alt: UacAlt,
        val deviceRate: Int,
        val inputRate: Int,
    ) {
        @Volatile var closed = false
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PREF_KEY -> mainHandler.post { if (enabled) attach() else release(publishOff = true) }
            PREF_VOLUME_MODE, PREF_MAX_VOLUME -> mainHandler.post {
                synchronized(this) {
                    applyVolume(_state.value.volume)
                    _state.value = _state.value.copy(hardwareVolume = volume != null && volumeMode == "HARDWARE")
                }
            }
        }
    }

    private val volumeMode: String get() = if (initialized) preferences.getString(PREF_VOLUME_MODE, null) ?: "HARDWARE" else "HARDWARE"
    private val maxVolume: Int get() = if (initialized) preferences.getInt(PREF_MAX_VOLUME, 100).coerceIn(10, 100) else 100

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val changed = deviceFrom(intent)
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> if (enabled && connection == null) attach()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> if (changed != null && changed.deviceName == device?.deviceName) {
                    release(publishOff = false)
                    publish(if (enabled) UsbDriverStatus.NO_DEVICE else UsbDriverStatus.OFF)
                }
                ACTION_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && changed != null && enabled) open(changed)
                    else if (!granted) publish(UsbDriverStatus.NEEDS_PERMISSION, changed?.productName, "没有授予 USB 访问权限")
                }
            }
        }
    }

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
        preferences = appContext.getSharedPreferences("native_settings", Context.MODE_PRIVATE)
        initialized = true
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_PERMISSION)
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        _state.value = _state.value.copy(volume = preferences.getInt(PREF_VOLUME, DEFAULT_VOLUME))
        if (enabled) attach()
    }

    fun addReadyListener(listener: () -> Unit) { readyListeners += listener }
    fun removeReadyListener(listener: () -> Unit) { readyListeners -= listener }

    private fun notifyReadyChanged() = mainHandler.post { readyListeners.forEach { it() } }

    @Synchronized
    fun attach() {
        if (!initialized || !enabled || connection != null) return
        if (!UsbAudioNative.available) {
            publish(UsbDriverStatus.ERROR, detail = "USB 驱动组件加载失败")
            return
        }
        val candidate = usbManager.deviceList.values.firstOrNull(::isAudioOutput)
        if (candidate == null) {
            publish(UsbDriverStatus.NO_DEVICE)
            return
        }
        if (!usbManager.hasPermission(candidate)) {
            publish(UsbDriverStatus.NEEDS_PERMISSION, candidate.productName, "请在弹出的系统对话框里允许访问")
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val intent = PendingIntent.getBroadcast(
                appContext, 0, Intent(ACTION_PERMISSION).setPackage(appContext.packageName), flags,
            )
            usbManager.requestPermission(candidate, intent)
            return
        }
        open(candidate)
    }

    @Synchronized
    private fun open(target: UsbDevice) {
        if (connection != null) return
        val name = target.productName ?: "USB DAC"
        val opened = runCatching { usbManager.openDevice(target) }.getOrNull()
        if (opened == null) {
            publish(UsbDriverStatus.ERROR, name, "无法打开 USB 设备")
            return
        }
        val parsed = opened.rawDescriptors?.let { runCatching { UacDescriptors.parse(it) }.getOrNull() }
        if (parsed == null || parsed.alts.isEmpty()) {
            opened.close()
            publish(UsbDriverStatus.UNSUPPORTED, name, "没有找到标准的 USB 音频（UAC）播放接口")
            return
        }
        val numbers = (listOf(parsed.controlInterface) + parsed.alts.map { it.interfaceNumber }).distinct()
        val interfaces = (0 until target.interfaceCount).map(target::getInterface)
        for (number in numbers) {
            val intf = interfaces.firstOrNull { it.id == number && it.alternateSetting == 0 }
                ?: interfaces.firstOrNull { it.id == number } ?: continue
            if (opened.claimInterface(intf, true)) {
                claimed += intf
            } else {
                Log.w(TAG, "claim $number failed")
                claimed.forEach { opened.releaseInterface(it) }
                claimed.forEach { UsbAudioNative.nativeReattach(opened.fileDescriptor, it.id) }
                claimed.clear()
                opened.close()
                publish(UsbDriverStatus.ERROR, name, "无法独占 USB 接口（可能被其他应用占用）")
                return
            }
        }
        device = target
        connection = opened
        topology = parsed
        speed = UsbAudioNative.nativeSpeed(opened.fileDescriptor)
        clockRates.clear()
        interfaces.filter { intf -> intf.alternateSetting == 0 && parsed.alts.any { it.interfaceNumber == intf.id } }
            .forEach { opened.setInterface(it) }
        setupVolume(parsed)
        _state.value = UsbDriverState(
            status = UsbDriverStatus.READY,
            deviceName = name,
            volume = _state.value.volume,
            hardwareVolume = volume != null && volumeMode == "HARDWARE",
            capabilities = describeCapabilities(parsed),
            detail = if (volume == null) "设备没有硬件音量，使用软件音量（音量低于 100% 时不再比特精确）" else null,
        )
        notifyReadyChanged()
    }

    @Synchronized
    fun release(publishOff: Boolean) {
        stopStreamLocked()
        val opened = connection
        if (opened != null) {
            val fd = opened.fileDescriptor
            claimed.forEach { runCatching { opened.releaseInterface(it) } }
            claimed.forEach { UsbAudioNative.nativeReattach(fd, it.id) }
            opened.close()
        }
        claimed.clear()
        connection = null
        device = null
        topology = null
        volume = null
        softwareGain = 1f
        if (publishOff) publish(if (enabled) UsbDriverStatus.NO_DEVICE else UsbDriverStatus.OFF)
        notifyReadyChanged()
    }

    private fun isAudioOutput(candidate: UsbDevice): Boolean = (0 until candidate.interfaceCount).any { i ->
        val intf = candidate.getInterface(i)
        intf.interfaceClass == UsbConstants.USB_CLASS_AUDIO && intf.interfaceSubclass == 2 &&
            (0 until intf.endpointCount).any { e ->
                val endpoint = intf.getEndpoint(e)
                endpoint.direction == UsbConstants.USB_DIR_OUT && endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC
            }
    }

    @Suppress("DEPRECATION")
    private fun deviceFrom(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

    @Synchronized
    internal fun openStream(rate: Int, channels: Int, sourceBits: Int): ActiveStream? {
        val opened = connection ?: return null
        val parsed = topology ?: return null
        stopStreamLocked()
        val choice = chooseFormat(opened, parsed, rate, channels, sourceBits) ?: run {
            publish(UsbDriverStatus.READY, detail = "DAC 不支持 ${rate / 1000.0} kHz 的任何格式")
            return null
        }
        val (alt, deviceRate) = choice
        val intf = device?.let { d -> (0 until d.interfaceCount).map(d::getInterface) }
            ?.firstOrNull { it.id == alt.interfaceNumber && it.alternateSetting == alt.alternateSetting } ?: return null
        if (!opened.setInterface(intf)) {
            publish(UsbDriverStatus.ERROR, detail = "切换 USB 接口失败")
            return null
        }
        if (!setSampleRate(opened, parsed, alt, deviceRate)) {
            Log.w(TAG, "sample rate $deviceRate not confirmed")
        }
        val highSpeed = speed >= 3
        val interval = 1 shl (alt.interval.coerceIn(1, 16) - 1)
        val packetsPerSecond = if (highSpeed) 8000 / interval else 1000
        val feedback = if (alt.syncType == 1 && alt.feedbackEndpoint != 0) alt.feedbackEndpoint else 0
        val feedbackSize = alt.feedbackMaxPacket.takeIf { it in 3..4 } ?: if (highSpeed) 4 else 3
        val handle = UsbAudioNative.nativeStart(
            opened.fileDescriptor, alt.dataEndpoint, alt.maxPacket, packetsPerSecond, alt.subslot, alt.bits,
            alt.channels, deviceRate, rate, feedback, feedbackSize, if (highSpeed) 1 else 0,
            if (highSpeed) interval else 1,
            preferences.getInt(PREF_BUFFER, 240).coerceIn(80, 1_000),
            if (preferences.getBoolean(PREF_DITHER, true)) 1 else 0,
        )
        if (handle == 0L) {
            publish(UsbDriverStatus.ERROR, detail = "USB 音频流启动失败")
            return null
        }
        val active = ActiveStream(handle, alt, deviceRate, rate)
        stream = active
        val exact = deviceRate == rate && alt.bits >= sourceBits.coerceAtMost(24)
        val softwareVolume = softwareGain < 0.99999f
        _state.value = _state.value.copy(
            status = UsbDriverStatus.STREAMING,
            format = buildString {
                append(formatRate(deviceRate))
                append(" · ${alt.bits} bit")
                append(" · UAC${alt.uacVersion}")
                append(when (alt.syncType) { 1 -> " 异步"; 2 -> " 自适应"; 3 -> " 同步"; else -> "" })
                if (deviceRate != rate) append("（由 ${formatRate(rate)} 重采样）")
            },
            bitPerfect = exact && !softwareVolume,
        )
        return active
    }

    @Synchronized
    internal fun closeStream(active: ActiveStream) {
        if (stream === active) stopStreamLocked()
    }

    private fun stopStreamLocked() {
        val active = stream ?: return
        stream = null
        synchronized(active) {
            active.closed = true
            UsbAudioNative.nativeStop(active.handle)
        }
        val parsed = topology
        val opened = connection
        val d = device
        if (parsed != null && opened != null && d != null) {
            (0 until d.interfaceCount).map(d::getInterface)
                .firstOrNull { it.id == active.alt.interfaceNumber && it.alternateSetting == 0 }
                ?.let { opened.setInterface(it) }
        }
        if (_state.value.status == UsbDriverStatus.STREAMING) {
            _state.value = _state.value.copy(status = UsbDriverStatus.READY, format = null)
        }
    }

    internal fun reportFailure(active: ActiveStream, message: String) {
        mainHandler.post {
            synchronized(this) {
                if (stream !== active) return@post
                Log.w(TAG, "stream failed: $message")
                release(publishOff = false)
                publish(UsbDriverStatus.ERROR, detail = "USB 传输中断：$message")
            }
        }
    }

    private fun chooseFormat(
        opened: UsbDeviceConnection,
        parsed: UacTopology,
        rate: Int,
        channels: Int,
        sourceBits: Int,
    ): Pair<UacAlt, Int>? {
        val preferred = if (initialized) preferences.getInt(PREF_BIT_DEPTH, 0) else 0
        val wantBits = if (preferred in setOf(16, 24, 32)) preferred else sourceBits.coerceIn(16, 32)
        fun rank(alt: UacAlt) = (if (alt.channels == channels) 0 else if (alt.channels == 2) 1000 else 2000) +
            (if (alt.bits == wantBits) 0 else if (alt.bits >= wantBits) alt.bits - wantBits else 100 + wantBits - alt.bits)
        val packetsPerSecond = if (speed >= 3) 8000 else 1000
        fun fits(alt: UacAlt, r: Int): Boolean {
            val interval = 1 shl (alt.interval.coerceIn(1, 16) - 1)
            val pps = if (speed >= 3) packetsPerSecond / interval else 1000
            return (r / pps + 1) * alt.frameBytes <= alt.maxPacket
        }
        val exact = parsed.alts.filter { supports(opened, parsed, it, rate) && fits(it, rate) }.minByOrNull(::rank)
        if (exact != null) return exact to rate
        val options = parsed.alts.flatMap { alt -> ratesOf(opened, parsed, alt).filter { fits(alt, it) }.map { alt to it } }
        return options.minWithOrNull(compareBy({ if (it.second >= rate) it.second - rate else 1_000_000 + rate - it.second }, { rank(it.first) }))
    }

    private fun supports(opened: UsbDeviceConnection, parsed: UacTopology, alt: UacAlt, rate: Int): Boolean {
        if (alt.uacVersion == 2) return clockRanges(opened, parsed, alt).any { rate in it }
        if (alt.discreteRates.isNotEmpty()) return rate in alt.discreteRates
        return alt.continuousRates?.contains(rate) ?: false
    }

    private fun ratesOf(opened: UsbDeviceConnection, parsed: UacTopology, alt: UacAlt): List<Int> {
        val common = listOf(44_100, 48_000, 88_200, 96_000, 176_400, 192_000, 352_800, 384_000)
        return if (alt.uacVersion == 2) {
            val ranges = clockRanges(opened, parsed, alt)
            common.filter { r -> ranges.any { r in it } }
        } else if (alt.discreteRates.isNotEmpty()) {
            alt.discreteRates
        } else {
            common.filter { alt.continuousRates?.contains(it) == true }
        }
    }

    private fun clockId(opened: UsbDeviceConnection, parsed: UacTopology, alt: UacAlt): Int? {
        var id = parsed.terminalClock[alt.terminalLink] ?: parsed.clockSources.firstOrNull() ?: return null
        repeat(6) {
            when {
                id in parsed.clockSources -> return id
                parsed.clockSelectors.containsKey(id) -> {
                    val inputs = parsed.clockSelectors.getValue(id)
                    val buffer = ByteArray(1)
                    val read = opened.controlTransfer(0xA1, 0x01, 0x0100, (id shl 8) or parsed.controlInterface, buffer, 1, 300)
                    val pin = if (read == 1) buffer[0].toInt() and 0xFF else 1
                    id = inputs.getOrNull(pin - 1) ?: inputs.firstOrNull() ?: return null
                }
                parsed.clockMultipliers.containsKey(id) -> id = parsed.clockMultipliers.getValue(id)
                else -> return id
            }
        }
        return id
    }

    private fun clockRanges(opened: UsbDeviceConnection, parsed: UacTopology, alt: UacAlt): List<IntRange> {
        val clock = clockId(opened, parsed, alt) ?: return emptyList()
        clockRates[clock]?.let { return it }
        val buffer = ByteArray(2 + 12 * 32)
        val read = opened.controlTransfer(0xA1, 0x02, 0x0100, (clock shl 8) or parsed.controlInterface, buffer, buffer.size, 500)
        val ranges = if (read >= 2) {
            val count = (buffer[0].toInt() and 0xFF) or ((buffer[1].toInt() and 0xFF) shl 8)
            (0 until count).mapNotNull { i ->
                val at = 2 + i * 12
                if (at + 12 > read) return@mapNotNull null
                fun u32(o: Int) = (buffer[o].toLong() and 0xFF) or ((buffer[o + 1].toLong() and 0xFF) shl 8) or
                    ((buffer[o + 2].toLong() and 0xFF) shl 16) or ((buffer[o + 3].toLong() and 0xFF) shl 24)
                u32(at).toInt()..u32(at + 4).toInt()
            }
        } else emptyList()
        clockRates[clock] = ranges
        return ranges
    }

    private fun setSampleRate(opened: UsbDeviceConnection, parsed: UacTopology, alt: UacAlt, rate: Int): Boolean {
        return if (alt.uacVersion == 2) {
            val clock = clockId(opened, parsed, alt) ?: return false
            val data = byteArrayOf(rate.toByte(), (rate shr 8).toByte(), (rate shr 16).toByte(), (rate shr 24).toByte())
            val index = (clock shl 8) or parsed.controlInterface
            opened.controlTransfer(0x21, 0x01, 0x0100, index, data, 4, 1000)
            repeat(5) {
                val back = ByteArray(4)
                if (opened.controlTransfer(0xA1, 0x01, 0x0100, index, back, 4, 500) == 4) {
                    val value = (back[0].toInt() and 0xFF) or ((back[1].toInt() and 0xFF) shl 8) or
                        ((back[2].toInt() and 0xFF) shl 16) or ((back[3].toInt() and 0xFF) shl 24)
                    if (value == rate) return true
                }
                Thread.sleep(20)
            }
            false
        } else {
            val data = byteArrayOf(rate.toByte(), (rate shr 8).toByte(), (rate shr 16).toByte())
            val sent = opened.controlTransfer(0x22, 0x01, 0x0100, alt.dataEndpoint, data, 3, 1000)
            sent == 3 || (alt.discreteRates.size <= 1 && alt.continuousRates == null)
        }
    }

    private class VolumeControl(val unit: Int, val channels: List<Int>, val minDb256: Int, val maxDb256: Int, val resDb256: Int, val uac2: Boolean)

    private fun setupVolume(parsed: UacTopology) {
        val opened = connection ?: return
        val unitInfo = parsed.volumeUnit(parsed.alts.first().terminalLink)
        volume = null
        if (unitInfo != null) {
            val channels = if (unitInfo.masterVolume) listOf(0) else unitInfo.channelVolumes
            val probe = channels.first()
            val index = (unitInfo.unitId shl 8) or parsed.controlInterface
            val wValue = (0x02 shl 8) or probe
            val range: Triple<Int, Int, Int>? = if (parsed.version == 2) {
                val buffer = ByteArray(2 + 6 * 8)
                val read = opened.controlTransfer(0xA1, 0x02, wValue, index, buffer, buffer.size, 500)
                if (read >= 8) {
                    fun s16(o: Int) = ((buffer[o].toInt() and 0xFF) or (buffer[o + 1].toInt() shl 8)).toShort().toInt()
                    val count = (read - 2) / 6
                    Triple(s16(2), s16(2 + (count - 1) * 6 + 2), s16(4 + 2).coerceAtLeast(1))
                } else null
            } else {
                fun get(request: Int): Int? {
                    val buffer = ByteArray(2)
                    return if (opened.controlTransfer(0xA1, request, wValue, index, buffer, 2, 500) == 2) {
                        ((buffer[0].toInt() and 0xFF) or (buffer[1].toInt() shl 8)).toShort().toInt()
                    } else null
                }
                val min = get(0x82)
                val max = get(0x83)
                if (min != null && max != null) Triple(min, max, (get(0x84) ?: 1).coerceAtLeast(1)) else null
            }
            if (range != null && range.second > range.first) {
                volume = VolumeControl(unitInfo.unitId, channels, range.first, range.second, range.third, parsed.version == 2)
                if (unitInfo.masterMute) {
                    opened.controlTransfer(0x21, 0x01, 0x0100, index, byteArrayOf(0), 1, 300)
                }
            }
        }
        applyVolume(_state.value.volume)
    }

    @Synchronized
    fun setVolume(percent: Int) {
        if (volumeMode == "FIXED") {
            applyVolume(100)
            return
        }
        val value = percent.coerceIn(0, maxVolume)
        if (initialized) preferences.edit().putInt(PREF_VOLUME, value).apply()
        _state.value = _state.value.copy(volume = value)
        applyVolume(value)
    }

    private fun applyVolume(requested: Int) {
        val mode = volumeMode
        val percent = if (mode == "FIXED") 100 else requested.coerceAtMost(maxVolume)
        val p = percent / 100f
        val control = volume
        val opened = connection
        if (control == null || opened == null || mode == "SOFTWARE") {
            if (control != null && opened != null) setHardwareDb(opened, control, control.maxDb256)
            softwareGain = (p * p).coerceIn(0f, 1f)
            return
        }
        softwareGain = 1f
        val db = if (p <= 0f) control.minDb256 / 256.0 else control.maxDb256 / 256.0 + 20.0 * log10((p * p).toDouble())
        val raw = (db * 256.0).roundToInt().coerceIn(control.minDb256, control.maxDb256)
        setHardwareDb(opened, control, raw)
    }

    private fun setHardwareDb(opened: UsbDeviceConnection, control: VolumeControl, db256: Int) {
        val stepped = control.minDb256 + ((db256 - control.minDb256) / control.resDb256) * control.resDb256
        val data = byteArrayOf(stepped.toByte(), (stepped shr 8).toByte())
        val index = (control.unit shl 8) or (topology?.controlInterface ?: 0)
        control.channels.forEach { channel ->
            opened.controlTransfer(0x21, 0x01, (0x02 shl 8) or channel, index, data, 2, 300)
        }
    }

    fun adjustVolume(delta: Int) = setVolume(_state.value.volume + delta)

    private fun publish(status: UsbDriverStatus, name: String? = _state.value.deviceName, detail: String? = null) {
        _state.value = _state.value.copy(status = status, deviceName = name, detail = detail, format = null, bitPerfect = false)
    }

    private fun formatRate(rate: Int): String =
        if (rate % 1000 == 0) "${rate / 1000} kHz" else String.format(java.util.Locale.ROOT, "%.1f kHz", rate / 1000.0)

    private fun describeCapabilities(parsed: UacTopology): String {
        val opened = connection ?: return ""
        return parsed.alts.map { alt ->
            val rates = ratesOf(opened, parsed, alt).joinToString("/") { (it / 1000.0).let { k -> if (k % 1.0 == 0.0) k.toInt().toString() else k.toString() } }
            "${alt.channels}ch ${alt.bits}bit @ $rates kHz"
        }.distinct().joinToString("；") + " · UAC${parsed.version}" + if (speed >= 3) " · 高速" else " · 全速"
    }

    @Suppress("unused")
    private fun percentFromDb(db: Double, max: Double): Int = (10.0.pow((db - max) / 40.0) * 100).roundToInt()
}
