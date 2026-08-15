package com.examsystem.app.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Local JVM unit tests for [QuestionParser].
 * Run via: Android Studio → right-click QuestionParserTest → "Run"
 * No emulator or device required.
 */
class QuestionParserTest {

    // ─── Helper ──────────────────────────────────────────────────────────────

    private fun parse(text: String) = QuestionParser.parse(text)
    private fun latex(text: String) = QuestionParser.normalizeLatex(text)

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 1 – normalizeLatex
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `latex - dollar signs are preserved verbatim`() {
        val result = latex("Find \$x^2 + 1\$")
        assertEquals("Find \$x^2 + 1\$", result)
    }

    @Test
    fun `latex - inline parens converted to dollar`() {
        val result = latex("""\(x^2\)""")
        assertEquals("\$x^2\$", result)
    }

    @Test
    fun `latex - square bracket display math converted to dollar`() {
        val result = latex("""\[x^2 + y^2 = r^2\]""")
        assertEquals("\$x^2 + y^2 = r^2\$", result)
    }

    @Test
    fun `latex - double dollar collapsed to single dollar`() {
        // Use regular string — $E in raw """ strings is Kotlin template interpolation
        val result = latex("\$\$E = mc^2\$\$")
        // $$...$$ → $...$
        assertEquals("\$E = mc^2\$", result)
    }

    @Test
    fun `latex - bare pi outside math becomes unicode`() {
        val result = latex("Area = \\pi r^2")
        assertEquals("Area = π r^2", result)
    }

    @Test
    fun `latex - bare times outside math becomes unicode`() {
        val result = latex("3 \\times 4 = 12")
        assertEquals("3 × 4 = 12", result)
    }

    @Test
    fun `latex - operators inside dollar block are untouched`() {
        // \pi inside $...$ must NOT be replaced — KaTeX will render it
        val result = latex("\$\\pi r^2\$")
        assertEquals("\$\\pi r^2\$", result)
    }

    @Test
    fun `latex - mixed inside and outside math`() {
        val result = latex("Value \\pm \$\\frac{a}{b}\$ and \\infty")
        assertEquals("Value ± \$\\frac{a}{b}\$ and ∞", result)
    }

    @Test
    fun `latex - leq geq neq replaced outside math`() {
        val r = latex("a \\leq b \\geq c \\neq d")
        assertEquals("a ≤ b ≥ c ≠ d", r)
    }

    @Test
    fun `latex - empty string returns empty`() {
        assertEquals("", latex(""))
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 2 – parse: basic clean format
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `parse - single clean question`() {
        val text = """
            1. What is 2 + 2?
            A. 3
            *B. 4
            C. 5
            D. 6
        """.trimIndent()
        val result = parse(text)
        assertEquals(1, result.size)
        val q = result[0]
        assertEquals("What is 2 + 2?", q.text)
        assertEquals("3", q.optionA)
        assertEquals("4", q.optionB)
        assertEquals("5", q.optionC)
        assertEquals("6", q.optionD)
        assertEquals("B", q.correctAnswer)
        assertEquals(1, q.marks)
    }

    @Test
    fun `parse - multiple clean questions`() {
        val text = """
            1. Question one?
            A. A1
            B. B1
            *C. C1
            D. D1
            2. Question two?
            *A. A2
            B. B2
            C. C2
            D. D2
        """.trimIndent()
        val result = parse(text)
        assertEquals(2, result.size)
        assertEquals("C", result[0].correctAnswer)
        assertEquals("A", result[1].correctAnswer)
    }

    @Test
    fun `parse - returns empty list for blank input`() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse("   \n\n  ").isEmpty())
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 3 – parse: question number formats
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `parse - Q1 dot format`() {
        val text = "Q1. What color is the sky?\nA. Red\n*B. Blue\nC. Green\nD. Yellow"
        val result = parse(text)
        assertEquals(1, result.size)
        assertEquals("What color is the sky?", result[0].text)
        assertEquals("B", result[0].correctAnswer)
    }

