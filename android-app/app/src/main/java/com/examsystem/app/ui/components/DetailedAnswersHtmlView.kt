package com.examsystem.app.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.examsystem.app.data.models.Attempt
import com.examsystem.app.data.models.Question
import com.examsystem.app.data.models.Test

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DetailedAnswersHtmlView(attempt: Attempt, test: Test, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    val htmlData = remember(attempt, test) {
        val sb = StringBuilder()
        sb.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0">
                <link rel="stylesheet" href="https://local.katex/katex.min.css">
                <script defer src="https://local.katex/katex.min.js"></script>
                <script defer src="https://local.katex/contrib/auto-render.min.js"></script>
                <style>
                    body {
                        font-family: -apple-system, sans-serif;
                        color: #000;
                        padding: 4px;
                        background-color: transparent;
                        line-height: 1.5;
                        margin: 0;
                        overflow-y: auto;
                    }
                    .section-title {
                        font-weight: bold;
                        font-size: 18px;
                        margin-top: 16px;
                        margin-bottom: 12px;
                    }
                    .question-card {
                        background-color: #fff;
                        border-radius: 12px;
                        padding: 16px;
                        margin-bottom: 16px;
                        border: 1px solid #e0e0e0;
                    }
                    .correct-card { background-color: #E8F5E9; border-color: #A5D6A7; }
                    .incorrect-card { background-color: #FFEBEE; border-color: #FFCDD2; }
                    
                    .q-header {
                        display: flex;
                        justify-content: space-between;
                        font-size: 14px;
                        font-weight: bold;
                        margin-bottom: 12px;
                    }
                    .status-correct { color: #2E7D32; }
                    .status-incorrect { color: #C62828; }
                    
                    .q-text { font-size: 15px; margin-bottom: 16px; overflow-wrap: break-word; }
                    
                    .option-row {
                        display: flex;
                        align-items: center;
                        padding: 10px 12px;
                        border-radius: 8px;
                        margin-bottom: 8px;
                        font-size: 14px;
                    }
                    .opt-correct { background-color: #C8E6C9; color: #1B5E20; font-weight: bold; }
                    .opt-incorrect { background-color: #FFCDD2; color: #B71C1C; font-weight: bold; }
                    .opt-normal { background-color: transparent; color: #424242; }
                    
                    .opt-label { margin-right: 8px; font-weight: bold; }
                    .opt-content { flex: 1; overflow-x: auto; padding: 2px 0; }
                    .opt-badge { font-size: 12px; margin-left: 8px; white-space: nowrap; }
                    
                    .katex-display { margin: 0.3em 0; overflow-x: auto; overflow-y: hidden; }
                </style>
            </head>
            <body>
        """.trimIndent())

        test.sections.forEach { sec ->
            val secTitle = sec.title.ifEmpty { "Section" }
            sb.append("<div class='section-title'>").append(secTitle).append("</div>")
            
            sec.questions.forEachIndexed { qIdx, q ->
                val qId = q.id
                val qCorrectAnswer = resolveCorrectLetter(q, attempt)
                val studentAnswer = (attempt.answers[qId] ?: "").trim().uppercase().take(1)
                val isCorrect = qCorrectAnswer.isNotBlank() && studentAnswer == qCorrectAnswer
                
                val cardClass = if (isCorrect) "question-card correct-card" else "question-card incorrect-card"
                val statusText = if (isCorrect) "Correct (+${q.marks})" else "Incorrect (0/${q.marks})"
                val statusClass = if (isCorrect) "status-correct" else "status-incorrect"
                
                sb.append("<div class='").append(cardClass).append("'>")
                sb.append("<div class='q-header'>")
                sb.append("<span>Q").append(qIdx + 1).append(".</span>")
                sb.append("<span class='").append(statusClass).append("'>").append(statusText).append("</span>")
                sb.append("</div>")
                
                sb.append("<div class='q-text'>").append(cleanLatexForKaTeX(q.text)).append("</div>")
                
                val options = listOf("A" to q.optionA, "B" to q.optionB, "C" to q.optionC, "D" to q.optionD)
                options.forEach { (optLetter, optText) ->
                    if (optText.isNotBlank()) {
                        val isStudentChoice = studentAnswer.isNotBlank() &&
                            studentAnswer.equals(optLetter, ignoreCase = true)
                        val isCorrectAnswer = qCorrectAnswer.isNotBlank() &&
                            qCorrectAnswer.equals(optLetter, ignoreCase = true)
                        
                        val optClass = when {
                            isCorrectAnswer -> "opt-correct"
                            isStudentChoice -> "opt-incorrect"
                            else -> "opt-normal"
                        }
                        
                        sb.append("<div class='option-row ").append(optClass).append("'>")
                        sb.append("<span class='opt-label'>").append(optLetter).append(".</span>")
                        sb.append("<div class='opt-content'>").append(cleanLatexForKaTeX(optText)).append("</div>")
                        
                        if (isCorrectAnswer) {
                            sb.append("<span class='opt-badge status-correct'>✓ Correct</span>")
                        } else if (isStudentChoice) {
                            sb.append("<span class='opt-badge status-incorrect'>✗ Your Choice</span>")
                        }
                        
                        sb.append("</div>")
                    }
                }
                
                sb.append("</div>")
            }
        }

        sb.append("""
            <script>
                document.addEventListener("DOMContentLoaded", function() {
                    renderMathInElement(document.body, {
                        delimiters: [
                            {left: "$$", right: "$$", display: true},
                            {left: "$", right: "$", display: false},
                            {left: "\\(", right: "\\)", display: false},
                            {left: "\\[", right: "\\]", display: true}
                        ],
                        ignoredClasses: ["text-plain"],
                        throwOnError: false
                    });
                });
            </script>
            </body>
            </html>
        """.trimIndent())
        
        sb.toString()
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            object : WebView(ctx) {
                @SuppressLint("ClickableViewAccessibility")
                override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
                    requestDisallowInterceptTouchEvent(true)
                    return super.onTouchEvent(event)
                }
            }.apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null
                        if (url.startsWith("https://local.katex/")) {
                            try {
                                var assetPath = url.substring("https://local.katex/".length)
                                if (assetPath.contains("?")) assetPath = assetPath.substringBefore("?")
                                if (assetPath.contains("#")) assetPath = assetPath.substringBefore("#")
                                val inputStream = ctx.assets.open("katex/$assetPath")
                                val mimeType = when {
                                    url.endsWith(".css") -> "text/css"
                                    url.endsWith(".js") -> "application/javascript"
                                    url.endsWith(".woff2") -> "font/woff2"
                                    url.endsWith(".woff") -> "font/woff"
                                    url.endsWith(".ttf") -> "font/ttf"
                                    else -> "application/octet-stream"
                                }
                                return android.webkit.WebResourceResponse(mimeType, "UTF-8", inputStream)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }
            }
        },
        update = { view ->
            view.loadDataWithBaseURL("https://local.katex/", htmlData, "text/html", "UTF-8", null)
        },
        onRelease = { view ->
            view.destroy()
        }
    )
}

private fun resolveCorrectLetter(q: Question, attempt: Attempt): String {
    val fromQuestion = q.correctAnswer.trim().uppercase().take(1)
    if (fromQuestion in listOf("A", "B", "C", "D")) return fromQuestion
    val fromAttempt = attempt.correctAnswers[q.id]?.trim()?.uppercase()?.take(1) ?: ""
    return if (fromAttempt in listOf("A", "B", "C", "D")) fromAttempt else ""
}
