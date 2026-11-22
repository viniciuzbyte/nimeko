package com.nimeko.stream

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

data class PlayReq(val movie: Movie, val epId: String, val label: String)

class FsChrome(private val act: Activity) : WebChromeClient() {
    var view by mutableStateOf<View?>(null)
    private var cb: CustomViewCallback? = null

    override fun onShowCustomView(v: View, c: CustomViewCallback) {
        view = v
        cb = c
        (act.window.decorView as FrameLayout).addView(v, FrameLayout.LayoutParams(-1, -1))
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun onHideCustomView() {
        view?.let { (act.window.decorView as FrameLayout).removeView(it) }
        view = null
        cb?.onCustomViewHidden()
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

private const val EMBED_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

/**
 * Embeds almost always bounce through a redirect chain onto a *different* host
 * before the actual player loads (e.g. provider.com/embed/x -> cdn-player.net/play/x).
 * The previous WebViewClient blocked any navigation whose host didn't match the
 * very first URL, which silently killed that redirect — the box stayed blank.
 * Fix: let normal http/https navigation through; only intercept non-web schemes
 * (intent://, market://, whatsapp://, …) so the WebView doesn't choke on them.
 */
@Composable
fun EmbedView(url: String, onLoading: (Boolean) -> Unit = {}) {
    val act = LocalContext.current as Activity
    val chrome = remember { FsChrome(act) }
    BackHandler(chrome.view != null) { chrome.onHideCustomView() }
    AndroidView(
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.userAgentString = EMBED_UA
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = chrome
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                        val scheme = r.url.scheme
                        if (scheme == "http" || scheme == "https") return false
                        return try {
                            v.context.startActivity(Intent(Intent.ACTION_VIEW, r.url)); true
                        } catch (e: Exception) { true }
                    }
                    override fun onPageFinished(v: WebView, u: String) { onLoading(false) }
                }
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = {
            if (it.tag != url) {
                it.tag = url
                onLoading(true)
                it.loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun PlayerScreen(req: PlayReq, prefs: Prefs, onBack: () -> Unit, onNext: (String) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var retry by remember { mutableIntStateOf(0) }
    var sel by remember(req.epId) { mutableIntStateOf(0) }
    var embedLoading by remember(req.epId) { mutableStateOf(true) }
    val ui = rememberLoad(req.epId, retry) { Api.stream(req.epId) }
    LaunchedEffect(req.epId) { prefs.addHist(req.movie, req.label) }
    LaunchedEffect(sel) { embedLoading = true }

    Box(Modifier.fillMaxSize().background(cs.background).blockTouch()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding().aspectRatio(16f / 9f)) {
                when (val u = ui) {
                    is Ui.Ok -> {
                        val embeds = u.v.servers.filter { !it.type.equals("direct", true) && !it.type.contains("download", true) }
                        if (embeds.isNotEmpty()) {
                            EmbedView(embeds[sel.coerceIn(0, embeds.size - 1)].link) { embedLoading = it }
                            AnimatedVisibility(embedLoading, enter = fadeIn(), exit = fadeOut(tween(300))) {
                                Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = cs.primary)
                                        Text("Loading player…", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                                    }
                                }
                            }
                        } else {
                            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No embed server available.", color = Color.White, fontSize = 13.sp)
                                OutlinedButton(
                                    onClick = { retry++ }, modifier = Modifier.padding(top = 10.dp), shape = RoundedCornerShape(14.dp)
                                ) { Text("Try again", color = Color.White) }
                            }
                        }
                    }
                    is Ui.Loading -> Loading()
                    is Ui.Err -> {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Couldn't load this episode.", color = Color.White, fontSize = 13.sp)
                            OutlinedButton(
                                onClick = { retry++ }, modifier = Modifier.padding(top = 10.dp), shape = RoundedCornerShape(14.dp)
                            ) { Text("Try again", color = Color.White) }
                        }
                    }
                }
            }
            Async(ui, { retry++ }) { s ->
                val embeds = s.servers.filter { !it.type.equals("direct", true) && !it.type.contains("download", true) }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 40.dp)) {
                    item(key = "title") {
                        Text(req.movie.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.appear())
                        Text(req.label, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp).appear())
                    }
                    if (embeds.size > 1) {
                        item(key = "servers") {
                            Section("Server")
                            Spacer(Modifier.height(8.dp))
                            Segmented(embeds.indices.map { it.toString() }, sel.toString(), embeds.map { "${it.name} ${it.quality}".trim() }, false) {
                                sel = it.toInt()
                            }
                        }
                    }
                    item(key = "actions") {
                        if (s.nextId != null) {
                            Button(
                                { onNext(s.nextId) },
                                Modifier.fillMaxWidth().padding(top = 18.dp).appear(),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Next episode") }
                        }
                        OutlinedButton(
                            onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://animeinweb.com/anime/${s.movieId}?ep=${s.epId}"))) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).appear(1), shape = RoundedCornerShape(14.dp)
                        ) { Text("Open official page") }
                    }
                }
            }
        }
        BackBtn(onBack, dark = true)
    }
}
