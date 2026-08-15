package com.examsystem.app.util

/**
 * Splits question text into plain prose vs LaTeX, so KaTeX only renders expressions —
 * not entire sentences or messy import blobs.
 */
object LatexRender {

    private val KNOWN_CMD = Regex(
        """^(boxed|frac|sqrt|begin|end|text|mathrm|mathbf|vec|overline|underline|hat|bar|left|right|sum|int|lim|sin|cos|tan|log|ln|pm|times|div|cdot|leq|geq|neq|ne|approx|infty|pi|alpha|beta|gamma|delta|theta|lambda|mu|sigma|omega|phi|Delta|Omega|dots|ldots|cdots|quad|qquad)\b"""
    )

    private sealed class Seg {
        data class Plain(val text: String) : Seg()
        data class Math(val latex: String) : Seg()
    }

    fun hasRenderableMath(s: String): Boolean {
        val t = s.trim()
        return t.contains('$') || t.contains('\\')
    }

    /** Clean LaTeX for Firestore (import, save test) — same rules as display. */
    fun normalizeForStorage(raw: String): String =
        simplifyTrivialDollarMath(QuestionParser.normalizeLatex(raw.trim()))

    /** `$5$` → `5` in options; keeps real formulas like `$x^2$` and matrices. */
    private fun simplifyTrivialDollarMath(s: String): String {
        var t = s
        t = t.replace(Regex("""\$\s*(\d+(?:\.\d+)?)\s*\$""")) { it.groupValues[1] }
        return t
    }

    fun normalizeQuestion(q: com.examsystem.app.data.models.Question): com.examsystem.app.data.models.Question =
        q.copy(
            text = normalizeForStorage(q.text),
            optionA = normalizeForStorage(q.optionA),
            optionB = normalizeForStorage(q.optionB),
            optionC = normalizeForStorage(q.optionC),
            optionD = normalizeForStorage(q.optionD)
        )

    fun normalizeTest(test: com.examsystem.app.data.models.Test): com.examsystem.app.data.models.Test =
        test.copy(
            sections = test.sections.map { sec ->
                sec.copy(questions = sec.questions.map { normalizeQuestion(it) })
            }
        )

    /** True when content should be in $...$ (algebra), not plain text or a simple label. */
    fun shouldWrapAsMath(content: String): Boolean {
        val c = content.trim()
        if (c.isEmpty()) return false
        if (c.contains('\\')) return true
        if (Regex("""[\^_]""").containsMatchIn(c)) return true
        if (Regex("""[a-zA-Z].*=.*\d""").containsMatchIn(c)) return true
        if (Regex("""\d+\s*[a-zA-Z]|[a-zA-Z]\s*\d""").containsMatchIn(c)) return true
        // Simple number or word — stay plain (no box, no math font)
        if (c.matches(Regex("""[\d.,]+"""))) return false
        if (c.matches(Regex("""[A-Za-z][\w\s.,'-]{0,40}""")) && !c.contains('=')) return false
        return c.length <= 48 && Regex("""[=+\-*/^]""").containsMatchIn(c)
    }

    /** HTML safe for WebView: plain spans + $...$ math only where needed. */
    fun prepareHtmlForKaTeX(raw: String): String {
        val normalized = normalizeForStorage(raw)
        return segmentsToHtml(segment(normalized))
    }

    private fun segment(input: String): List<Seg> {
        if (input.isEmpty()) return emptyList()
        val out = mutableListOf<Seg>()
        var i = 0
        while (i < input.length) {
            when {
                input[i] == '$' -> {
                    val close = findClosingDollar(input, i + 1)
                    if (close > i) {
                        val inner = input.substring(i + 1, close)
                        if (isLikelyMath(inner)) {
                            out.add(Seg.Math(inner))
                        } else {
                            out.add(Seg.Plain(input.substring(i, close + 1)))
                        }
                        i = close + 1
                    } else {
                        appendPlainChar(out, input[i])
                        i++
                    }
                }
                input.startsWith("\\", i) -> {
                    val end = latexSpanEnd(input, i)
                    if (end > i) {
                        out.add(Seg.Math(input.substring(i, end)))
                        i = end
                    } else {
                        appendPlainChar(out, input[i])
                        i++
                    }
                }
                else -> {
                    val start = i
                    while (i < input.length) {
                        if (input[i] == '$') break
                        if (input.startsWith("\\", i) && latexSpanEnd(input, i) > i) break
                        i++
                    }
                    if (i > start) out.add(Seg.Plain(input.substring(start, i)))
                }
            }
        }
        return mergeAdjacentPlain(out)
    }

    private fun appendPlainChar(out: MutableList<Seg>, c: Char) {
        if (out.isNotEmpty() && out.last() is Seg.Plain) {
            val last = out.removeAt(out.lastIndex) as Seg.Plain
            out.add(Seg.Plain(last.text + c))
        } else {
            out.add(Seg.Plain(c.toString()))
        }
    }

