package io.github.machtvapps.machtv

import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

fun addonInstallUrl(link: String): String? = if (link.startsWith("stremio://")) "https://" + link.removePrefix("stremio://") else null

private const val REMOTE_FIELDS_SCRIPT = """
(function () {
  var editing = null;
  function typed(element) {
    return element instanceof HTMLInputElement && ['checkbox', 'radio', 'button', 'submit', 'range'].indexOf(element.type) < 0;
  }
  function quiet() {
    Array.prototype.forEach.call(document.querySelectorAll('input'), function (element) {
      if (typed(element) && element !== editing) element.readOnly = true;
    });
  }
  quiet();
  new MutationObserver(quiet).observe(document.documentElement, { childList: true, subtree: true });
  document.addEventListener('focusout', function (event) {
    if (event.target === editing) {
      editing = null;
      event.target.readOnly = true;
    }
  }, true);
  document.addEventListener('keydown', function (event) {
    var field = event.target;
    if (!typed(field)) return;
    if (event.key === 'Enter' && field.readOnly) {
      event.preventDefault();
      field.blur();
      editing = field;
      field.readOnly = false;
      field.focus();
      setTimeout(function () { remoteKeyboard.show(); }, 100);
      return;
    }
    if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return;
    var stops = Array.prototype.filter.call(
      document.querySelectorAll('a[href],button,input,select,textarea,[tabindex]'),
      function (element) { return !element.disabled && element.tabIndex >= 0 && element.getClientRects().length > 0; }
    );
    var next = stops[stops.indexOf(field) + (event.key === 'ArrowDown' ? 1 : -1)];
    if (next) {
      event.preventDefault();
      next.focus();
    }
  }, true);
})();
"""

@Composable
fun AddonConfigure(url: String, onInstall: (String) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    val keyboard = context.getSystemService(InputMethodManager::class.java)
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun show() {
                                post { keyboard.showSoftInput(this@apply, 0) }
                            }
                        },
                        "remoteKeyboard",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            view.evaluateJavascript(REMOTE_FIELDS_SCRIPT, null)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val manifest = addonInstallUrl(request.url.toString()) ?: return false
                            onInstall(manifest)
                            return true
                        }
                    }
                    loadUrl(url)
                    requestFocus()
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.destroy() },
        )
    }
}
