package com.girlspace.app.ui.reels

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubeIFramePlayer(
    videoId: String,
    modifier: Modifier = Modifier
) {
    // Use youtube-nocookie to reduce cookie issues; still ToS-safe iframe embed.
    val embedUrl =
        "https://www.youtube-nocookie.com/embed/$videoId" +
                "?playsinline=1&autoplay=1&controls=1&rel=0&modestbranding=1"

    val html = """
        <!DOCTYPE html>
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0"/>
            <style>
              html, body { margin:0; padding:0; background:black; height:100%; width:100%; overflow:hidden; }
              iframe { position:absolute; top:0; left:0; right:0; bottom:0; width:100%; height:100%; border:0; }
            </style>
          </head>
          <body>
            <iframe
              src="$embedUrl"
              allow="autoplay; encrypted-media; picture-in-picture"
              allowfullscreen>
            </iframe>
          </body>
        </html>
    """.trimIndent()

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.BLACK)

                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = false
                }

                val s = settings
                s.javaScriptEnabled = true
                s.domStorageEnabled = true
                s.loadsImagesAutomatically = true
                s.mediaPlaybackRequiresUserGesture = false
                s.useWideViewPort = true
                s.loadWithOverviewMode = true
                s.cacheMode = WebSettings.LOAD_DEFAULT
                s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                s.javaScriptCanOpenWindowsAutomatically = true
                s.setSupportMultipleWindows(true)

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                loadDataWithBaseURL(
                    "https://www.youtube.com",
                    html,
                    "text/html",
                    "utf-8",
                    null
                )
            }
        }
    )

    DisposableEffect(videoId) {
        onDispose { /* WebView will be disposed by AndroidView */ }
    }
}
