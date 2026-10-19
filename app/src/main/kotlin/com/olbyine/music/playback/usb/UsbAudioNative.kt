package com.olbyine.music.playback.usb

import java.nio.ByteBuffer

internal object UsbAudioNative {
    val available: Boolean = runCatching { System.loadLibrary("olbyineusb") }.isSuccess

    const val IN_S16 = 1
    const val IN_S24 = 2
    const val IN_S32 = 3
    const val IN_FLOAT = 4

    @JvmStatic external fun nativeSpeed(fd: Int): Int
    @JvmStatic external fun nativeReattach(fd: Int, interfaceNumber: Int): Int
    @JvmStatic external fun nativeStart(
        fd: Int,
        endpoint: Int,
        maxPacket: Int,
        packetsPerSecond: Int,
        subslot: Int,
        bits: Int,
        channels: Int,
        rate: Int,
        inputRate: Int,
        feedbackEndpoint: Int,
        feedbackSize: Int,
        feedbackHighSpeed: Int,
        feedbackMicroframes: Int,
        ringMs: Int,
        dither: Int,
    ): Long
    @JvmStatic external fun nativeWrite(handle: Long, buffer: ByteBuffer, offset: Int, length: Int, encoding: Int, channels: Int, gain: Float): Int
    @JvmStatic external fun nativeSetPaused(handle: Long, paused: Boolean)
    @JvmStatic external fun nativeFlush(handle: Long)
    @JvmStatic external fun nativeFramesPlayed(handle: Long): Long
    @JvmStatic external fun nativeFramesQueued(handle: Long): Long
    @JvmStatic external fun nativeCapacityFrames(handle: Long): Long
    @JvmStatic external fun nativeUnderruns(handle: Long): Int
    @JvmStatic external fun nativeFailed(handle: Long): Boolean
    @JvmStatic external fun nativeMeasuredRate(handle: Long): Double
    @JvmStatic external fun nativeError(handle: Long): String
    @JvmStatic external fun nativeStop(handle: Long)
}
