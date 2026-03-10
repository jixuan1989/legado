package io.legado.app.model.localBook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 单元测试：EPUB 章节 url 到音频 href 的推导规则。
 */
class EpubFileTest {

    @Test
    fun deriveAudioHrefFromChapterUrl_withTextPath_returnsMp3Path() {
        assertEquals(
            "OEBPS/audio/chapter_000.mp3",
            EpubFile.deriveAudioHrefFromChapterUrl("OEBPS/text/chapter_000.xhtml")
        )
        assertEquals(
            "audio/chapter_001.mp3",
            EpubFile.deriveAudioHrefFromChapterUrl("text/chapter_001.xhtml")
        )
        assertEquals(
            "OEBPS/audio/chapter_002.mp3",
            EpubFile.deriveAudioHrefFromChapterUrl("OEBPS/text/chapter_002.xhtml#frag")
        )
    }

    @Test
    fun deriveAudioHrefFromChapterUrl_withoutTextPath_returnsNull() {
        assertNull(EpubFile.deriveAudioHrefFromChapterUrl("OEBPS/nav.xhtml"))
        assertNull(EpubFile.deriveAudioHrefFromChapterUrl(""))
    }
}
