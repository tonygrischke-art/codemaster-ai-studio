package com.codemaster.aistudio.ui.screens.preview

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

enum class PreviewMode { HTML, MARKDOWN, CODE, RAW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    fileName: String,
    content: String,
    onBack: () -> Unit
) {
    val extension = fileName.substringAfterLast(".", "").lowercase()
    var mode by remember {
        mutableStateOf(
            when (extension) {
                "html", "htm" -> PreviewMode.HTML
                "md", "markdown" -> PreviewMode.MARKDOWN
                else -> PreviewMode.CODE
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Preview", fontWeight = FontWeight.Bold)
                        Text(fileName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    // Mode switcher
                    if (extension in listOf("html", "htm")) {
                        IconButton(onClick = { mode = PreviewMode.HTML }) {
                            Icon(Icons.Default.Language, "HTML", tint = if (mode == PreviewMode.HTML) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (extension in listOf("md", "markdown")) {
                        IconButton(onClick = { mode = PreviewMode.MARKDOWN }) {
                            Icon(Icons.Default.TextFields, "Markdown", tint = if (mode == PreviewMode.MARKDOWN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { mode = PreviewMode.CODE }) {
                        Icon(Icons.Default.Code, "Code", tint = if (mode == PreviewMode.CODE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { mode = PreviewMode.RAW }) {
                        Icon(Icons.Default.RawOn, "Raw", tint = if (mode == PreviewMode.RAW) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (mode) {
                PreviewMode.HTML -> HtmlPreview(content)
                PreviewMode.MARKDOWN -> MarkdownPreview(content)
                PreviewMode.CODE -> CodePreview(content, extension)
                PreviewMode.RAW -> RawPreview(content)
            }
        }
    }
}

@Composable
fun HtmlPreview(html: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun MarkdownPreview(markdown: String) {
    // Convert markdown to HTML and render
    val html = markdownToHtml(markdown)
    val styledHtml = """
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            body { font-family: -apple-system, sans-serif; padding: 16px; line-height: 1.6; color: #e0e0e0; background: #1a1a2e; }
            h1,h2,h3 { color: #bb86fc; border-bottom: 1px solid #333; padding-bottom: 8px; }
            code { background: #2d2d2d; padding: 2px 6px; border-radius: 4px; font-family: monospace; color: #03dac6; }
            pre { background: #0d1117; padding: 16px; border-radius: 8px; overflow-x: auto; border: 1px solid #333; }
            pre code { background: none; padding: 0; }
            blockquote { border-left: 4px solid #bb86fc; margin: 0; padding-left: 16px; color: #888; }
            a { color: #03dac6; }
            table { border-collapse: collapse; width: 100%; }
            th, td { border: 1px solid #333; padding: 8px; }
            th { background: #2d2d2d; }
            img { max-width: 100%; }
            hr { border-color: #333; }
        </style>
        </head>
        <body>$html</body>
        </html>
    """.trimIndent()
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = false
                loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

fun markdownToHtml(md: String): String {
    var html = md
    // Headers
    html = html.replace(Regex("^#{6} (.+)$", RegexOption.MULTILINE)) { "<h6>${it.groupValues[1]}</h6>" }
    html = html.replace(Regex("^#{5} (.+)$", RegexOption.MULTILINE)) { "<h5>${it.groupValues[1]}</h5>" }
    html = html.replace(Regex("^#{4} (.+)$", RegexOption.MULTILINE)) { "<h4>${it.groupValues[1]}</h4>" }
    html = html.replace(Regex("^#{3} (.+)$", RegexOption.MULTILINE)) { "<h3>${it.groupValues[1]}</h3>" }
    html = html.replace(Regex("^#{2} (.+)$", RegexOption.MULTILINE)) { "<h2>${it.groupValues[1]}</h2>" }
    html = html.replace(Regex("^# (.+)$", RegexOption.MULTILINE)) { "<h1>${it.groupValues[1]}</h1>" }
    // Code blocks
    html = html.replace(Regex("```[\w]*\n([\s\S]*?)```")) { "<pre><code>${it.groupValues[1].trim()}</code></pre>" }
    // Inline code
    html = html.replace(Regex("`([^`]+)`")) { "<code>${it.groupValues[1]}</code>" }
    // Bold
    html = html.replace(Regex("\*\*(.+?)\*\*")) { "<strong>${it.groupValues[1]}</strong>" }
    html = html.replace(Regex("__(.+?)__")) { "<strong>${it.groupValues[1]}</strong>" }
    // Italic
    html = html.replace(Regex("\*(.+?)\*")) { "<em>${it.groupValues[1]}</em>" }
    // Links
    html = html.replace(Regex("\[(.+?)\]\((.+?)\)")) { "<a href='${it.groupValues[2]}'>${it.groupValues[1]}</a>" }
    // Blockquote
    html = html.replace(Regex("^> (.+)$", RegexOption.MULTILINE)) { "<blockquote>${it.groupValues[1]}</blockquote>" }
    // HR
    html = html.replace(Regex("^---$", RegexOption.MULTILINE), "<hr>")
    // Lists
    html = html.replace(Regex("^- (.+)$", RegexOption.MULTILINE)) { "<li>${it.groupValues[1]}</li>" }
    html = html.replace(Regex("^\* (.+)$", RegexOption.MULTILINE)) { "<li>${it.groupValues[1]}</li>" }
    // Paragraphs
    html = html.replace(Regex("\n\n")) { "</p><p>" }
    return "<p>$html</p>"
}

@Composable
fun CodePreview(content: String, extension: String) {
    val styledHtml = """
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css">
        <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
        <style>
            body { margin: 0; padding: 8px; background: #0d1117; }
            pre { margin: 0; }
            code { font-size: 13px; line-height: 1.5; }
            .hljs { padding: 16px; border-radius: 8px; }
        </style>
        </head>
        <body>
        <pre><code class="language-$extension">${content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</code></pre>
        <script>hljs.highlightAll();</script>
        </body>
        </html>
    """.trimIndent()
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                loadDataWithBaseURL("https://cdnjs.cloudflare.com", styledHtml, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun RawPreview(content: String) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))
    ) {
        Text(
            text = content,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color(0xFFADBDD0),
            lineHeight = 18.sp,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(scrollState)
        )
    }
}
