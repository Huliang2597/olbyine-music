package com.olbyine.music.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream

internal object QrcDecrypter {
    private val QRC_KEY = "!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(Charsets.US_ASCII)
    private val LOCAL_HEADER = byteArrayOf(
        0x98.toByte(), 0x25, 0xb0.toByte(), 0xac.toByte(), 0xe3.toByte(), 0x02, 0x83.toByte(), 0x68,
        0xe8.toByte(), 0xfc.toByte(), 0x6c,
    )
    private const val ENCRYPT = 1
    private const val DECRYPT = 0

    private val schedule: Array<Array<IntArray>> by lazy {
        arrayOf(
            keySchedule(QRC_KEY, 16, DECRYPT),
            keySchedule(QRC_KEY, 8, ENCRYPT),
            keySchedule(QRC_KEY, 0, DECRYPT),
        )
    }

    fun decryptHex(hex: String): String {
        val clean = hex.trim()
        require(clean.isNotEmpty() && clean.length % 2 == 0 && clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) { "QRC 数据无效" }
        val bytes = ByteArray(clean.length / 2) { index ->
            ((Character.digit(clean[index * 2], 16) shl 4) or Character.digit(clean[index * 2 + 1], 16)).toByte()
        }
        return decryptBytes(bytes)
    }

    fun decryptBytes(encrypted: ByteArray): String {
        require(encrypted.isNotEmpty() && encrypted.size % 8 == 0) { "QRC 数据长度无效" }
        val keys = schedule
        val output = ByteArray(encrypted.size)
        val block = ByteArray(8)
        for (offset in encrypted.indices step 8) {
            System.arraycopy(encrypted, offset, block, 0, 8)
            var data = block
            for (round in 0 until 3) data = crypt(data, keys[round])
            System.arraycopy(data, 0, output, offset, 8)
        }
        return inflate(output)
    }

    fun isLocalQrc(bytes: ByteArray): Boolean =
        bytes.size > LOCAL_HEADER.size && LOCAL_HEADER.indices.all { bytes[it] == LOCAL_HEADER[it] }

    fun decryptLocal(file: ByteArray): String = decryptBytes(unwrapLocal(file))

    fun unwrapLocal(file: ByteArray): ByteArray {
        require(isLocalQrc(file)) { "不是 QQ 音乐本地歌词文件" }
        val data = file.copyOf()
        for (i in data.indices) {
            val key = if (i > 0x7FFF) QMC1_KEY[(i % 0x7FFF) and 0x7F] else QMC1_KEY[i and 0x7F]
            data[i] = (data[i].toInt() xor key).toByte()
        }
        return data.copyOfRange(LOCAL_HEADER.size, data.size)
    }

