package com.music.msv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ImslpParsing 纯解析层单测。
 * 这些用例同时充当"站点格式契约"：IMSLP 改版后若选择器/关键词变化，先改用例固化新格式，再改 ImslpParsing。
 */
class ImslpParsingTest {

    // ---------- isNonWork ----------

    @Test
    fun isNonWork_categoryNamespace_returnsTrue() {
        assertTrue(ImslpParsing.isNonWork("Category:Mozart, Wolfgang Amadeus"))
    }

    @Test
    fun isNonWork_normalWorkTitle_returnsFalse() {
        assertFalse(ImslpParsing.isNonWork("Piano Sonata No.14, Op.27 No.2"))
    }

    @Test
    fun isNonWork_fileAndWishlistPrefixes_returnTrue() {
        assertTrue(ImslpParsing.isNonWork("File:Foo.pdf"))
        assertTrue(ImslpParsing.isNonWork("Wishlist"))
    }

    // ---------- cleanSnippet ----------

    @Test
    fun cleanSnippet_stripsHtmlTags() {
        assertEquals("hello world", ImslpParsing.cleanSnippet("""<span class="searchmatch">hello</span> world"""))
    }

    @Test
    fun cleanSnippet_dropsTemplateParameterLines() {
        assertEquals("Piano", ImslpParsing.cleanSnippet("|Performer Categories=Piano\nPiano"))
    }

    @Test
    fun cleanSnippet_unwrapsWikiLinksKeepingDisplayText() {
        assertEquals("Beethoven", ImslpParsing.cleanSnippet("[[Beethoven|Beethoven]]"))
    }

    @Test
    fun cleanSnippet_removesTemplatesAndCollapsesWhitespace() {
        assertEquals("Sonata", ImslpParsing.cleanSnippet("{{cleanup}}   Sonata\n\n"))
    }

    @Test
    fun cleanSnippet_decodesHtmlEntities() {
        assertEquals("a & b < c > d \" e ' f", ImslpParsing.cleanSnippet("a &amp; b &lt; c &gt; d &quot; e &#39; f"))
    }

    @Test
    fun cleanSnippet_emptyAfterCleaning_returnsEmpty() {
        assertEquals("", ImslpParsing.cleanSnippet("{{template}}"))
    }

    // ---------- resolveUrl ----------

    @Test
    fun resolveUrl_absoluteHttp_unchanged() {
        assertEquals("https://imslp.org/x.pdf", ImslpParsing.resolveUrl("https://imslp.org/x.pdf"))
    }

    @Test
    fun resolveUrl_protocolRelative_prefixedWithHttps() {
        assertEquals("https://imslp.org/x.pdf", ImslpParsing.resolveUrl("//imslp.org/x.pdf"))
    }

    @Test
    fun resolveUrl_rootRelative_prefixedWithBase() {
        assertEquals("https://imslp.org/wiki/Main", ImslpParsing.resolveUrl("/wiki/Main"))
    }

    @Test
    fun resolveUrl_bareRelative_unchanged() {
        assertEquals("x.pdf", ImslpParsing.resolveUrl("x.pdf"))
    }

    // ---------- nextUrlFromHtml ----------

    @Test
    fun nextUrlFromHtml_botCheckGate_returnsNull() {
        assertNull(ImslpParsing.nextUrlFromHtml("<html>Bot Check required</html>"))
    }

    @Test
    fun nextUrlFromHtml_startVerificationGate_returnsNull() {
        assertNull(ImslpParsing.nextUrlFromHtml("Start Verification now"))
    }

    @Test
    fun nextUrlFromHtml_waitPageDataId_resolvesToAbsolute() {
        val html = """<div id="sm_dl_wait" data-id="//imslp.org/images/a/b/x.pdf"></div>"""
        assertEquals("https://imslp.org/images/a/b/x.pdf", ImslpParsing.nextUrlFromHtml(html))
    }

    @Test
    fun nextUrlFromHtml_dataIdRootRelativePdf_resolved() {
        val html = """<span data-id="/images/aa/bb/score.pdf">wait</span>"""
        assertEquals("https://imslp.org/images/aa/bb/score.pdf", ImslpParsing.nextUrlFromHtml(html))
    }

    @Test
    fun nextUrlFromHtml_dataIdNotUsable_fallsBackToMetaRefresh() {
        val html = """<div data-id="javascript:void(0)"></div><meta content="3; url=/foo.pdf">"""
        assertEquals("https://imslp.org/foo.pdf", ImslpParsing.nextUrlFromHtml(html))
    }

    @Test
    fun nextUrlFromHtml_locationHrefFallback() {
        val html = """<script>location.href = 'https://imslp.org/images/z/z/z.pdf';</script>"""
        assertEquals("https://imslp.org/images/z/z/z.pdf", ImslpParsing.nextUrlFromHtml(html))
    }

    @Test
    fun nextUrlFromHtml_noSignal_returnsNull() {
        assertNull(ImslpParsing.nextUrlFromHtml("<html><body>plain page, nothing to follow</body></html>"))
    }
}