    private fun mergeAdjacentPlain(segs: List<Seg>): List<Seg> {
        if (segs.isEmpty()) return segs
        val merged = mutableListOf<Seg>()
        for (s in segs) {
            when (s) {
                is Seg.Plain -> {
                    if (merged.isNotEmpty() && merged.last() is Seg.Plain) {
                        val p = merged.removeAt(merged.lastIndex) as Seg.Plain
                        merged.add(Seg.Plain(p.text + s.text))
                    } else merged.add(s)
                }
                is Seg.Math -> merged.add(s)
            }
        }
        return merged
    }

    private fun findClosingDollar(s: String, from: Int): Int {
        for (j in from until s.length) {
            if (s[j] == '$' && (j == 0 || s[j - 1] != '\\')) return j
        }
        return -1
    }

    /** End index (exclusive) of a LaTeX command span starting at backslash. */
    internal fun latexSpanEnd(s: String, start: Int): Int {
        if (!s.startsWith("\\", start)) return start
        var i = start + 1
        while (i < s.length && s[i].isLetter()) i++
        val cmd = s.substring(start + 1, i)
        if (cmd.isEmpty()) return start + 1

        if (cmd == "begin") {
            val env = readBraced(s, i) ?: return i
            val envName = env.first
            i = env.second
            val endTag = "\\end{$envName}"
            val idx = s.indexOf(endTag, i, ignoreCase = false)
            return if (idx >= 0) idx + endTag.length else s.length
        }

        if (cmd in setOf("frac")) {
            val a = readBraced(s, i) ?: return i
            i = a.second
            val b = readBraced(s, i) ?: return a.second
            return b.second
        }

        if (KNOWN_CMD.matchEntire(cmd) != null || cmd.length <= 12) {
            var pos = i
            while (pos < s.length && s[pos] == '{') {
                val br = readBraced(s, pos) ?: break
                pos = br.second
            }
            return pos
        }
        return start + 1 + cmd.length
    }

    private fun readBraced(s: String, from: Int): Pair<String, Int>? {
        if (from >= s.length || s[from] != '{') return null
        var depth = 0
        var j = from
        while (j < s.length) {
            when (s[j]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return s.substring(from + 1, j) to j + 1
                    }
                }
            }
            j++
        }
        return null
    }

    internal fun isLikelyMath(content: String): Boolean {
        val c = content.trim()
        if (c.isEmpty()) return false
        if (c.contains('\\')) return true
        if (Regex("""[\^_=+\-*/|<>]""").containsMatchIn(c)) return true
        // Long English sentence wrongly wrapped in $...$
        val words = c.split(Regex("""\s+""")).filter { it.length > 2 }
        if (c.length > 50 && words.size >= 6 && !c.contains('\\')) return false
        return c.length <= 120
    }

    private fun segmentsToHtml(segs: List<Seg>): String {
        return segs.joinToString("") { seg ->
            when (seg) {
                is Seg.Plain -> {
                    val plain = seg.text
                    if (plain.isEmpty()) ""
                    else {
                        val readable = if (plain.contains('\\')) MathUtils.stripLatex(plain) else plain
                        """<span class="text-plain">${escapeHtml(readable)}</span>"""
                    }
                }
                is Seg.Math -> {
                    val cleaned = cleanMathLatex(seg.latex)
                    if (cleaned.isBlank()) ""
                    else "\$${escapeHtml(cleaned)}\$"
                }
            }
        }
    }

    internal fun cleanMathLatex(latex: String): String {
        var s = latex.trim()
        // \boxed / \fbox draw visible frames in KaTeX — keep only the inner answer/expression
        s = unwrapBoxCommands(s)
        s = s.replace(Regex("""\\{2,}(begin|end|frac|sqrt|text|mathrm|alpha|beta|gamma|theta|delta|pi|times|div|pm|le|ge|ne|approx|infty|cdot|sum|int|cases|matrix|bmatrix|pmatrix|vmatrix)""", RegexOption.IGNORE_CASE)) {
            "\\${it.groupValues[1]}"
        }
        return s.replace(Regex("""\s+"""), " ")
    }

    /** Removes LaTeX box frames; repeats until stable for nested \\boxed{\\boxed{x}}. */
    internal fun unwrapBoxCommands(latex: String): String {
        val boxRe = Regex(
            """\\+(?:boxed|fbox|fcolorbox)\s*(?:\{(?:[^{}]|\{[^{}]*\})*\}){1,3}""",
            RegexOption.IGNORE_CASE
        )
        var s = latex
        var prev = ""
        while (s != prev) {
            prev = s
            s = boxRe.replace(s) { match ->
                val inner = Regex("""\{((?:[^{}]|\{[^{}]*\})*)\}""")
                    .findAll(match.value)
                    .map { it.groupValues[1] }
                    .lastOrNull()
                    ?.trim()
                    ?: ""
                inner
            }
        }
        return s.trim()
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