    private fun inflate(compressed: ByteArray): String {
        val output = ByteArrayOutputStream()
        InflaterInputStream(ByteArrayInputStream(compressed)).use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 4 * 1024 * 1024) { "逐字歌词解压后过大" }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray().toString(Charsets.UTF_8).removePrefix("﻿")
    }

    private fun bitnum(a: ByteArray, offset: Int, b: Int, c: Int): Int =
        (((a[offset + (b / 32) * 4 + 3 - (b % 32) / 8].toInt() and 0xFF) ushr (7 - b % 8)) and 1) shl c

    private fun bitnumIntr(a: Int, b: Int, c: Int): Int = ((a ushr (31 - b)) and 1) shl c

    private fun bitnumIntl(a: Int, b: Int, c: Int): Int = ((a shl b) and 0x80000000.toInt()) ushr c

    private fun sboxBit(a: Int): Int = (a and 32) or ((a and 31) ushr 1) or ((a and 1) shl 4)

    private fun crypt(input: ByteArray, key: Array<IntArray>): ByteArray {
        var s0 = bitnum(input, 0, 57, 31) or bitnum(input, 0, 49, 30) or bitnum(input, 0, 41, 29) or bitnum(input, 0, 33, 28) or
            bitnum(input, 0, 25, 27) or bitnum(input, 0, 17, 26) or bitnum(input, 0, 9, 25) or bitnum(input, 0, 1, 24) or
            bitnum(input, 0, 59, 23) or bitnum(input, 0, 51, 22) or bitnum(input, 0, 43, 21) or bitnum(input, 0, 35, 20) or
            bitnum(input, 0, 27, 19) or bitnum(input, 0, 19, 18) or bitnum(input, 0, 11, 17) or bitnum(input, 0, 3, 16) or
            bitnum(input, 0, 61, 15) or bitnum(input, 0, 53, 14) or bitnum(input, 0, 45, 13) or bitnum(input, 0, 37, 12) or
            bitnum(input, 0, 29, 11) or bitnum(input, 0, 21, 10) or bitnum(input, 0, 13, 9) or bitnum(input, 0, 5, 8) or
            bitnum(input, 0, 63, 7) or bitnum(input, 0, 55, 6) or bitnum(input, 0, 47, 5) or bitnum(input, 0, 39, 4) or
            bitnum(input, 0, 31, 3) or bitnum(input, 0, 23, 2) or bitnum(input, 0, 15, 1) or bitnum(input, 0, 7, 0)
        var s1 = bitnum(input, 0, 56, 31) or bitnum(input, 0, 48, 30) or bitnum(input, 0, 40, 29) or bitnum(input, 0, 32, 28) or
            bitnum(input, 0, 24, 27) or bitnum(input, 0, 16, 26) or bitnum(input, 0, 8, 25) or bitnum(input, 0, 0, 24) or
            bitnum(input, 0, 58, 23) or bitnum(input, 0, 50, 22) or bitnum(input, 0, 42, 21) or bitnum(input, 0, 34, 20) or
            bitnum(input, 0, 26, 19) or bitnum(input, 0, 18, 18) or bitnum(input, 0, 10, 17) or bitnum(input, 0, 2, 16) or
            bitnum(input, 0, 60, 15) or bitnum(input, 0, 52, 14) or bitnum(input, 0, 44, 13) or bitnum(input, 0, 36, 12) or
            bitnum(input, 0, 28, 11) or bitnum(input, 0, 20, 10) or bitnum(input, 0, 12, 9) or bitnum(input, 0, 4, 8) or
            bitnum(input, 0, 62, 7) or bitnum(input, 0, 54, 6) or bitnum(input, 0, 46, 5) or bitnum(input, 0, 38, 4) or
            bitnum(input, 0, 30, 3) or bitnum(input, 0, 22, 2) or bitnum(input, 0, 14, 1) or bitnum(input, 0, 6, 0)

        for (round in 0 until 15) {
            val previous = s1
            s1 = f(s1, key[round]) xor s0
            s0 = previous
        }
        s0 = f(s1, key[15]) xor s0

        val data = ByteArray(8)
        data[3] = (bitnumIntr(s1, 7, 7) or bitnumIntr(s0, 7, 6) or bitnumIntr(s1, 15, 5) or bitnumIntr(s0, 15, 4) or
            bitnumIntr(s1, 23, 3) or bitnumIntr(s0, 23, 2) or bitnumIntr(s1, 31, 1) or bitnumIntr(s0, 31, 0)).toByte()
        data[2] = (bitnumIntr(s1, 6, 7) or bitnumIntr(s0, 6, 6) or bitnumIntr(s1, 14, 5) or bitnumIntr(s0, 14, 4) or
            bitnumIntr(s1, 22, 3) or bitnumIntr(s0, 22, 2) or bitnumIntr(s1, 30, 1) or bitnumIntr(s0, 30, 0)).toByte()
        data[1] = (bitnumIntr(s1, 5, 7) or bitnumIntr(s0, 5, 6) or bitnumIntr(s1, 13, 5) or bitnumIntr(s0, 13, 4) or
            bitnumIntr(s1, 21, 3) or bitnumIntr(s0, 21, 2) or bitnumIntr(s1, 29, 1) or bitnumIntr(s0, 29, 0)).toByte()
        data[0] = (bitnumIntr(s1, 4, 7) or bitnumIntr(s0, 4, 6) or bitnumIntr(s1, 12, 5) or bitnumIntr(s0, 12, 4) or
            bitnumIntr(s1, 20, 3) or bitnumIntr(s0, 20, 2) or bitnumIntr(s1, 28, 1) or bitnumIntr(s0, 28, 0)).toByte()
        data[7] = (bitnumIntr(s1, 3, 7) or bitnumIntr(s0, 3, 6) or bitnumIntr(s1, 11, 5) or bitnumIntr(s0, 11, 4) or
            bitnumIntr(s1, 19, 3) or bitnumIntr(s0, 19, 2) or bitnumIntr(s1, 27, 1) or bitnumIntr(s0, 27, 0)).toByte()
        data[6] = (bitnumIntr(s1, 2, 7) or bitnumIntr(s0, 2, 6) or bitnumIntr(s1, 10, 5) or bitnumIntr(s0, 10, 4) or
            bitnumIntr(s1, 18, 3) or bitnumIntr(s0, 18, 2) or bitnumIntr(s1, 26, 1) or bitnumIntr(s0, 26, 0)).toByte()
        data[5] = (bitnumIntr(s1, 1, 7) or bitnumIntr(s0, 1, 6) or bitnumIntr(s1, 9, 5) or bitnumIntr(s0, 9, 4) or
            bitnumIntr(s1, 17, 3) or bitnumIntr(s0, 17, 2) or bitnumIntr(s1, 25, 1) or bitnumIntr(s0, 25, 0)).toByte()
        data[4] = (bitnumIntr(s1, 0, 7) or bitnumIntr(s0, 0, 6) or bitnumIntr(s1, 8, 5) or bitnumIntr(s0, 8, 4) or
            bitnumIntr(s1, 16, 3) or bitnumIntr(s0, 16, 2) or bitnumIntr(s1, 24, 1) or bitnumIntr(s0, 24, 0)).toByte()
        return data
    }

    private fun f(state: Int, key: IntArray): Int {
        val t1 = bitnumIntl(state, 31, 0) or ((state and 0xf0000000.toInt()) ushr 1) or bitnumIntl(state, 4, 5) or
            bitnumIntl(state, 3, 6) or ((state and 0x0f000000) ushr 3) or bitnumIntl(state, 8, 11) or
            bitnumIntl(state, 7, 12) or ((state and 0x00f00000) ushr 5) or bitnumIntl(state, 12, 17) or
            bitnumIntl(state, 11, 18) or ((state and 0x000f0000) ushr 7) or bitnumIntl(state, 16, 23)
        val t2 = bitnumIntl(state, 15, 0) or ((state and 0x0000f000) shl 15) or bitnumIntl(state, 20, 5) or
            bitnumIntl(state, 19, 6) or ((state and 0x00000f00) shl 13) or bitnumIntl(state, 24, 11) or
            bitnumIntl(state, 23, 12) or ((state and 0x000000f0) shl 11) or bitnumIntl(state, 28, 17) or
            bitnumIntl(state, 27, 18) or ((state and 0x0000000f) shl 9) or bitnumIntl(state, 0, 23)

        val l0 = ((t1 ushr 24) and 0xff) xor key[0]
        val l1 = ((t1 ushr 16) and 0xff) xor key[1]
        val l2 = ((t1 ushr 8) and 0xff) xor key[2]
        val l3 = ((t2 ushr 24) and 0xff) xor key[3]
        val l4 = ((t2 ushr 16) and 0xff) xor key[4]
        val l5 = ((t2 ushr 8) and 0xff) xor key[5]

        val s = (SBOX[0][sboxBit(l0 ushr 2)] shl 28) or
            (SBOX[1][sboxBit(((l0 and 0x03) shl 4) or (l1 ushr 4))] shl 24) or
            (SBOX[2][sboxBit(((l1 and 0x0f) shl 2) or (l2 ushr 6))] shl 20) or
            (SBOX[3][sboxBit(l2 and 0x3f)] shl 16) or
            (SBOX[4][sboxBit(l3 ushr 2)] shl 12) or
            (SBOX[5][sboxBit(((l3 and 0x03) shl 4) or (l4 ushr 4))] shl 8) or
            (SBOX[6][sboxBit(((l4 and 0x0f) shl 2) or (l5 ushr 6))] shl 4) or
            SBOX[7][sboxBit(l5 and 0x3f)]

        return bitnumIntl(s, 15, 0) or bitnumIntl(s, 6, 1) or bitnumIntl(s, 19, 2) or bitnumIntl(s, 20, 3) or
            bitnumIntl(s, 28, 4) or bitnumIntl(s, 11, 5) or bitnumIntl(s, 27, 6) or bitnumIntl(s, 16, 7) or
            bitnumIntl(s, 0, 8) or bitnumIntl(s, 14, 9) or bitnumIntl(s, 22, 10) or bitnumIntl(s, 25, 11) or
            bitnumIntl(s, 4, 12) or bitnumIntl(s, 17, 13) or bitnumIntl(s, 30, 14) or bitnumIntl(s, 9, 15) or
            bitnumIntl(s, 1, 16) or bitnumIntl(s, 7, 17) or bitnumIntl(s, 23, 18) or bitnumIntl(s, 13, 19) or
            bitnumIntl(s, 31, 20) or bitnumIntl(s, 26, 21) or bitnumIntl(s, 2, 22) or bitnumIntl(s, 8, 23) or
            bitnumIntl(s, 18, 24) or bitnumIntl(s, 12, 25) or bitnumIntl(s, 29, 26) or bitnumIntl(s, 5, 27) or
            bitnumIntl(s, 21, 28) or bitnumIntl(s, 10, 29) or bitnumIntl(s, 3, 30) or bitnumIntl(s, 24, 31)
    }

    private fun keySchedule(key: ByteArray, offset: Int, mode: Int): Array<IntArray> {
        val schedule = Array(16) { IntArray(6) }
        var c = 0
        var d = 0
        for (i in 0 until 28) {
            c = c or bitnum(key, offset, KEY_PERM_C[i], 31 - i)
            d = d or bitnum(key, offset, KEY_PERM_D[i], 31 - i)
        }
        for (i in 0 until 16) {
            val shift = KEY_RND_SHIFT[i]
            c = ((c shl shift) or (c ushr (28 - shift))) and 0xfffffff0.toInt()
            d = ((d shl shift) or (d ushr (28 - shift))) and 0xfffffff0.toInt()
            val target = if (mode == DECRYPT) 15 - i else i
            val round = schedule[target]
            round.fill(0)
            for (j in 0 until 24) round[j / 8] = round[j / 8] or bitnumIntr(c, KEY_COMPRESSION[j], 7 - j % 8)
            for (j in 24 until 48) round[j / 8] = round[j / 8] or bitnumIntr(d, KEY_COMPRESSION[j] - 27, 7 - j % 8)
        }
        return schedule
    }

    private val KEY_RND_SHIFT = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
    private val KEY_PERM_C = intArrayOf(56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35)
    private val KEY_PERM_D = intArrayOf(62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3)
    private val KEY_COMPRESSION = intArrayOf(
        13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3, 25, 7, 15, 6, 26, 19, 12, 1,
        40, 51, 30, 36, 46, 54, 29, 39, 50, 44, 32, 47, 43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31,
    )

    private val SBOX = arrayOf(
        intArrayOf(
            14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7,
            0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
            4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0,
            15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13,
        ),
        intArrayOf(
            15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10,
            3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5,
            0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15,
            13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9,
        ),
        intArrayOf(
            10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8,
            13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
            13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7,
            1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12,
        ),
        intArrayOf(
            7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15,
            13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
            10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4,
            3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14,
        ),
        intArrayOf(
            2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9,
            14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
            4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14,
            11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3,
        ),
        intArrayOf(
            12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11,
            10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
            9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6,
            4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13,
        ),
        intArrayOf(
            4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1,
            13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
            1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2,
            6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12,
        ),
        intArrayOf(
            13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7,
            1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
            7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8,
            2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11,
        ),
    )

    private val QMC1_KEY = intArrayOf(
        0xc3, 0x4a, 0xd6, 0xca, 0x90, 0x67, 0xf7, 0x52, 0xd8, 0xa1, 0x66, 0x62, 0x9f, 0x5b, 0x09, 0x00,
        0xc3, 0x5e, 0x95, 0x23, 0x9f, 0x13, 0x11, 0x7e, 0xd8, 0x92, 0x3f, 0xbc, 0x90, 0xbb, 0x74, 0x0e,
        0xc3, 0x47, 0x74, 0x3d, 0x90, 0xaa, 0x3f, 0x51, 0xd8, 0xf4, 0x11, 0x84, 0x9f, 0xde, 0x95, 0x1d,
        0xc3, 0xc6, 0x09, 0xd5, 0x9f, 0xfa, 0x66, 0xf9, 0xd8, 0xf0, 0xf7, 0xa0, 0x90, 0xa1, 0xd6, 0xf3,
        0xc3, 0xf3, 0xd6, 0xa1, 0x90, 0xa0, 0xf7, 0xf0, 0xd8, 0xf9, 0x66, 0xfa, 0x9f, 0xd5, 0x09, 0xc6,
        0xc3, 0x1d, 0x95, 0xde, 0x9f, 0x84, 0x11, 0xf4, 0xd8, 0x51, 0x3f, 0xaa, 0x90, 0x3d, 0x74, 0x47,
        0xc3, 0x0e, 0x74, 0xbb, 0x90, 0xbc, 0x3f, 0x92, 0xd8, 0x7e, 0x11, 0x13, 0x9f, 0x23, 0x95, 0x5e,
        0xc3, 0x00, 0x09, 0x5b, 0x9f, 0x62, 0x66, 0xa1, 0xd8, 0x52, 0xf7, 0x67, 0x90, 0xca, 0xd6, 0x4a,
    )
}
