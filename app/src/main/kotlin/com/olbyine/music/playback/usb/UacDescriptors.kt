package com.olbyine.music.playback.usb

internal data class UacAlt(
    val interfaceNumber: Int,
    val alternateSetting: Int,
    val uacVersion: Int,
    val terminalLink: Int,
    val pcm: Boolean,
    val channels: Int,
    val subslot: Int,
    val bits: Int,
    val discreteRates: List<Int>,
    val continuousRates: IntRange?,
    val dataEndpoint: Int,
    val maxPacket: Int,
    val interval: Int,
    val syncType: Int,
    val feedbackEndpoint: Int,
    val feedbackMaxPacket: Int,
) {
    val frameBytes: Int get() = subslot * channels
}

internal data class UacFeatureUnit(
    val unitId: Int,
    val sourceId: Int,
    val masterVolume: Boolean,
    val masterMute: Boolean,
    val channelVolumes: List<Int>,
)

internal data class UacTopology(
    val version: Int,
    val controlInterface: Int,
    val alts: List<UacAlt>,
    val terminalClock: Map<Int, Int>,
    val clockSources: Set<Int>,
    val clockSelectors: Map<Int, List<Int>>,
    val clockMultipliers: Map<Int, Int>,
    val featureUnits: List<UacFeatureUnit>,
) {
    fun volumeUnit(terminal: Int): UacFeatureUnit? =
        featureUnits.firstOrNull { it.sourceId == terminal && (it.masterVolume || it.channelVolumes.isNotEmpty()) }
            ?: featureUnits.firstOrNull { it.masterVolume || it.channelVolumes.isNotEmpty() }
}

internal object UacDescriptors {
    private const val INTERFACE = 4
    private const val ENDPOINT = 5
    private const val CS_INTERFACE = 0x24
    private const val AUDIO = 1
    private const val AUDIO_CONTROL = 1
    private const val AUDIO_STREAMING = 2

    fun parse(raw: ByteArray): UacTopology? {
        var index = 0
        var interfaceNumber = -1
        var alternate = 0
        var interfaceClass = 0
        var interfaceSubclass = 0
        var controlInterface = -1
        var version = 1
        var current: AltBuilder? = null
        val alts = mutableListOf<UacAlt>()
        val terminalClock = HashMap<Int, Int>()
        val clockSources = HashSet<Int>()
        val clockSelectors = HashMap<Int, List<Int>>()
        val clockMultipliers = HashMap<Int, Int>()
        val featureUnits = mutableListOf<UacFeatureUnit>()

        fun u8(at: Int) = raw.getOrNull(at)?.toInt()?.and(0xFF) ?: 0
        fun u16(at: Int) = u8(at) or (u8(at + 1) shl 8)
        fun u24(at: Int) = u16(at) or (u8(at + 2) shl 16)
        fun u32(at: Int) = u24(at) or (u8(at + 3) shl 24)

        fun finishAlt() {
            current?.build()?.let(alts::add)
            current = null
        }

        while (index + 2 <= raw.size) {
            val length = u8(index)
            val type = u8(index + 1)
            if (length < 2 || index + length > raw.size) break
            val d = index
            when (type) {
                INTERFACE -> {
                    finishAlt()
                    interfaceNumber = u8(d + 2)
                    alternate = u8(d + 3)
                    interfaceClass = u8(d + 5)
                    interfaceSubclass = u8(d + 6)
                    val protocol = u8(d + 7)
                    if (interfaceClass == AUDIO && interfaceSubclass == AUDIO_CONTROL) {
                        controlInterface = interfaceNumber
                        version = if (protocol == 0x20) 2 else 1
                    }
                    if (interfaceClass == AUDIO && interfaceSubclass == AUDIO_STREAMING && alternate > 0) {
                        current = AltBuilder(interfaceNumber, alternate, if (protocol == 0x20) 2 else 1)
                    }
                }
                CS_INTERFACE -> if (interfaceClass == AUDIO) {
                    val subtype = u8(d + 2)
                    if (interfaceSubclass == AUDIO_CONTROL) {
                        when (subtype) {
                            0x02 -> if (version == 2 && length >= 8) terminalClock[u8(d + 3)] = u8(d + 7)
                            0x06 -> featureUnits += parseFeatureUnit(raw, d, length, version)
                            0x0A -> if (version == 2) clockSources += u8(d + 3)
                            0x0B -> if (version == 2) {
                                val pins = u8(d + 4)
                                clockSelectors[u8(d + 3)] = (0 until pins).map { u8(d + 5 + it) }
                            }
                            0x0C -> if (version == 2) clockMultipliers[u8(d + 3)] = u8(d + 4)
                        }
                    } else if (interfaceSubclass == AUDIO_STREAMING) {
                        val alt = current
                        if (alt != null) when (subtype) {
                            0x01 -> if (alt.version == 2) {
                                alt.terminalLink = u8(d + 3)
                                val formatType = u8(d + 5)
                                alt.pcm = formatType == 1 && (u32(d + 6) and 1) != 0
                                alt.channels = u8(d + 10)
                            } else {
                                alt.terminalLink = u8(d + 3)
                                alt.pcm = u16(d + 5) == 0x0001
                            }
                            0x02 -> if (u8(d + 3) == 1) {
                                if (alt.version == 2) {
                                    alt.subslot = u8(d + 4)
                                    alt.bits = u8(d + 5)
                                } else {
                                    alt.channels = u8(d + 4)
                                    alt.subslot = u8(d + 5)
                                    alt.bits = u8(d + 6)
                                    val count = u8(d + 7)
                                    if (count == 0 && length >= 14) {
                                        alt.continuous = u24(d + 8)..u24(d + 11)
                                    } else {
                                        alt.rates = (0 until count).map { u24(d + 8 + it * 3) }.filter { it > 0 }
                                    }
                                }
                            } else {
                                alt.pcm = false
                            }
                        }
                    }
                }
                ENDPOINT -> current?.let { alt ->
                    val address = u8(d + 2)
                    val attributes = u8(d + 3)
                    val packet = u16(d + 4)
                    val bytes = (packet and 0x7FF) * (1 + ((packet shr 11) and 0x3))
                    val interval = u8(d + 6)
                    val isochronous = attributes and 0x3 == 1
                    val usage = (attributes shr 4) and 0x3
                    if (isochronous && address and 0x80 == 0 && usage != 1) {
                        alt.dataEndpoint = address
                        alt.maxPacket = bytes
                        alt.interval = interval
                        alt.syncType = (attributes shr 2) and 0x3
                        if (length >= 9 && u8(d + 8) != 0) alt.feedbackEndpoint = u8(d + 8)
                    } else if (isochronous && address and 0x80 != 0 && usage != 2) {
                        alt.feedbackEndpoint = address
                        alt.feedbackMaxPacket = bytes
                    }
                }
            }
            index += length
        }
        finishAlt()
        if (controlInterface < 0 || alts.isEmpty()) return null
        return UacTopology(
            version = version,
            controlInterface = controlInterface,
            alts = alts.filter { it.pcm && it.dataEndpoint != 0 && it.channels in 1..8 && it.subslot in 2..4 && it.bits in 16..32 },
            terminalClock = terminalClock,
            clockSources = clockSources,
            clockSelectors = clockSelectors,
            clockMultipliers = clockMultipliers,
            featureUnits = featureUnits,
        )
    }

