package com.examsystem.app.util

import com.examsystem.app.data.models.Question
import java.util.UUID

/**
 * Pure-Kotlin MCQ parser — no Android or Compose dependencies.
 * Can be unit-tested with plain JUnit on the JVM.
 */
object QuestionParser {

    // ─── LaTeX Normalizer ────────────────────────────────────────────────────

    /**
     * Normalizes LaTeX in [input] while PRESERVING $...$ KaTeX delimiters.
     *
     * 1. \(...\)  → $...$
     * 2. \[...\]  → $...$
     * 3. $$...$$ → $...$  (display math collapsed to inline)
     * 4. Content inside existing $...$ blocks is left completely untouched.
     * 5. Bare LaTeX operators OUTSIDE math blocks are replaced with Unicode.
     */
    fun normalizeLatex(input: String): String {
        var t = input

        // 1. \(...\) → $...$
        t = t.replace(
            Regex("""\\\((.+?)\\\)""", RegexOption.DOT_MATCHES_ALL)
        ) { "\$${it.groupValues[1]}\$" }

        // 2. \[...\] → $...$
        t = t.replace(
            Regex("""\\\[(.+?)\\\]""", RegexOption.DOT_MATCHES_ALL)
        ) { "\$${it.groupValues[1]}\$" }

        // 3. $$...$$ → $...$
        t = t.replace(
            Regex("""\$\$(.+?)\$\$""", RegexOption.DOT_MATCHES_ALL)
        ) { "\$${it.groupValues[1]}\$" }

        // Collapse whitespace only inside $...$ — keep spaces before/after delimiters
        t = t.replace(
            Regex("""\$(.*?)\$""", RegexOption.DOT_MATCHES_ALL)
        ) { m ->
            val inner = LatexRender.unwrapBoxCommands(
                m.groupValues[1].replace(Regex("""\s+"""), " ").trim()
            )
            "\$$inner\$"
        }

        // 3b. Bare \\boxed{...} / \\fbox{...} (PDF imports) — no frame; math only when needed
        var prev = ""
        while (prev != t) {
            prev = t
            t = Regex(
                """\\+(?:boxed|fbox)\s*\{((?:[^{}]|\{[^{}]*\})*)\}""",
                RegexOption.IGNORE_CASE
            ).replace(t) { m ->
                val inner = LatexRender.unwrapBoxCommands(m.value)
                when {
                    inner.isBlank() -> ""
                    LatexRender.shouldWrapAsMath(inner) -> "\$$inner\$"
                    else -> inner
                }
            }
        }

        // 4. Auto-wrap bare \begin{...} ... \end{...} environments and fix common OCR backslash errors
        t = t.replace(
            Regex("""(?:([A-Za-z])\s*=\s*)?\\begin\{([a-zA-Z*]+)\}(.*?)\\end\{\2\}""", RegexOption.DOT_MATCHES_ALL)
        ) { match -> 
            val prefix = match.groupValues[1].let { if (it.isNotEmpty()) "$it=" else "" }
            val env = match.groupValues[2]
            var content = match.groupValues[3]
            
            // OCR tools often output `\3` or `\x` instead of `\\ 3` or `\\ x` for row breaks.
            // This safely converts a single backslash followed by a digit or x/y/z into a double backslash.
            content = content.replace(Regex("""(?<!\\)\\([0-9xyz])"""), "\\\\\\\\$1")
            
            // Check if this match is already inside an existing math block (odd number of dollar signs before it)
            val beforeText = t.substring(0, match.range.first)
            val dollarCount = beforeText.count { it == '$' }
            val isAlreadyInMath = (dollarCount % 2 != 0)
            
            if (isAlreadyInMath) {
                // Reconstruct exactly as-is without double-wrapping in new dollar signs
                val origPrefix = match.groupValues[1].let { if (it.isNotEmpty()) "$it=" else "" }
                "${origPrefix}\\begin{$env}$content\\end{$env}"
            } else {
                // Wrap in dollar signs for KaTeX rendering
                "\$$prefix\\begin{$env}$content\\end{$env}\$"
            }
        }

        // 4+5. Walk char-by-char: inside $ ... $ → verbatim; outside → unicode subs
        val substitutions = listOf(
            "\\times" to "×", "\\div" to "÷", "\\pm" to "±",
            "\\leq" to "≤", "\\geq" to "≥", "\\neq" to "≠",
            "\\approx" to "≈", "\\infty" to "∞", "\\cdot" to "·",
            "\\pi" to "π", "\\alpha" to "α", "\\beta" to "β",
            "\\gamma" to "γ", "\\theta" to "θ", "\\Delta" to "Δ",
            "\\mu" to "μ", "\\Omega" to "Ω", "\\degree" to "°",
            "^\\circ" to "°", "\\circ" to "°",
            "\\dots" to "…", "\\ldots" to "…", "\\cdots" to "…",
            "\\sum" to "∑", "\\int" to "∫"
        )

        val sb = StringBuilder()
        var inMath = false
        var i = 0
        while (i < t.length) {
            when {
                t[i] == '$' -> {
                    inMath = !inMath
                    sb.append('$')
                    i++
                }
                inMath -> {
                    sb.append(t[i])
                    i++
                }
                else -> {
                    var matched = false
                    for ((from, to) in substitutions) {
                        if (t.startsWith(from, i)) {
                            sb.append(to)
                            i += from.length
                            matched = true
                            break
                        }
                    }
                    if (!matched) { sb.append(t[i]); i++ }
                }
            }
        }
        return sb.toString().trim()
    }

