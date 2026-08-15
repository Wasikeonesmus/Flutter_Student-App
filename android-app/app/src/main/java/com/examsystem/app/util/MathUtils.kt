package com.examsystem.app.util

object MathUtils {
    /**
     * Strips LaTeX delimiters and converts common commands to readable Unicode/text
     * so that text contexts (which cannot render KaTeX) are still human-readable.
     */
    fun stripLatex(input: String): String {
        var s = input
        
        // 1. Normalize consecutive backslashes followed by letters down to a single backslash
        s = s.replace(Regex("""\\{2,}([a-zA-Z])""")) { m -> "\\" + m.groupValues[1] }
        
        // 2. Remove matrix and environment blocks but keep contents, separated by spaces
        s = s.replace(Regex("""\\+begin\{[a-zA-Z*]+\}"""), "")
        s = s.replace(Regex("""\\+end\{[a-zA-Z*]+\}"""), "")
        s = s.replace("&", "   ") // space out matrix columns
        s = s.replace("\\\\", "\n") // break lines for matrix rows
        
        // 3. Specific formatting commands (bold, italic, text style)
        s = s.replace(Regex("""\\+text\s*\{([^}]*)\}""")) { m -> m.groupValues[1] }
        s = s.replace(Regex("""\\+mathrm\s*\{([^}]*)\}""")) { m -> m.groupValues[1] }
        s = s.replace(Regex("""\\+textbf\s*\{([^}]*)\}""")) { m -> m.groupValues[1] }
        s = s.replace(Regex("""\\+textit\s*\{([^}]*)\}""")) { m -> m.groupValues[1] }
        s = s.replace(Regex("""\\+math(sf|bb|cal|frak|bf|it)\s*\{([^}]*)\}""")) { m -> m.groupValues[2] }

        // 4. Fractions: \frac{a}{b} → a/b
        s = s.replace(Regex("""\\+frac\s*\{([^}]*)\}\s*\{([^}]*)\}""")) { m ->
            val num = m.groupValues[1].trim()
            val den = m.groupValues[2].trim()
            if (num.all { it.isDigit() || it.isLetter() || it == '-' || it == '+' } &&
                den.all { it.isDigit() || it.isLetter() || it == '-' || it == '+' }) {
                "$num/$den"
            } else {
                "($num)/($den)"
            }
        }
        
        // 5. Boxed before sqrt/frac — inner braces must be resolved first
        s = s.replace(Regex("""\\+boxed\s*\{([^}]*)\}""")) { m -> m.groupValues[1].trim() }
        s = s.replace(Regex("""\\+fbox\s*\{([^}]*)\}""")) { m -> m.groupValues[1].trim() }
        s = s.replace(Regex("""\\+fcolorbox\s*\{[^}]*\}\s*\{[^}]*\}\s*\{([^}]*)\}""")) { m -> m.groupValues[1].trim() }
        
        // 6. Square root: \sqrt{x} → √(x)
        s = s.replace(Regex("""\\+sqrt\s*\{([^}]*)\}""")) { m -> "√(${m.groupValues[1].trim()})" }
        s = s.replace(Regex("""\\+sqrt"""), "√")
        
        // 7. General brace commands: \cmd{content} -> content (e.g. \vec{x} -> x)
        // Repeat to handle nested commands like \overline{\vec{x}}
        var lastString = ""
        while (s != lastString) {
            lastString = s
            s = s.replace(Regex("""\\+[a-zA-Z]+\s*\{([^}]*)\}""")) { m -> m.groupValues[1] }
        }
        
        // 8. Superscripts and subscripts:
        s = s.replace(Regex("""\^\{([^}]*)\}""")) { m -> "^${m.groupValues[1]}" }
        s = s.replace(Regex("""_\{([^}]*)\}""")) { m -> "_${m.groupValues[1]}" }
        
        // 9. Strip backslashes from common standard math functions (e.g. \ln -> ln)
        s = s.replace(Regex("""\\+(sin|cos|tan|ln|log|sec|csc|cot|sinh|cosh|tanh)"""), "$1")
        
        // 10. Common symbols
        s = s.replace("\\times", "×")
            .replace("\\div", "÷")
            .replace("\\pm", "±")
            .replace("\\leq", "≤").replace("\\le", "≤")
            .replace("\\geq", "≥").replace("\\ge", "≥")
            .replace("\\neq", "≠").replace("\\ne", "≠")
            .replace("\\approx", "≈")
            .replace("\\infty", "∞")
            .replace("\\pi", "π")
            .replace("\\alpha", "α").replace("\\beta", "β")
            .replace("\\gamma", "γ").replace("\\delta", "δ")
            .replace("\\theta", "θ").replace("\\lambda", "λ")
            .replace("\\mu", "μ").replace("\\sigma", "σ")
            .replace("\\omega", "ω").replace("\\phi", "φ")
            .replace("\\cdot", "·").replace("\\ldots", "...")
            .replace("\\sum", "Σ").replace("\\int", "∫")
            .replace("\\in", "∈").replace("\\notin", "∉")
            .replace("\\subset", "⊂").replace("\\cup", "∪")
            .replace("\\cap", "∩").replace("\\rightarrow", "→")
            .replace("\\leftarrow", "←").replace("\\Rightarrow", "⇒")
            .replace("\\left", "").replace("\\right", "")
            
        // 11. Remove remaining curly braces used as grouping
        s = s.replace("{", "").replace("}", "")
        
        // 12. Remove block/inline math delimiters
        s = s.replace(Regex("""\$\$(.+?)\$\$""", RegexOption.DOT_MATCHES_ALL)) { m -> m.groupValues[1].trim() }
        s = s.replace(Regex("""\$(.+?)\$""", RegexOption.DOT_MATCHES_ALL)) { m -> m.groupValues[1].trim() }
        s = s.replace(Regex("""\\\[(.+?)\\\]""", RegexOption.DOT_MATCHES_ALL)) { m -> m.groupValues[1].trim() }
        s = s.replace(Regex("""\\\((.+?)\\\)""", RegexOption.DOT_MATCHES_ALL)) { m -> m.groupValues[1].trim() }
        
        // 13. Remove any remaining lone backslash commands
        s = s.replace(Regex("""\\[a-zA-Z]+"""), "")
        
        // 14. Clean up extra spaces
        s = s.replace(Regex("""[ \t]{2,}"""), " ").trim()
        
        return s
    }
}