    @Test
    fun `parse - Question 1 colon format`() {
        val text = "Question 1: What is H2O?\nA. Oil\nB. Gas\n*C. Water\nD. Salt"
        val result = parse(text)
        assertEquals(1, result.size)
        assertEquals("C", result[0].correctAnswer)
    }

    @Test
    fun `parse - markdown bold question number`() {
        val text = "**1.** What is 1+1?\nA. 1\n*B. 2\nC. 3\nD. 4"
        val result = parse(text)
        assertEquals(1, result.size)
        assertEquals("B", result[0].correctAnswer)
    }

    @Test
    fun `parse - no number prefix treated as single question`() {
        val text = "What is gravity?\nA. Push\n*B. Pull\nC. Spin\nD. None"
        val result = parse(text)
        assertEquals(1, result.size)
        assertEquals("What is gravity?", result[0].text)
        assertEquals("B", result[0].correctAnswer)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 4 – parse: option formats
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `parse - parenthesis option format`() {
        val text = "1. Question?\n(A) Option A\n*(B) Option B\n(C) Option C\n(D) Option D"
        val result = parse(text)
        assertEquals(1, result.size)
        assertEquals("Option A", result[0].optionA)
        assertEquals("Option B", result[0].optionB)
        assertEquals("B", result[0].correctAnswer)
    }

    @Test
    fun `parse - bracket option format`() {
        val text = "1. Question?\n[A] Option A\n[B] Option B\n*[C] Option C\n[D] Option D"
        val result = parse(text)
        assertEquals(1, result.size)
        assertEquals("C", result[0].correctAnswer)
    }

    @Test
    fun `parse - colon option format`() {
        val text = "1. Q?\nA: First\nB: Second\n*C: Third\nD: Fourth"
        val result = parse(text)
        assertEquals(1, result.size)
        assertEquals("Third", result[0].optionC)
        assertEquals("C", result[0].correctAnswer)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 5 – parse: correct answer detection
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `parse - star marker on option`() {
        val text = "1. Q?\nA. W\n*B. X\nC. Y\nD. Z"
        assertEquals("B", parse(text)[0].correctAnswer)
    }

    @Test
    fun `parse - CORRECT label in option line`() {
        val text = "1. Q?\nA. W\nB. X (CORRECT)\nC. Y\nD. Z"
        val r = parse(text)
        assertEquals("B", r[0].correctAnswer)
        // "(CORRECT)" should be stripped from option text
        assertFalse(r[0].optionB.contains("CORRECT", ignoreCase = true))
    }

    @Test
    fun `parse - checkmark emoji in option`() {
        val text = "1. Q?\nA. W\nB. X ✔\nC. Y\nD. Z"
        val r = parse(text)
        assertEquals("B", r[0].correctAnswer)
        assertFalse(r[0].optionB.contains("✔"))
    }

    @Test
    fun `parse - green check emoji in option`() {
        val text = "1. Q?\nA. W\nB. X\nC. Y ✅\nD. Z"
        val r = parse(text)
        assertEquals("C", r[0].correctAnswer)
    }

    @Test
    fun `parse - standalone ANSWER line`() {
        val text = "1. Q?\nA. W\nB. X\nC. Y\nD. Z\nANSWER: D"
        assertEquals("D", parse(text)[0].correctAnswer)
    }

    @Test
    fun `parse - standalone CORRECT line`() {
        val text = "1. Q?\nA. W\nB. X\nC. Y\nD. Z\nCORRECT: C"
        assertEquals("C", parse(text)[0].correctAnswer)
    }

    @Test
    fun `parse - standalone ANS line`() {
        val text = "1. Q?\nA. W\nB. X\nC. Y\nD. Z\nANS: B"
        assertEquals("B", parse(text)[0].correctAnswer)
    }

    @Test
    fun `parse - CORRECT ANSWER line`() {
        val text = "1. Q?\nA. W\nB. X\nC. Y\nD. Z\nCORRECT ANSWER: A"
        assertEquals("A", parse(text)[0].correctAnswer)
    }

    @Test
    fun `parse - defaults to A when no marker present`() {
        val text = "1. Q?\nA. W\nB. X\nC. Y\nD. Z"
        assertEquals("A", parse(text)[0].correctAnswer)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 6 – parse: marks
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `parse - marks line is parsed`() {
        val text = "1. Q?\nA. W\n*B. X\nC. Y\nD. Z\nMarks: 3"
        assertEquals(3, parse(text)[0].marks)
    }

    @Test
    fun `parse - default marks is 1`() {
        val text = "1. Q?\nA. W\n*B. X\nC. Y\nD. Z"
        assertEquals(1, parse(text)[0].marks)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 7 – parse: messy / real-world input
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `parse - extra whitespace and tabs collapsed`() {
        val text = "1.   What  is  2+2?\n A.   Three\n *B.   Four\n C.   Five\n D.   Six"
        val r = parse(text)
        assertEquals(1, r.size)
        assertEquals("B", r[0].correctAnswer)
    }

    @Test
    fun `parse - CRLF line endings handled`() {
        val text = "1. What is 1+1?\r\nA. 0\r\n*B. 2\r\nC. 3\r\nD. 4"
        val r = parse(text)
        assertEquals(1, r.size)
        assertEquals("B", r[0].correctAnswer)
    }

    @Test
    fun `parse - markdown bold stripped from options`() {
        val text = "1. Q?\n**A.** W\n***B.** X (correct)\n**C.** Y\n**D.** Z"
        val r = parse(text)
        // Bold markers stripped; option text clean
        assertFalse(r[0].optionA.contains("**"))
    }

    @Test
    fun `parse - markdown italics surrounding option letter stripped`() {
        val text = "1. What is determinant?\nA. 5\n*B.* \$11\$\nC. 14\nD. -5"
        val r = parse(text)
        assertEquals(1, r.size)
        assertEquals("B", r[0].correctAnswer)
        assertEquals("\$11\$", r[0].optionB)
        assertFalse(r[0].optionB.contains("*"))
    }

    @Test
    fun `parse - separator lines ignored`() {
        val text = "1. Q?\n---\nA. W\n*B. X\n___\nC. Y\nD. Z"
        val r = parse(text)
        assertEquals(1, r.size)
        assertEquals("B", r[0].correctAnswer)
    }

    @Test
    fun `parse - missing options get placeholder text`() {
        val text = "1. Q?\n*A. Only one option"
        val r = parse(text)
        assertEquals(1, r.size)
        assertEquals("Option B", r[0].optionB)
        assertEquals("Option C", r[0].optionC)
        assertEquals("Option D", r[0].optionD)
    }

    @Test
    fun `parse - 10+ questions bulk paste`() {
        val sb = StringBuilder()
        for (i in 1..15) {
            sb.appendLine("$i. Question number $i?")
            sb.appendLine("A. Wrong 1")
            sb.appendLine("*B. Correct")
            sb.appendLine("C. Wrong 2")
            sb.appendLine("D. Wrong 3")
        }
        val r = parse(sb.toString())
        assertEquals(15, r.size)
        r.forEach { assertEquals("B", it.correctAnswer) }
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 8 – parse: KaTeX / equation handling
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `parse - dollar signs preserved through parsing`() {
        val text = "1. Solve \$x^2 - 4 = 0\$?\nA. x=1\n*B. x=±2\nC. x=4\nD. x=0"
        val r = parse(text)
        assertTrue(r[0].text.contains("\$x^2 - 4 = 0\$"))
    }

    @Test
    fun `parse - inline parens converted through parsing`() {
        val text = "1. Find the value of \\(\\pi r^2\\)?\nA. 2πr\n*B. πr²\nC. 4πr²\nD. πr"
        val r = parse(text)
        // \(...\) should have been converted to $...$
        assertTrue(r[0].text.contains("\$"))
    }

    @Test
    fun `parse - bare latex operator in question text`() {
        val text = "1. What is 3 \\times 4?\nA. 7\n*B. 12\nC. 34\nD. 1"
        val r = parse(text)
        // \times outside math → ×
        assertTrue(r[0].text.contains("×"))
    }

    @Test
    fun `parse - katex inside dollar not mutated`() {
        val text = "1. Simplify \$\\frac{a}{b} + \\frac{c}{d}\$?\n*A. Same\nB. Diff\nC. Zero\nD. One"
        val r = parse(text)
        // \frac should survive untouched inside $...$
        assertTrue(r[0].text.contains("\\frac"))
    }

    @Test
    fun `parse - question with complex equation in option`() {
        val text = "1. Which is correct?\nA. \$x=1\$\n*B. \$x=\\pm\\sqrt{2}\$\nC. \$x=0\$\nD. \$x=-1\$"
        val r = parse(text)
        assertTrue(r[0].optionB.contains("\$"))
        assertEquals("B", r[0].correctAnswer)
    }

    @Test
    fun `latex - auto wraps bare matrix and includes variable prefix`() {
        val r = latex("If A=\\begin{bmatrix}2&1\\\\3&4\\end{bmatrix}")
        assertEquals("If \$A=\\begin{bmatrix}2&1\\\\3&4\\end{bmatrix}\$", r)
    }

    @Test
    fun `latex - fixes single backslash OCR errors in matrices`() {
        // \3 should become \\3
        val r = latex("A=\\begin{bmatrix}2&1\\3&4\\end{bmatrix}")
        assertEquals("\$A=\\begin{bmatrix}2&1\\\\3&4\\end{bmatrix}\$", r)
    }

    @Test
    fun `latex - fixes single backslash OCR errors in cases`() {
        // \x should become \\x
        val r = latex("\\begin{cases}2x+y=7\\x-y=1\\end{cases}")
        assertEquals("\$\\begin{cases}2x+y=7\\\\x-y=1\\end{cases}\$", r)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 9 – parse: regression tests
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `parse - regression test - numeric options do not split questions`() {
        // Bug: 'A. 5' caused a split because 5 matched \d+ without start-of-line anchor
        val text = "1. What is 2+3?\nA. 5\n*B. 8\nC. 11\nD. 14\n2. Next Q\n*A. 1\nB. 2\nC. 3\nD. 4"
        val r = parse(text)
        assertEquals(2, r.size)
        assertEquals("What is 2+3?", r[0].text)
        assertEquals("5", r[0].optionA)
        assertEquals("1", r[1].optionA)
    }

    @Test
    fun `parse - regression test - math variables are not treated as options`() {
        // Bug: 'A = \begin' was treated as Option A because \s matched the space after A
        val text = "1. If\nA = \\begin{bmatrix}2&1\\3&4\\end{bmatrix}\nFind det(A)\nA. 5\n*B. 8\nC. 11\nD. 14"
        val r = parse(text)
        assertEquals(1, r.size)
        assertTrue("Question text should contain the matrix", r[0].text.contains("begin{bmatrix}"))
        assertTrue("Question text should reference matrix A", r[0].text.contains("A") && r[0].text.contains("="))
        assertEquals("5", r[0].optionA)
        assertEquals("8", r[0].optionB)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 10 – MathUtils.stripLatex tests
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `stripLatex - boxed expressions`() {
        assertEquals("15", MathUtils.stripLatex("\\boxed{15}"))
        assertEquals("x^2", MathUtils.stripLatex("\\boxed{x^2}"))
        assertEquals("Ans", MathUtils.stripLatex("\\boxed{\\text{Ans}}"))
    }

    @Test
    fun `stripLatex - general brace commands`() {
        assertEquals("x", MathUtils.stripLatex("\\vec{x}"))
        assertEquals("AB", MathUtils.stripLatex("\\overline{AB}"))
        assertEquals("CD", MathUtils.stripLatex("\\underline{CD}"))
        assertEquals("θ", MathUtils.stripLatex("\\hat{\\theta}"))
        assertEquals("x", MathUtils.stripLatex("\\overline{\\vec{x}}"))
    }

    @Test
    fun `stripLatex - complex expression`() {
        assertEquals("√(x + y)", MathUtils.stripLatex("\\sqrt{x + \\boxed{y}}"))
        assertEquals("Let f(x) = x^2 + 2x. Find f'(x).", MathUtils.stripLatex("Let \$f(x) = x^2 + 2x\$. Find \$f'(x)\$."))
    }
}
