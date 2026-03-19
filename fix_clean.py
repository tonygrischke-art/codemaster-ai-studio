import re

# 1. Fix NavGraph - show line 41 first
with open("app/src/main/java/com/codemaster/aistudio/ui/navigation/NavGraph.kt") as f:
    lines = f.readlines()
print("NavGraph line 41:", repr(lines[40]))

# Remove any line containing 'createRoute' that also has 'Preview'
fixed = []
for line in lines:
    if 'Preview' in line and 'createRoute' in line:
        # Keep only the simple Screen object definition
        fixed.append('    object Preview  : Screen("preview/{projectId}")\n')
    else:
        fixed.append(line)
with open("app/src/main/java/com/codemaster/aistudio/ui/navigation/NavGraph.kt", 'w') as f:
    f.writelines(fixed)
print("Fixed NavGraph.kt")

# 2. Delete PreviewScreen and rewrite with correct Kotlin triple-quoted strings
preview = '''package com.codemaster.aistudio.ui.screens.preview

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                        Icon(Icons.Default.Edit, "Raw", tint = if (mode == PreviewMode.RAW) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun MarkdownPreview(markdown: String) {
    val html = markdownToHtml(markdown)
    val dark = "#1a1a2e"
    val styledHtml = "<html><head><meta name=\\"viewport\\" content=\\"width=device-width, initial-scale=1\\"><style>body{font-family:sans-serif;padding:16px;line-height:1.6;color:#e0e0e0;background:$dark}h1,h2,h3{color:#bb86fc}code{background:#2d2d2d;padding:2px 6px;border-radius:4px;color:#03dac6}pre{background:#0d1117;padding:16px;border-radius:8px}a{color:#03dac6}</style></head><body>$html</body></html>"
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
    // Use triple-quoted strings to avoid escape issues
    html = html.replace(Regex("""^###### (.+)$""", RegexOption.MULTILINE)) { "<h6>${it.groupValues[1]}</h6>" }
    html = html.replace(Regex("""^##### (.+)$""", RegexOption.MULTILINE)) { "<h5>${it.groupValues[1]}</h5>" }
    html = html.replace(Regex("""^#### (.+)$""", RegexOption.MULTILINE)) { "<h4>${it.groupValues[1]}</h4>" }
    html = html.replace(Regex("""^### (.+)$""", RegexOption.MULTILINE)) { "<h3>${it.groupValues[1]}</h3>" }
    html = html.replace(Regex("""^## (.+)$""", RegexOption.MULTILINE)) { "<h2>${it.groupValues[1]}</h2>" }
    html = html.replace(Regex("""^# (.+)$""", RegexOption.MULTILINE)) { "<h1>${it.groupValues[1]}</h1>" }
    html = html.replace(Regex("""```[\\w]*\\n([\\s\\S]*?)```""")) { "<pre><code>${it.groupValues[1].trim()}</code></pre>" }
    html = html.replace(Regex("""`([^`]+)`""")) { "<code>${it.groupValues[1]}</code>" }
    html = html.replace(Regex("""\\*\\*(.+?)\\*\\*""")) { "<strong>${it.groupValues[1]}</strong>" }
    html = html.replace(Regex("""\\*(.+?)\\*""")) { "<em>${it.groupValues[1]}</em>" }
    html = html.replace(Regex("""\\[(.+?)\\]\\((.+?)\\)""")) { "<a href='${it.groupValues[2]}'>${it.groupValues[1]}</a>" }
    html = html.replace(Regex("""^> (.+)$""", RegexOption.MULTILINE)) { "<blockquote>${it.groupValues[1]}</blockquote>" }
    html = html.replace(Regex("""^- (.+)$""", RegexOption.MULTILINE)) { "<li>${it.groupValues[1]}</li>" }
    html = html.replace("\\n\\n", "</p><p>")
    return "<p>$html</p>"
}

@Composable
fun CodePreview(content: String, extension: String) {
    val escaped = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val styledHtml = """<html><head><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css"><script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script><style>body{margin:0;padding:8px;background:#0d1117}pre{margin:0}code{font-size:13px;line-height:1.5}.hljs{padding:16px;border-radius:8px}</style></head><body><pre><code class="language-$extension">$escaped</code></pre><script>hljs.highlightAll();</script></body></html>"""
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
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        Text(
            text = content,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color(0xFFADBDD0),
            lineHeight = 18.sp,
            modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(scrollState)
        )
    }
}
'''

with open("app/src/main/java/com/codemaster/aistudio/ui/screens/preview/PreviewScreen.kt", 'w') as f:
    f.write(preview)
print("Fixed PreviewScreen.kt")