    // ─── Question Parser ─────────────────────────────────────────────────────

    /**
     * Parses [rawText] — which may be messy, multi-format, copy-pasted MCQ text —
     * into a list of [Question] objects.
     *
     * Supported question-number formats:
     *   1.  1)  1/  Q1.  Q1:  Question 1.  Question 1)  **1.**  #1.
     *
     * Supported option formats:
     *   A.  A)  A:  (A)  [A]  — case-insensitive
     *
     * Correct-answer markers:
     *   *  before the line  |  (CORRECT) / [CORRECT] / ✔ / ✅ in the line
     *   Standalone "CORRECT: B" / "ANSWER: B" / "ANS: B" lines
     */
    fun parse(rawText: String): List<Question> {

        // ── Pre-processing ──────────────────────────────────────────────────
        val cleaned = rawText
            .replace("\r\n", "\n").replace("\r", "\n")   // CRLF → LF
            .replace(Regex("[ \t]+"), " ")                // collapse spaces/tabs
            .replace(Regex("\n{3,}"), "\n\n")             // max 2 consecutive blank lines
            .trim()

        // ── Split into question blocks ───────────────────────────────────────
        // Matches: optional leading *, #, _ chars, then Q1. / Question 1. / 1. / 1) etc.
        val qStartRegex = Regex("""(?m)^(?:[*#_]{0,3}\s*)?(?:(?:Q(?:uestion)?\s*\d+[:.)])|(?:\d{1,3}[.):/]))""")

        val firstLine = cleaned.lines().firstOrNull { it.isNotBlank() } ?: ""
        // If the very first line is not a question header, treat the whole blob as Q1
        val normalizedText = if (!qStartRegex.containsMatchIn(firstLine)) "1. $cleaned" else cleaned

        val splitRegex = Regex("""(?m)(?=^(?:[*#_]{0,3}\s*)?(?:(?:Q(?:uestion)?\s*\d+[:.)])|(?:\d{1,3}[.):/])))""")
        val blocks = normalizedText.split(splitRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // ── Parse each block ─────────────────────────────────────────────────
        return blocks.mapIndexedNotNull { index, block ->
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }

            val qLines = mutableListOf<String>()
            var optA = ""; var optB = ""; var optC = ""; var optD = ""
            var correct = "A"
            var marks = 1
            var currentMode = "Q"

            for (rawLine in lines) {
                // Strip markdown bold (**text**)
                val debolded = rawLine.trim().replace(Regex("""\*\*"""), "").trim()

                val isMarkedCorrect = debolded.startsWith("*") ||
                    debolded.contains("(CORRECT)", ignoreCase = true) ||
                    debolded.contains("[CORRECT]", ignoreCase = true) ||
                    debolded.contains("✔") || debolded.contains("✅")

                // Strip leading *, #, _ (markdown / correct markers)
                val line = debolded.replace(Regex("""^[*#_]+\s*"""), "").trim()

                if (line.isEmpty() || line == "---" || line == "___") continue

                val upperLine = line.uppercase()

                when {
                    // ── Option A ── (anchored; exclude "A = …" matrix / variable lines)
                    Regex("""^(?!A\s*=)(?:A[:.)]\s*|\(A\)\s*|\[A\]\s*)""", RegexOption.IGNORE_CASE).containsMatchIn(line) -> {
                        currentMode = "A"
                        if (isMarkedCorrect) correct = "A"
                        optA = line
                            .replaceFirst(Regex("""^(?:A[:.)][*_]?|\(A\)[*_]?|\[A\][*_]?)\s*""", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("""\s*\(?\[?CORRECT\]?\)?\s*""", RegexOption.IGNORE_CASE), "")
                            .replace("✔", "").replace("✅", "").trim()
                    }
                    // ── Option B ──
                    Regex("""^(?!B\s*=)(?:B[:.)]\s*|\(B\)\s*|\[B\]\s*)""", RegexOption.IGNORE_CASE).containsMatchIn(line) -> {
                        currentMode = "B"
                        if (isMarkedCorrect) correct = "B"
                        optB = line
                            .replaceFirst(Regex("""^(?:B[:.)][*_]?|\(B\)[*_]?|\[B\][*_]?)\s*""", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("""\s*\(?\[?CORRECT\]?\)?\s*""", RegexOption.IGNORE_CASE), "")
                            .replace("✔", "").replace("✅", "").trim()
                    }
                    // ── Option C ──
                    Regex("""^(?!C\s*=)(?:C[:.)]\s*|\(C\)\s*|\[C\]\s*)""", RegexOption.IGNORE_CASE).containsMatchIn(line) -> {
                        currentMode = "C"
                        if (isMarkedCorrect) correct = "C"
                        optC = line
                            .replaceFirst(Regex("""^(?:C[:.)][*_]?|\(C\)[*_]?|\[C\][*_]?)\s*""", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("""\s*\(?\[?CORRECT\]?\)?\s*""", RegexOption.IGNORE_CASE), "")
                            .replace("✔", "").replace("✅", "").trim()
                    }
                    // ── Option D ──
                    Regex("""^(?!D\s*=)(?:D[:.)]\s*|\(D\)\s*|\[D\]\s*)""", RegexOption.IGNORE_CASE).containsMatchIn(line) -> {
                        currentMode = "D"
                        if (isMarkedCorrect) correct = "D"
                        optD = line
                            .replaceFirst(Regex("""^(?:D[:.)][*_]?|\(D\)[*_]?|\[D\][*_]?)\s*""", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("""\s*\(?\[?CORRECT\]?\)?\s*""", RegexOption.IGNORE_CASE), "")
                            .replace("✔", "").replace("✅", "").trim()
                    }
                    // ── Explicit answer line ──
                    upperLine.startsWith("CORRECT ANSWER:") ||
                    upperLine.startsWith("CORRECT:") ||
                    upperLine.startsWith("ANSWER:") ||
                    upperLine.startsWith("ANS:") -> {
                        currentMode = "Correct"
                        val ansStr = line.substringAfter(":").trim().uppercase()
                        Regex("[A-D]").find(ansStr)?.let { correct = it.value }
                    }
                    // ── Marks line ──
                    upperLine.startsWith("MARKS:") || upperLine.startsWith("MARK:") || upperLine.startsWith("POINTS:") -> {
                        currentMode = "Marks"
                        marks = Regex("\\d+").find(line.substringAfter(":"))?.value?.toIntOrNull() ?: 1
                    }
                    else -> when (currentMode) {
                        "Q" -> {
                            // Strip question number prefix on the first line only
                            val cleanLine = if (qLines.isEmpty()) {
                                line.replaceFirst(
                                    Regex("""^[*#_]*\s*(?:(?:Q|Question)\s*\d*[:.)]|\d+[:/.)])\s*""",
                                    RegexOption.IGNORE_CASE
                                ), ""
                            ).trim()
                            } else line
                            if (cleanLine.isNotEmpty()) qLines.add(cleanLine)
                        }
                        "A" -> optA += "\n$line"
                        "B" -> optB += "\n$line"
                        "C" -> optC += "\n$line"
                        "D" -> optD += "\n$line"
                    }
                }
            }

            val qText = qLines.joinToString("\n").trim()
            if (qText.isEmpty()) return@mapIndexedNotNull null

            val stableId = UUID.nameUUIDFromBytes("$index|${qText}".toByteArray()).toString()
            Question(
                id = stableId,
                text = LatexRender.normalizeForStorage(qText),
                optionA = LatexRender.normalizeForStorage(optA.replace("\n", " ").trim()).ifEmpty { "Option A" },
                optionB = LatexRender.normalizeForStorage(optB.replace("\n", " ").trim()).ifEmpty { "Option B" },
                optionC = LatexRender.normalizeForStorage(optC.replace("\n", " ").trim()).ifEmpty { "Option C" },
                optionD = LatexRender.normalizeForStorage(optD.replace("\n", " ").trim()).ifEmpty { "Option D" },
                correctAnswer = if (correct in listOf("A", "B", "C", "D")) correct else "A",
                marks = marks
            )
        }
    }
}
