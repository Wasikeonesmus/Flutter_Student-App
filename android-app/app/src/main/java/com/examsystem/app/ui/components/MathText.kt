package com.examsystem.app.ui.components

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.examsystem.app.util.LatexRender
import org.json.JSONObject

/** Legacy name — returns HTML with plain text separated from math spans. */
fun cleanLatexForKaTeX(raw: String): String = LatexRender.prepareHtmlForKaTeX(raw)

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    textSizeSp: Int = 16
) {
    val context = LocalContext.current
    val contentColor = LocalContentColor.current
    val colorHex = remember(contentColor) {
        val argb = contentColor.toArgb()
        String.format("#%06X", 0xFFFFFF and argb)
    }

    val htmlContent = remember(text) { LatexRender.prepareHtmlForKaTeX(text) }
    val contentKey = "$htmlContent|$colorHex|$textSizeSp"
    val density = context.resources.displayMetrics.density

    // key(contentKey) forces Compose to reconstruct the WebView and its JS interface
    // every time the equation text changes, preventing state-closure bugs.
    key(contentKey) {
        val webViewHeightState = remember { mutableStateOf(40) } // Safe initial height of 40dp

        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .height(webViewHeightState.value.dp),
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
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    
                    settings.apply {
                        javaScriptEnabled = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        domStorageEnabled = true
                        databaseEnabled = true
                        setSupportZoom(false)
                        displayZoomControls = false
                        useWideViewPort = false
                        loadWithOverviewMode = true
                        allowFileAccess = true
                        allowContentAccess = true
                        allowFileAccessFromFileURLs = true
                        allowUniversalAccessFromFileURLs = true
                    }
                    
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false

                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): android.webkit.WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null
                            if (url.startsWith("https://local.katex/")) {
                                try {
                                    var assetPath = url.substring("https://local.katex/".length)
                                    if (assetPath.contains("?")) {
                                        assetPath = assetPath.substringBefore("?")
                                    }
                                    if (assetPath.contains("#")) {
                                        assetPath = assetPath.substringBefore("#")
                                    }
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

                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun onRenderComplete(height: Float) {
                            val heightDp = Math.ceil(height / density.toDouble()).toInt() + 20 // 20dp safety buffer to prevent clipping
                            post {
                                if (heightDp > webViewHeightState.value || webViewHeightState.value == 40) {
                                    webViewHeightState.value = heightDp
                                }
                            }
                        }
                    }, "AndroidBridge")
                }
            },
            update = { view ->
                val jsonEscapedText = JSONObject.quote(htmlContent)

                val htmlData = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0">
                        <link rel="stylesheet" href="https://local.katex/katex.min.css">
                        <script defer src="https://local.katex/katex.min.js"></script>
                        <script defer src="https://local.katex/contrib/auto-render.min.js"></script>
                        <style>
                            html, body {
                                margin: 0; padding: 0;
                                width: 100%;
                                background-color: transparent;
                                overflow: visible;
                            }
                            #mathContent {
                                margin: 0;
                                padding: 12px 4px; /* Added more vertical padding to fix clipping of tall fractions */
                                font-size: ${textSizeSp}px;
                                font-family: -apple-system, sans-serif;
                                color: $colorHex;
                                line-height: 1.5;
                                overflow: visible;
                                overflow-wrap: break-word;
                                word-wrap: break-word;
                                -webkit-user-select: none;
                            }
                            .katex-display { 
                                margin: 0.3em 0; 
                                overflow-x: auto; 
                                overflow-y: hidden;
                            }
                            /* Prevent sub-pixel rounding clipping of radical & fraction lines on Android WebViews */
                            .katex .sqrt > span.vlist > span > span {
                                border-top-width: 0.08em !important;
                            }
                            .katex .frac-line {
                                border-bottom-width: 0.08em !important;
                            }
                            .katex svg {
                                min-width: 1px !important;
                                min-height: 1px !important;
                            }
                            .text-plain {
                                font-family: -apple-system, sans-serif;
                                white-space: pre-wrap;
                            }
                        </style>
                    </head>
                    <body>
                        <div id="mathContent"></div>
                        <script>
                            function sendHeight() {
                                if (window.AndroidBridge) {
                                    var container = document.getElementById('mathContent');
                                    var height = container.offsetHeight || container.scrollHeight || document.body.scrollHeight;
                                    window.AndroidBridge.onRenderComplete(height);
                                }
                            }

                            function render() {
                                var container = document.getElementById('mathContent');
                                container.innerHTML = $jsonEscapedText;
                                if (window.renderMathInElement) {
                                    renderMathInElement(container, {
                                        delimiters: [
                                            {left: "$$", right: "$$", display: true},
                                            {left: "$", right: "$", display: false},
                                            {left: "\\(", right: "\\)", display: false},
                                            {left: "\\[", right: "\\]", display: true}
                                        ],
                                        ignoredClasses: ["text-plain"],
                                        throwOnError: false,
                                        minRuleThickness: 0.08
                                    });
                                }
                                
                                sendHeight();
                                
                                if (document.fonts && document.fonts.ready) {
                                    document.fonts.ready.then(function() {
                                        sendHeight();
                                    });
                                }
                                
                                setTimeout(sendHeight, 30);
                                setTimeout(sendHeight, 100);
                                setTimeout(sendHeight, 300);
                            }
                            window.onload = render;
                        </script>
                    </body>
                    </html>
                """.trimIndent()

                view.loadDataWithBaseURL("https://local.katex/", htmlData, "text/html", "UTF-8", null)
            }
        )
    }
}

@Composable
fun SmartMathText(
    text: String,
    modifier: Modifier = Modifier,
    textSizeSp: Int = 16
) {
    val cleanText = text.trim()
    if (!LatexRender.hasRenderableMath(cleanText)) {
        Text(
            text = cleanText,
            modifier = modifier,
            fontSize = textSizeSp.sp,
            color = LocalContentColor.current,
            lineHeight = (textSizeSp * 1.5).sp
        )
        return
    }

    MathText(text = cleanText, modifier = modifier, textSizeSp = textSizeSp)
}

/**
 * Input that shows **rendered equations inside the field** when not editing.
 * Tap the field → type LaTeX → tap ✓ or leave the field to see the rendered preview again.
 */
@Composable
fun EditableMathField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    placeholder: String = "Tap to type. Put only formulas in \$...\$"
) {
    var editing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    if (editing) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { if (!it.isFocused) editing = false },
            minLines = minLines,
            trailingIcon = {
                IconButton(onClick = { editing = false }) {
                    Icon(Icons.Default.Check, contentDescription = "Done preview")
                }
            }
        )
        LaunchedEffect(editing) {
            if (editing) focusRequester.requestFocus()
        }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clickable { editing = true },
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SmartMathText(
                        text = value,
                        textSizeSp = 16,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 36.dp, max = 320.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tap to edit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Renders multiple labeled content blocks (e.g. question + 4 options) inside a SINGLE WebView.
 *
 * WHY: Stacking 5 separate MathText/SmartMathText WebViews inside a LazyColumn causes each
 * WebView to independently measure its own height via async JS callbacks. This creates a race
 * where earlier WebViews haven't expanded yet when later ones finish, causing equations to
 * overflow and bleed into adjacent rows ("clutter"). Using one WebView for all blocks
 * eliminates this entirely — one render pass, one correct height.
 *
 * @param blocks List of (label, content) pairs, e.g. listOf("Q:" to "x²+y²", "A:" to "...")
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun MultiBlockMathText(
    blocks: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    textSizeSp: Int = 15
) {
    val context = LocalContext.current
    val contentColor = LocalContentColor.current
    val colorHex = remember(contentColor) {
        val argb = contentColor.toArgb()
        String.format("#%06X", 0xFFFFFF and argb)
    }

    val cleanedBlocks = remember(blocks) {
        blocks.map { (label, text) -> label to LatexRender.prepareHtmlForKaTeX(text.trim()) }
    }

    val density = context.resources.displayMetrics.density
    val minHeightDp = (blocks.size * 52).coerceAtLeast(100)
    val webViewHeightState = remember(blocks.size) { mutableStateOf(minHeightDp) }

    // Build JSON string of blocks
    val jsonArrayStr = remember(cleanedBlocks) {
        val arr = org.json.JSONArray()
        cleanedBlocks.forEach { (label, text) ->
            arr.put(org.json.JSONObject().apply {
                put("label", label)
                put("text", text)
            })
        }
        arr.toString()
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeightDp.dp)
            .height(maxOf(webViewHeightState.value, minHeightDp).dp),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportZoom(false)
                    displayZoomControls = false
                    allowFileAccess = true
                    allowContentAccess = true
                }

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
                                    url.endsWith(".js")  -> "application/javascript"
                                    url.endsWith(".woff2") -> "font/woff2"
                                    url.endsWith(".woff") -> "font/woff"
                                    url.endsWith(".ttf")  -> "font/ttf"
                                    else -> "application/octet-stream"
                                }
                                return android.webkit.WebResourceResponse(mimeType, "UTF-8", inputStream)
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Trigger initial render once HTML wrapper loads
                        evaluateJavascript("updateBlocks($jsonArrayStr);", null)
                    }
                }

                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onRenderComplete(height: Float) {
                        // Add 28dp buffer so tall fractions/radicals are never clipped
                        val heightDp = Math.ceil(height / density.toDouble()).toInt() + 28
                        post {
                            // Allow shrinking and growing
                            if (Math.abs(webViewHeightState.value - heightDp) > 2) {
                                webViewHeightState.value = heightDp
                            }
                        }
                    }
                }, "AndroidBridge")

                // Initial HTML wrapper setup (only loaded once)
                val htmlData = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0">
                        <link rel="stylesheet" href="https://local.katex/katex.min.css">
                        <script defer src="https://local.katex/katex.min.js"></script>
                        <script defer src="https://local.katex/contrib/auto-render.min.js"></script>
                        <style>
                            html, body { margin: 0; padding: 0; width: 100%; background-color: transparent; overflow: hidden; }
                            #content {
                                padding: 8px 4px 16px 4px;
                                font-size: ${textSizeSp}px;
                                font-family: -apple-system, sans-serif;
                                color: $colorHex;
                                line-height: 1.7;
                                overflow: visible;
                                overflow-wrap: break-word;
                                word-wrap: break-word;
                                -webkit-user-select: none;
                            }
                            .block-row {
                                display: flex;
                                align-items: flex-start;
                                margin-bottom: 8px;
                            }
                            .block-label {
                                font-weight: bold;
                                min-width: 2.2em;
                                flex-shrink: 0;
                                margin-right: 6px;
                                padding-top: 2px;
                                color: #888;
                                font-size: ${textSizeSp - 1}px;
                            }
                            .block-text { flex: 1; overflow: visible; }
                            .katex-display { margin: 0.3em 0; overflow-x: auto; overflow-y: visible; }
                            .katex .sqrt > span.vlist > span > span { border-top-width: 0.08em !important; }
                            .katex .frac-line { border-bottom-width: 0.08em !important; }
                            .katex svg { min-width: 1px !important; min-height: 1px !important; }
                        </style>
                    </head>
                    <body>
                        <div id="content"></div>
                        <script>
                            function sendHeight() {
                                if (window.AndroidBridge) {
                                    var h = Math.max(
                                        document.body.scrollHeight, document.documentElement.scrollHeight,
                                        document.body.offsetHeight, document.documentElement.offsetHeight,
                                        document.getElementById('content').scrollHeight
                                    );
                                    window.AndroidBridge.onRenderComplete(h);
                                }
                            }

                            window.updateBlocks = function(blocks) {
                                var container = document.getElementById('content');
                                container.innerHTML = ''; // Clear previous

                                blocks.forEach(function(block) {
                                    var row = document.createElement('div');
                                    row.className = 'block-row';

                                    var labelEl = document.createElement('span');
                                    labelEl.className = 'block-label';
                                    labelEl.textContent = block.label;

                                    var textEl = document.createElement('span');
                                    textEl.className = 'block-text';
                                    textEl.innerHTML = block.text;

                                    row.appendChild(labelEl);
                                    row.appendChild(textEl);
                                    container.appendChild(row);
                                });

                                if (window.renderMathInElement) {
                                    renderMathInElement(container, {
                                        delimiters: [
                                            {left: "$$", right: "$$", display: true},
                                            {left: "$",  right: "$",  display: false},
                                            {left: "\\(", right: "\\)", display: false},
                                            {left: "\\[", right: "\\]", display: true}
                                        ],
                                        ignoredClasses: ["text-plain"],
                                        throwOnError: false,
                                        minRuleThickness: 0.08
                                    });
                                }

                                sendHeight();
                                setTimeout(sendHeight, 100);
                                setTimeout(sendHeight, 400);
                            };
                        </script>
                    </body>
                    </html>
                """.trimIndent()

                loadDataWithBaseURL("https://local.katex/", htmlData, "text/html", "UTF-8", null)
            }
        },
        update = { view ->
            // Instead of reloading the page, inject the new JSON data and trigger the update function
            view.evaluateJavascript("if(window.updateBlocks) { updateBlocks($jsonArrayStr); }", null)
        }
    )
}
