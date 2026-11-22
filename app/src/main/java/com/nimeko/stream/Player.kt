package com.nimeko.stream

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Environment
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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

@Composable
fun EmbedView(url: String) {
    val act = LocalContext.current as Activity
    val chrome = remember { FsChrome(act) }
    BackHandler(chrome.view != null) { chrome.onHideCustomView() }
    AndroidView(
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = chrome
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean =
                        r.url.host != Uri.parse(v.tag?.toString() ?: "").host
                }
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = {
            if (it.tag != url) {
                it.tag = url
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
    val ui = rememberLoad(req.epId, retry) { Api.stream(req.epId) }
    LaunchedEffect(req.epId) { prefs.addHist(req.movie, req.label) }
    Box(Modifier.fillMaxSize().background(cs.background).blockTouch()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding().aspectRatio(16f / 9f)) {
                when (val u = ui) {
                    is Ui.Ok -> {
                        val embeds = u.v.servers.filter { it.type == "semi" }
                        if (embeds.isNotEmpty()) EmbedView(embeds[sel.coerceIn(0, embeds.size - 1)].link)
                        else Text("No embed server available.", color = Color.White, modifier = Modifier.align(Alignment.Center))
                    }
                    is Ui.Loading -> Loading()
                    is Ui.Err -> {}
                }
            }
            Async(ui, { retry++ }) { s ->
                val embeds = s.servers.filter { it.type == "semi" }
                val dls = s.servers.filter { it.type == "direct" }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 40.dp)) {
                    item {
                        Text(req.movie.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(req.label, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                    }
                    if (embeds.size > 1) {
                        item {
                            Segmented(embeds.indices.map { it.toString() }, sel.toString(), embeds.map { "${it.name} ${it.quality}".trim() }, false) {
                                sel = it.toInt()
                            }
                        }
                    }
                    item {
                        if (s.nextId != null) {
                            Button({ onNext(s.nextId) }, Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(14.dp)) { Text("Next episode") }
                        }
                        OutlinedButton(
                            onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://animeinweb.com/anime/${s.movieId}?ep=${s.epId}"))) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(14.dp)
                        ) { Text("Open official page") }
                        if (dls.isNotEmpty()) Text("Download", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                    }
                    if (dls.isNotEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surface)) {
                                dls.forEach { d ->
                                    Column(Modifier.clickable {
                                        val name = "Nimeko-${req.movie.title}-${req.label}-${d.quality}.mp4".replace(Regex("[^A-Za-z0-9._-]"), "_")
                                        val r = DownloadManager.Request(Uri.parse(d.link))
                                            .addRequestHeader("User-Agent", "okhttp/4.12.0")
                                            .addRequestHeader("Referer", "https://animeinweb.com/")
                                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                                        ctx.getSystemService(DownloadManager::class.java).enqueue(r)
                                        Toast.makeText(ctx, "Download started", Toast.LENGTH_SHORT).show()
                                    }) { Cell("${d.name} ${d.quality}".trim(), "${d.size} ↓") }
                                }
                            }
                        }
                    }
                }
            }
        }
        BackBtn(onBack, dark = true)
    }
}
