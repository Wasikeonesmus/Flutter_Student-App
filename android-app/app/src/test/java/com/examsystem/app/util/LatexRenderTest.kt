package com.examsystem.app.util

import org.junit.Assert.*
import org.junit.Test

class LatexRenderTest {

    @Test
    fun `html plain question has no math-inline`() {
        val html = LatexRender.prepareHtmlForKaTeX("Which tool is NOT used by sales?")
        assertTrue(html.contains("text-plain"))
        assertFalse(html.contains("frac"))
    }

    @Test
    fun `html mixed text and fraction`() {
        val html = LatexRender.prepareHtmlForKaTeX("Find \$\\frac{1}{2}\$ of 10")
        assertTrue(html.contains("text-plain"))
        assertTrue(html.contains("frac"))
    }

    @Test
    fun `html bare boxed`() {
        val html = LatexRender.prepareHtmlForKaTeX("The answer is \\boxed{15} marks.")
        assertTrue(html.contains("15"))
        assertTrue(html.contains("text-plain"))
        assertFalse(html.contains("boxed"))
    }

    @Test
    fun `long prose in dollars stays plain`() {
        assertFalse(
            LatexRender.isLikelyMath(
                "Which of the following tools is NOT listed as currently used by the sales team"
            )
        )
    }

    @Test
    fun `unwrap boxed removes frame command`() {
        assertEquals("15", LatexRender.unwrapBoxCommands("\\boxed{15}"))
        assertEquals("x^2", LatexRender.unwrapBoxCommands("\\boxed{\\boxed{x^2}}"))
        assertFalse(LatexRender.prepareHtmlForKaTeX("\\boxed{15}").contains("boxed"))
    }

    @Test
    fun `boxed number becomes plain text not math box`() {
        val html = LatexRender.prepareHtmlForKaTeX("Answer: \\boxed{15}")
        assertTrue(html.contains("text-plain"))
        assertTrue(html.contains("15"))
        assertFalse(html.contains("boxed"))
    }

    @Test
    fun `boxed equation becomes real math`() {
        val stored = LatexRender.normalizeForStorage("Solve \\boxed{2x+5=11}")
        assertTrue(stored.contains("$"))
        assertTrue(stored.contains("2x"))
    }

    @Test
    fun `latex span end covers frac`() {
        val s = "\\frac{a}{b}"
        assertEquals(s.length, LatexRender.latexSpanEnd(s, 0))
    }
}
