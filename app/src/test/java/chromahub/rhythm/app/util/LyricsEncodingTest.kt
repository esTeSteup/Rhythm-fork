/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class LyricsEncodingTest {

    private val simplifiedLyrics =
        "[00:12.34]你方唱罢我登场，长安姑娘\n[00:15.00]第二行歌词在这里\n[00:18.00]我们都在听这首歌"

    private val traditionalLyrics =
        "[00:12.34]你方唱罷我登場，長安姑娘\n[00:15.00]第二行歌詞在這裡\n[00:18.00]我們都在聽這首歌"

    private fun encode(text: String, charsetName: String): ByteArray =
        text.toByteArray(Charset.forName(charsetName))

    @Test
    fun `utf8 is detected`() {
        val result = LyricsEncoding.decodeWithInfo(simplifiedLyrics.toByteArray(Charsets.UTF_8))

        assertEquals(LyricsEncoding.Option.UTF_8, result.option)
        assertEquals(simplifiedLyrics, result.text)
    }

    @Test
    fun `plain ascii stays utf8`() {
        val text = "[00:01.00]Instrumental\n[00:02.00]No lyrics"

        val result = LyricsEncoding.decodeWithInfo(text.toByteArray(Charsets.UTF_8))

        assertEquals(LyricsEncoding.Option.UTF_8, result.option)
        assertEquals(text, result.text)
    }

    @Test
    fun `utf8 bom is stripped`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        val result = LyricsEncoding.decodeWithInfo(bom + simplifiedLyrics.toByteArray(Charsets.UTF_8))

        assertEquals(LyricsEncoding.Option.UTF_8, result.option)
        assertEquals(simplifiedLyrics, result.text)
    }

    @Test
    fun `gbk is detected`() {
        val result = LyricsEncoding.decodeWithInfo(encode(simplifiedLyrics, "GBK"))

        assertEquals(LyricsEncoding.Option.GB18030, result.option)
        assertEquals(simplifiedLyrics, result.text)
    }

    @Test
    fun `gb18030 is detected`() {
        val result = LyricsEncoding.decodeWithInfo(encode(simplifiedLyrics, "GB18030"))

        assertEquals(LyricsEncoding.Option.GB18030, result.option)
        assertEquals(simplifiedLyrics, result.text)
    }

    @Test
    fun `big5 is detected`() {
        val result = LyricsEncoding.decodeWithInfo(encode(traditionalLyrics, "Big5"))

        assertEquals(LyricsEncoding.Option.BIG5, result.option)
        assertEquals(traditionalLyrics, result.text)
    }

    @Test
    fun `shift jis is detected`() {
        val japaneseLyrics = "[00:12.34]君の名前を呼んでいる\n[00:15.00]夜が明けるまで歌う"

        val result = LyricsEncoding.decodeWithInfo(encode(japaneseLyrics, "Shift_JIS"))

        assertEquals(LyricsEncoding.Option.SHIFT_JIS, result.option)
        assertEquals(japaneseLyrics, result.text)
    }

    @Test
    fun `utf16 with bom is detected`() {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val bytes = bom + simplifiedLyrics.toByteArray(Charsets.UTF_16LE)

        val result = LyricsEncoding.decodeWithInfo(bytes)

        assertEquals(LyricsEncoding.Option.UTF_16_LE, result.option)
        assertEquals(simplifiedLyrics, result.text)
    }

    @Test
    fun `bomless utf16le is detected`() {
        val result = LyricsEncoding.decodeWithInfo(simplifiedLyrics.toByteArray(Charsets.UTF_16LE))

        assertEquals(LyricsEncoding.Option.UTF_16_LE, result.option)
        assertEquals(simplifiedLyrics, result.text)
    }

    @Test
    fun `mixed english and gbk chinese is detected`() {
        val mixedLyrics = buildString {
            repeat(8) { append("[00:0").append(it).append(".00]La la la la la la\n") }
            append("[00:10.00]你方唱罢我登场\n[00:12.00]长安姑娘")
        }

        val result = LyricsEncoding.decodeWithInfo(encode(mixedLyrics, "GBK"))

        assertEquals(LyricsEncoding.Option.GB18030, result.option)
        assertEquals(mixedLyrics, result.text)
    }

    @Test
    fun `windows 1252 lyrics are detected`() {
        val text = "Café — déjà vu\nOn chante encore ce soir"

        val result = LyricsEncoding.decodeWithInfo(encode(text, "windows-1252"))

        assertEquals(LyricsEncoding.Option.WINDOWS_1252, result.option)
        assertEquals(text, result.text)
    }

    @Test
    fun `manual encoding overrides detection`() {
        val bytes = encode(simplifiedLyrics, "GBK")

        val result = LyricsEncoding.decodeWithInfo(bytes, LyricsEncoding.Option.GB18030.id)

        assertEquals(LyricsEncoding.Option.GB18030, result.option)
        assertEquals(simplifiedLyrics, result.text)
    }

    @Test
    fun `manual utf8 is respected even for legacy bytes`() {
        val bytes = encode(simplifiedLyrics, "GBK")

        val result = LyricsEncoding.decodeWithInfo(bytes, LyricsEncoding.Option.UTF_8.id)

        assertEquals(LyricsEncoding.Option.UTF_8, result.option)
        assertTrue(result.text.contains('�'))
    }

    @Test
    fun `mojibake from utf8 bytes read as latin1 is repaired`() {
        val mojibake = String(simplifiedLyrics.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)

        assertEquals(simplifiedLyrics, LyricsEncoding.repairMojibake(mojibake))
    }

    @Test
    fun `mojibake from gbk bytes read as latin1 is repaired`() {
        val mojibake = String(encode(simplifiedLyrics, "GBK"), Charsets.ISO_8859_1)

        assertEquals(simplifiedLyrics, LyricsEncoding.repairMojibake(mojibake))
    }

    @Test
    fun `legitimate latin lyrics are not touched by mojibake repair`() {
        val text = "Café déjà vu, on chante encore ce soir"

        assertEquals(text, LyricsEncoding.repairMojibake(text))
    }

    @Test
    fun `correct chinese lyrics are not touched by mojibake repair`() {
        assertEquals(simplifiedLyrics, LyricsEncoding.repairMojibake(simplifiedLyrics))
    }

    @Test
    fun `unknown option id falls back to detection`() {
        val result = LyricsEncoding.decodeWithInfo(
            simplifiedLyrics.toByteArray(Charsets.UTF_8),
            "not-a-charset"
        )

        assertEquals(LyricsEncoding.Option.UTF_8, result.option)
        assertEquals(simplifiedLyrics, result.text)
    }
}
