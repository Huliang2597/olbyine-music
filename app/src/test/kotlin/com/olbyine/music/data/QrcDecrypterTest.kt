package com.olbyine.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrcDecrypterTest {
    private fun sample(name: String): ByteArray =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("qrc/$name")) { "missing $name" }.use { it.readBytes() }

    @Test
    fun decryptsLocalWordByWordLyrics() {
        val text = QrcDecrypter.decryptLocal(sample("orig.qrc"))
        println(text.take(400))
        assertTrue(text.contains("LyricContent="))
        assertTrue(Regex("\\[\\d+,\\d+][^\\n]*\\(\\d+,\\d+\\)").containsMatchIn(text))
    }

    @Test
    fun decryptsTranslationAndRomanization() {
        assertTrue(QrcDecrypter.decryptLocal(sample("ts.qrc")).isNotBlank())
        assertTrue(QrcDecrypter.decryptLocal(sample("roma.qrc")).contains("LyricContent="))
    }

    @Test
    fun parsesIntoWordByWordLyrics() {
        val qrc = QrcDecrypter.decryptLocal(sample("orig.qrc"))
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val repository = unsafe.javaClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, OnlineLyricsRepository::class.java)
        val parse = OnlineLyricsRepository::class.java.getDeclaredMethod("parseQrc", String::class.java).apply { isAccessible = true }
        val bundle = parse.invoke(repository, qrc)!!
        val wordLyrics = bundle.javaClass.getMethod("getWordLyrics").invoke(bundle) as String
        println(wordLyrics.lines().take(6).joinToString("\n"))
        val document = LyricsParser.parse(wordLyrics)
        assertTrue("expected lines", document.lines.size > 10)
        assertTrue("expected word timing", document.lines.count { it.words.size > 1 } > 10)
        assertTrue(document.lines.none { Regex("\\(\\d+,\\d+\\)").containsMatchIn(it.text) })
    }

    @Test
    fun cloudHexMatchesLocal() {
        val file = sample("orig.qrc")
        val hex = QrcDecrypter.unwrapLocal(file).joinToString("") { "%02X".format(it) }
        assertEquals(QrcDecrypter.decryptLocal(file), QrcDecrypter.decryptHex(hex))
    }
}