    private fun parseFeatureUnit(raw: ByteArray, d: Int, length: Int, version: Int): List<UacFeatureUnit> {
        fun u8(at: Int) = raw.getOrNull(at)?.toInt()?.and(0xFF) ?: 0
        val unitId = u8(d + 3)
        val sourceId = u8(d + 4)
        return if (version == 2) {
            val count = (length - 6) / 4
            fun controls(channel: Int): Long {
                val at = d + 5 + channel * 4
                return (u8(at).toLong() or (u8(at + 1).toLong() shl 8) or (u8(at + 2).toLong() shl 16) or (u8(at + 3).toLong() shl 24))
            }
            val master = if (count > 0) controls(0) else 0L
            val channels = (1 until count).filter { (controls(it) shr 2) and 0x3 == 0x3L }
            listOf(UacFeatureUnit(unitId, sourceId, (master shr 2) and 0x3 == 0x3L, master and 0x3 == 0x3L, channels))
        } else {
            val size = u8(d + 5).coerceAtLeast(1)
            val count = (length - 7) / size
            fun controls(channel: Int): Int {
                var value = 0
                for (b in 0 until size) value = value or (u8(d + 6 + channel * size + b) shl (8 * b))
                return value
            }
            val master = if (count > 0) controls(0) else 0
            val channels = (1 until count).filter { controls(it) and 0x2 != 0 }
            listOf(UacFeatureUnit(unitId, sourceId, master and 0x2 != 0, master and 0x1 != 0, channels))
        }
    }

    private class AltBuilder(val interfaceNumber: Int, val alternate: Int, val version: Int) {
        var terminalLink = 0
        var pcm = true
        var channels = 0
        var subslot = 0
        var bits = 0
        var rates: List<Int> = emptyList()
        var continuous: IntRange? = null
        var dataEndpoint = 0
        var maxPacket = 0
        var interval = 1
        var syncType = 0
        var feedbackEndpoint = 0
        var feedbackMaxPacket = 0

        fun build() = UacAlt(
            interfaceNumber = interfaceNumber,
            alternateSetting = alternate,
            uacVersion = version,
            terminalLink = terminalLink,
            pcm = pcm,
            channels = channels,
            subslot = subslot,
            bits = bits,
            discreteRates = rates,
            continuousRates = continuous,
            dataEndpoint = dataEndpoint,
            maxPacket = maxPacket,
            interval = interval,
            syncType = syncType,
            feedbackEndpoint = feedbackEndpoint,
            feedbackMaxPacket = feedbackMaxPacket,
        )
    }
}
