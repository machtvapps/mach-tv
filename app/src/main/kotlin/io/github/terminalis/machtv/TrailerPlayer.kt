package io.github.terminalis.machtv

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val TRAILER_ORIGIN = "https://app.local"
private const val TRAILER_PLAYING = "trailer playing"
private const val TRAILER_FAILED = "trailer failed"
private const val TRAILER_ENDED = "trailer ended"

private enum class TrailerState { Loading, Playing, Failed }

private fun trailerPage(ytId: String) = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;background:#000;overflow:hidden}#player{position:fixed;left:0;top:0;width:100vw;height:100vh;border:0}</style></head>
<body><div id="player"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
function onYouTubeIframeAPIReady(){
  new YT.Player('player',{width:'100%',height:'100%',videoId:'$ytId',
    playerVars:{autoplay:1,controls:1,playsinline:1,rel:0,origin:'$TRAILER_ORIGIN'},
    events:{onReady:function(e){e.target.playVideo()},
            onStateChange:function(e){if(e.data==1)console.log('$TRAILER_PLAYING');if(e.data==0)console.log('$TRAILER_ENDED')},
            onError:function(){console.log('$TRAILER_FAILED')}}});
}
</script></body></html>
"""

@Composable
fun TrailerPlayer(ytId: String, onClose: () -> Unit) {
    var state by remember { mutableStateOf(TrailerState.Loading) }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(AndroidColor.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            when (message.message()) {
                                TRAILER_PLAYING -> state = TrailerState.Playing
                                TRAILER_FAILED -> state = TrailerState.Failed
                                TRAILER_ENDED -> onClose()
                            }
                            return true
                        }
                    }
                    loadDataWithBaseURL(TRAILER_ORIGIN, trailerPage(ytId), "text/html", "utf-8", null)
                    requestFocus()
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.destroy() },
        )
        when (state) {
            TrailerState.Loading -> LoadingSpinner()
            TrailerState.Failed -> BasicText("This trailer can't be played", style = MachText.body)
            TrailerState.Playing -> Unit
        }
    }
}

@Composable
fun LoadingSpinner(modifier: Modifier = Modifier) {
    val turn by rememberInfiniteTransition(label = "spinner").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "turn")
    Canvas(modifier.size(48.dp)) {
        val stroke = 4.dp.toPx()
        drawArc(Color.White, turn, 270f, useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
