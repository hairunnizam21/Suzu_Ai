package io.suzuai.app.ui.chat

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.suzuai.app.ui.theme.SuzuColors

/**
 * Heuristic — does this code block render to something we can preview in a
 * WebView? We accept HTML directly, plus standalone CSS/JS which we wrap in
 * an inline document.
 */
fun isPreviewable(language: String?): Boolean {
    val lang = language?.lowercase()?.trim()
    return lang in setOf("html", "htm", "xhtml", "svg", "css", "js", "javascript", "ts", "typescript")
}

/** Build a self-contained HTML document for arbitrary CSS/JS snippets. */
fun buildPreviewHtml(language: String?, code: String): String {
    val lang = language?.lowercase()?.trim()
    return when (lang) {
        "html", "htm", "xhtml" -> code
        "svg" -> """<!doctype html><meta charset="utf-8"><style>body{margin:0;background:#000;display:flex;align-items:center;justify-content:center;height:100vh}</style>$code"""
        "css" -> """<!doctype html>
<html><head><meta charset="utf-8">
<style>body{font-family:sans-serif;margin:24px;color:#eee;background:#0a0a0a}</style>
<style>${code.escapeForInline()}</style>
</head><body>
<h1>Heading</h1>
<p>Paragraph for preview.</p>
<button>Button</button>
<a href="#">Link</a>
<input placeholder="input" />
<div class="card">Element with class <code>.card</code>.</div>
</body></html>"""
        "js", "javascript", "ts", "typescript" -> """<!doctype html>
<html><head><meta charset="utf-8"><style>body{font-family:monospace;margin:16px;color:#eee;background:#0a0a0a}#log{white-space:pre-wrap}</style></head>
<body><div id="log"></div>
<script>
(function(){
  const out = document.getElementById('log');
  function fmt(a){try{return typeof a==='object'?JSON.stringify(a,null,2):String(a)}catch(e){return String(a)}}
  ['log','info','warn','error'].forEach(k=>{const o=console[k];console[k]=function(){const line=Array.from(arguments).map(fmt).join(' ');out.textContent+='['+k+'] '+line+'\n';o&&o.apply(console,arguments)}});
  window.addEventListener('error',e=>{out.textContent+='[uncaught] '+e.message+'\n'});
})();
try{
${code.escapeForInline()}
}catch(e){document.getElementById('log').textContent+='[throw] '+(e&&e.message||e)+'\n'}
</script></body></html>"""
        else -> code
    }
}

private fun String.escapeForInline(): String =
    replace("</script", "<\\/script").replace("</style", "<\\/style")

/**
 * Full-screen preview dialog with a WebView. JS is enabled inside an isolated
 * sandbox (no network access by default — content is loaded via
 * `loadDataWithBaseURL(null, ...)`).
 */
@Composable
fun PreviewDialog(
    title: String,
    html: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SuzuColors.Background),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SuzuColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Preview · $title",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = SuzuColors.AccentCyan,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = SuzuColors.OnSurface)
                }
            }
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(SuzuColors.Background),
                factory = { ctx ->
                    WebView(ctx).apply {
                        @Suppress("SetJavaScriptEnabled")
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        webViewClient = WebViewClient()
                    }
                },
                update = { wv ->
                    wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                },
            )
        }
    }
}
