package com.codemaster.aistudio.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

// ─── Language detection ────────────────────────────────────────
fun detectLanguage(filename: String): String {
    return when (filename.substringAfterLast('.').lowercase()) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "py" -> "python"
        "js", "jsx" -> "javascript"
        "ts", "tsx" -> "typescript"
        "dart" -> "dart"
        "cpp", "cc", "cxx" -> "cpp"
        "c" -> "c"
        "cs" -> "csharp"
        "html" -> "html"
        "xml" -> "xml"
        "json" -> "json"
        "yaml", "yml" -> "yaml"
        "md" -> "markdown"
        "sh", "bash" -> "shell"
        "gradle" -> "groovy"
        "rb" -> "ruby"
        "go" -> "go"
        "rs" -> "rust"
        "swift" -> "swift"
        "php" -> "php"
        "css" -> "css"
        "scss" -> "scss"
        "sql" -> "sql"
        else -> "plaintext"
    }
}

// ─── Build Monaco HTML ─────────────────────────────────────────
private fun buildMonacoHtml(
    value: String,
    language: String,
    isDarkTheme: Boolean,
    fontSize: Int
): String {
    val monacoTheme = if (isDarkTheme) "vs-dark" else "vs"
    val bgColor = if (isDarkTheme) "#1e1e1e" else "#ffffff"
    val loadingTextColor = if (isDarkTheme) "#858585" else "#555555"
    // Escape for JS string - use JSON encoding for safety
    val safeValue = JSONObject.quote(value)

    return """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body { width: 100%; height: 100%; overflow: hidden; background: $bgColor; }
    #container { width: 100%; height: 100%; }
    #loading {
      position: fixed; inset: 0; z-index: 999;
      display: flex; flex-direction: column;
      align-items: center; justify-content: center;
      background: $bgColor; color: $loadingTextColor;
      font-family: monospace; font-size: 13px; gap: 12px;
    }
    .spinner {
      width: 28px; height: 28px; border-radius: 50%;
      border: 3px solid ${if (isDarkTheme) "#333" else "#ddd"};
      border-top-color: #58a6ff;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
  </style>
</head>
<body>
  <div id="loading"><div class="spinner"></div><span>Loading Monaco...</span></div>
  <div id="container"></div>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.44.0/min/vs/loader.min.js"></script>
  <script>
    var editor;

    require.config({
      paths: { vs: 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.44.0/min/vs' }
    });

    require(['vs/editor/editor.main'], function() {
      editor = monaco.editor.create(document.getElementById('container'), {
        value: $safeValue,
        language: '$language',
        theme: '$monacoTheme',
        fontSize: $fontSize,
        lineNumbers: 'on',
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        automaticLayout: true,
        wordWrap: 'on',
        tabSize: 2,
        insertSpaces: true,
        folding: true,
        renderLineHighlight: 'all',
        cursorBlinking: 'smooth',
        smoothScrolling: true,
        contextmenu: false,
        quickSuggestions: true,
        suggestOnTriggerCharacters: true,
        acceptSuggestionOnEnter: 'on',
        padding: { top: 8, bottom: 8 },
        scrollbar: { verticalScrollbarSize: 6, horizontalScrollbarSize: 6 },
      });

      document.getElementById('loading').style.display = 'none';
      Android.onReady();

      // Debounced onChange
      var debounceTimer;
      editor.onDidChangeModelContent(function() {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(function() {
          Android.onChange(editor.getValue());
        }, 300);
      });

      // Cursor position
      editor.onDidChangeCursorPosition(function(e) {
        Android.onCursor(e.position.lineNumber, e.position.column);
      });
    });

    // Commands from Kotlin
    function handleCommand(json) {
      try {
        var msg = JSON.parse(json);
        if (!editor) return;
        switch(msg.cmd) {
          case 'setValue':
            editor.setValue(msg.value || '');
            break;
          case 'getValue':
            Android.onGetValue(msg.requestId, editor.getValue());
            break;
          case 'insertAtCursor':
            editor.executeEdits('kt-insert', [{
              range: editor.getSelection(),
              text: msg.text || '',
              forceMoveMarkers: true
            }]);
            editor.focus();
            break;
          case 'setLanguage':
            monaco.editor.setModelLanguage(editor.getModel(), msg.language);
            break;
          case 'setTheme':
            monaco.editor.setTheme(msg.theme);
            break;
          case 'setFontSize':
            editor.updateOptions({ fontSize: msg.fontSize });
            break;
          case 'format':
            editor.getAction('editor.action.formatDocument').run();
            break;
          case 'undo':
            editor.trigger('keyboard', 'undo', null);
            break;
          case 'redo':
            editor.trigger('keyboard', 'redo', null);
            break;
          case 'find':
            editor.getAction('actions.find').run();
            break;
          case 'gotoLine':
            editor.revealLineInCenter(msg.line);
            editor.setPosition({ lineNumber: msg.line, column: 1 });
            break;
        }
      } catch(e) {}
    }
  </script>
</body>
</html>"""
}

// ─── MonacoEditorState ─────────────────────────────────────────
// Hold onto the WebView instance so we can call commands imperatively
class MonacoEditorState {
    internal var webView: WebView? = null
    private val requestCounter = AtomicInteger(0)
    private val pendingRequests = ConcurrentHashMap<String, (String) -> Unit>()

    fun setValue(value: String) {
        val json = JSONObject().apply {
            put("cmd", "setValue")
            put("value", value)
        }.toString()
        runJs("handleCommand(${JSONObject.quote(json)})")
    }

    suspend fun getValue(): String {
        val requestId = "req_${requestCounter.incrementAndGet()}"
        return withTimeoutOrNull(3000) {
            suspendCancellableCoroutine { cont ->
                pendingRequests[requestId] = { value ->
                    pendingRequests.remove(requestId)
                    cont.resume(value)
                }
                val json = JSONObject().apply {
                    put("cmd", "getValue")
                    put("requestId", requestId)
                }.toString()
                runJs("handleCommand(${JSONObject.quote(json)})")
            }
        } ?: ""
    }

    fun insertAtCursor(text: String) {
        val json = JSONObject().apply {
            put("cmd", "insertAtCursor")
            put("text", text)
        }.toString()
        runJs("handleCommand(${JSONObject.quote(json)})")
    }

    fun setLanguage(language: String) {
        val json = JSONObject().apply {
            put("cmd", "setLanguage")
            put("language", language)
        }.toString()
        runJs("handleCommand(${JSONObject.quote(json)})")
    }

    fun setTheme(isDark: Boolean) {
        val json = JSONObject().apply {
            put("cmd", "setTheme")
            put("theme", if (isDark) "vs-dark" else "vs")
        }.toString()
        runJs("handleCommand(${JSONObject.quote(json)})")
    }

    fun setFontSize(size: Int) {
        val json = JSONObject().apply {
            put("cmd", "setFontSize")
            put("fontSize", size)
        }.toString()
        runJs("handleCommand(${JSONObject.quote(json)})")
    }

    fun format() = runJs("handleCommand('{\"cmd\":\"format\"}')")
    fun undo() = runJs("handleCommand('{\"cmd\":\"undo\"}')")
    fun redo() = runJs("handleCommand('{\"cmd\":\"redo\"}')")
    fun find() = runJs("handleCommand('{\"cmd\":\"find\"}')")

    fun gotoLine(line: Int) {
        val json = JSONObject().apply {
            put("cmd", "gotoLine")
            put("line", line)
        }.toString()
        runJs("handleCommand(${JSONObject.quote(json)})")
    }

    internal fun resolveGetValue(requestId: String, value: String) {
        pendingRequests[requestId]?.invoke(value)
    }

    private fun runJs(script: String) {
        webView?.post {
            webView?.evaluateJavascript(script, null)
        }
    }
}

@Composable
fun rememberMonacoEditorState(): MonacoEditorState {
    return remember { MonacoEditorState() }
}

// ─── MonacoEditorView Composable ───────────────────────────────
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MonacoEditorView(
    value: String,
    language: String = "plaintext",
    isDarkTheme: Boolean = true,
    fontSize: Int = 14,
    onChange: (String) -> Unit = {},
    onCursorChange: (line: Int, col: Int) -> Unit = { _, _ -> },
    state: MonacoEditorState = rememberMonacoEditorState(),
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    // Track prop changes to push updates without full reload
    val prevTheme = remember { mutableStateOf(isDarkTheme) }
    val prevFontSize = remember { mutableStateOf(fontSize) }

    LaunchedEffect(isDarkTheme) {
        if (prevTheme.value != isDarkTheme) {
            prevTheme.value = isDarkTheme
            if (!isLoading) state.setTheme(isDarkTheme)
        }
    }

    LaunchedEffect(fontSize) {
        if (prevFontSize.value != fontSize) {
            prevFontSize.value = fontSize
            if (!isLoading) state.setFontSize(fontSize)
        }
    }

    val html = remember(language, isDarkTheme, fontSize) {
        buildMonacoHtml(value, language, isDarkTheme, fontSize)
    }

    Box(modifier = modifier) {
        if (hasError) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("⚠️", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Editor failed to load",
                    color = Color(0xFFF85149),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Monaco loads from CDN\nCheck internet connection",
                    color = Color(0xFF8B949E),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        }

                        // JavaScript interface — called from Monaco JS
                        addJavascriptInterface(
                            object : Any() {
                                @android.webkit.JavascriptInterface
                                fun onReady() {
                                    (context as? android.app.Activity)?.runOnUiThread {
                                        isLoading = false
                                    }
                                }

                                @android.webkit.JavascriptInterface
                                fun onChange(newValue: String) {
                                    (context as? android.app.Activity)?.runOnUiThread {
                                        onChange(newValue)
                                    }
                                }

                                @android.webkit.JavascriptInterface
                                fun onCursor(line: Int, col: Int) {
                                    (context as? android.app.Activity)?.runOnUiThread {
                                        onCursorChange(line, col)
                                    }
                                }

                                @android.webkit.JavascriptInterface
                                fun onGetValue(requestId: String, value: String) {
                                    state.resolveGetValue(requestId, value)
                                }
                            },
                            "Android"
                        )

                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                hasError = true
                            }
                        }

                        webChromeClient = WebChromeClient()
                        state.webView = this
                        loadDataWithBaseURL(
                            "https://cdnjs.cloudflare.com",
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                update = { _ ->
                    // No full reload on recompose — use imperative state methods instead
                }
            )
        }

        // Loading overlay
        if (isLoading && !hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDarkTheme) Color(0xFF1E1E1E) else Color.White),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF58A6FF),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        "Loading Monaco Editor...",
                        color = Color(0xFF858585),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}
