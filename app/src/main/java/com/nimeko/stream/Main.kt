package com.nimeko.stream

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private data class Tab(val icon: ImageVector, val label: String)

private val Tabs = listOf(
    Tab(Icons.Filled.Home, "Home"),
    Tab(Icons.Filled.DateRange, "Schedule"),
    Tab(Icons.Filled.Search, "Search"),
    Tab(Icons.Filled.Favorite, "Library"),
    Tab(Icons.Filled.SettingsIcon, "Settings")
)

@Composable
fun Root(askNotif: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var theme by remember { mutableStateOf(prefs.theme) }
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    NimekoTheme(dark) {
        val online by remember { ctx.onlineFlow() }.collectAsState(true)
        var onboarded by remember { mutableStateOf(prefs.onboarded) }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (!onboarded) {
                Welcome {
                    prefs.onboarded = true
                    onboarded = true
                    askNotif()
                }
            } else {
                Main(prefs, theme) { theme = it; prefs.theme = it }
            }
            if (!online) OfflineScreen()
        }
    }
}

@Composable
private fun FloatingBar(tab: Int, onTab: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().softShadow(34.dp).clip(RoundedCornerShape(34.dp))
                .background(cs.surface.copy(alpha = 0.94f)).border(0.5.dp, cs.outlineVariant, RoundedCornerShape(34.dp)).padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Tabs.forEachIndexed { i, t ->
                val on = tab == i
                val bg by animateColorAsState(if (on) cs.primary.copy(alpha = 0.15f) else Color.Transparent, tween(260), label = "bg")
                val tint by animateColorAsState(if (on) cs.primary else cs.onSurfaceVariant, tween(260), label = "tint")
                val sc by animateFloatAsState(if (on) 1.1f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "sc")
                Row(
                    Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                        .clip(RoundedCornerShape(28.dp)).background(bg)
                        .clickable(remember { MutableInteractionSource() }, null) { onTab(i) }
                        .padding(horizontal = if (on) 14.dp else 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(t.icon, t.label, Modifier.size(24.dp).graphicsLayer { scaleX = sc; scaleY = sc }, tint = tint)
                    if (on) Text(t.label, color = tint, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
fun Main(prefs: Prefs, theme: String, onTheme: (String) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var tab by remember { mutableIntStateOf(0) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var detailOn by remember { mutableStateOf(false) }
    var play by remember { mutableStateOf<PlayReq?>(null) }
    var playOn by remember { mutableStateOf(false) }
    var upd by remember { mutableStateOf<Update?>(null) }
    LaunchedEffect(Unit) {
        Updater.check()?.let { upd = it; Updater.notify(ctx, it) }
    }
    BackHandler(playOn) { playOn = false }
    BackHandler(detailOn && !playOn) { detailOn = false }
    val open: (String) -> Unit = { detailId = it; detailOn = true }
    val sheetIn = slideInVertically(spring(0.85f, Spring.StiffnessMediumLow)) { it } + fadeIn(tween(250))
    val sheetOut = slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(250))
    Box(Modifier.fillMaxSize().background(cs.background)) {
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            transitionSpec = {
                val d = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 5 * d } + fadeIn(tween(340)))
                    .togetherWith(slideOutHorizontally(tween(260)) { -it / 5 * d } + fadeOut(tween(200)))
            },
            label = "tab"
        ) { t ->
            when (t) {
                0 -> HomeScreen(open)
                1 -> ScheduleScreen(open)
                2 -> SearchScreen(open)
                3 -> LibraryScreen(prefs, open)
                else -> SettingsScreen(prefs, theme, onTheme)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) { FloatingBar(tab) { tab = it } }
        AnimatedVisibility(detailOn, enter = sheetIn, exit = sheetOut) {
            detailId?.let { id ->
                DetailScreen(id, prefs, { detailOn = false }) { m, e ->
                    play = PlayReq(m, e.id, e.label)
                    playOn = true
                }
            }
        }
        AnimatedVisibility(playOn, enter = sheetIn, exit = sheetOut) {
            play?.let { p ->
                PlayerScreen(p, prefs, { playOn = false }) { next -> play = PlayReq(p.movie, next, "Next episode") }
            }
        }
    }
    upd?.let { u ->
        AlertDialog(
            onDismissRequest = { upd = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Update available") },
            text = { Text("Nimeko ${u.tag} is ready to download.") },
            confirmButton = {
                TextButton({
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)))
                    upd = null
                }) { Text("Download") }
            },
            dismissButton = { TextButton({ upd = null }) { Text("Later") } }
        )
    }
}

@Composable
private fun Fan(ps: List<Movie>) {
    @Composable
    fun card(m: Movie, mod: Modifier) {
        Box(mod.softShadow(22.dp).clip(RoundedCornerShape(22.dp)).border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(22.dp))) {
            AsyncImage(m.img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
    Box(contentAlignment = Alignment.Center) {
        card(ps[0], Modifier.offset((-98).dp, 20.dp).rotate(-9f).size(118.dp, 176.dp))
        card(ps[1], Modifier.offset(98.dp, 20.dp).rotate(9f).size(118.dp, 176.dp))
        card(ps[2], Modifier.size(142.dp, 214.dp))
    }
}

@Composable
fun Welcome(onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pal = listOf(
        Color(0xFF5E5CE6) to Color(0xFF0A84FF),
        Color(0xFFFF375F) to Color(0xFFFF9F0A),
        Color(0xFF30D158) to Color(0xFF0A84FF)
    )
    val pages = listOf(
        Triple(Icons.Filled.PlayArrow, "Welcome to Nimeko", "Stream the latest anime episodes in a clean, fast player."),
        Triple(Icons.Filled.DateRange, "Never miss a day", "Check the weekly schedule and see what is airing right now."),
        Triple(Icons.Filled.Notifications, "Stay up to date", "Get a notification as soon as a new version of Nimeko is released.")
    )
    val st = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val posters by produceState(emptyList<Movie>()) {
        value = try { Api.latest(0).filter { !it.img.isNullOrBlank() } } catch (e: Exception) { emptyList() }
    }
    val c1 by animateColorAsState(pal[st.currentPage].first, tween(700), label = "c1")
    val c2 by animateColorAsState(pal[st.currentPage].second, tween(700), label = "c2")
    val inf = rememberInfiniteTransition(label = "bg")
    val ox by inf.animateFloat(-40f, 40f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ox")
    val last = st.currentPage == pages.size - 1
    Box(Modifier.fillMaxSize().background(cs.background)) {
        Box(Modifier.fillMaxWidth().height(560.dp).background(Brush.verticalGradient(listOf(c1.copy(alpha = 0.5f), c2.copy(alpha = 0.2f), Color.Transparent))))
        Box(Modifier.offset((-90 + ox).dp, (30 - ox).dp).size(340.dp).background(Brush.radialGradient(listOf(c1.copy(alpha = 0.6f), Color.Transparent)), CircleShape))
        Box(Modifier.align(Alignment.TopEnd).offset((80 - ox).dp, (170 + ox).dp).size(300.dp).background(Brush.radialGradient(listOf(c2.copy(alpha = 0.55f), Color.Transparent)), CircleShape))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(
                    "NIMEKO · STREAM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.5.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Brush.horizontalGradient(listOf(c1, c2))).padding(horizontal = 12.dp, vertical = 6.dp)
                )
                if (!last) TextButton(onDone) { Text("Skip") }
            }
            HorizontalPager(st, Modifier.weight(1f)) { i ->
                val (icon, title, text) = pages[i]
                val ps = posters.drop(i * 3).take(3)
                Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(300.dp), Alignment.Center) {
                        if (ps.size == 3) Fan(ps)
                        else Box(Modifier.size(140.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c1, c2))), Alignment.Center) {
                            Icon(icon, null, Modifier.size(64.dp), tint = Color.White)
                        }
                    }
                    Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp))
                    Text(text, fontSize = 16.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, start = 16.dp, end = 16.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), Arrangement.Center, Alignment.CenterVertically) {
                repeat(pages.size) { i ->
                    val w by animateDpAsState(if (i == st.currentPage) 28.dp else 8.dp, tween(300), label = "dot")
                    Box(Modifier.padding(horizontal = 4.dp).size(w, 8.dp).clip(CircleShape).background(if (i == st.currentPage) c1 else cs.surfaceVariant))
                }
            }
            Box(
                Modifier.fillMaxWidth().height(56.dp).softShadow(28.dp).clip(RoundedCornerShape(28.dp))
                    .background(Brush.horizontalGradient(listOf(c1, c2)))
                    .clickable { if (last) onDone() else scope.launch { st.animateScrollToPage(st.currentPage + 1) } },
                Alignment.Center
            ) {
                Text(if (last) "Get started" else "Continue", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun OfflineScreen() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(cs.background).blockTouch().padding(32.dp),
        Arrangement.Center, Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(110.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFFF375F), Color(0xFFFF9F0A)))), Alignment.Center) {
            Icon(Icons.Filled.Warning, null, Modifier.size(54.dp), tint = Color.White)
        }
        Text("No internet connection", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
        Text(
            "Nimeko needs an internet connection. Connect to Wi-Fi or mobile data to continue.",
            color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        Button(
            onClick = { ctx.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
            shape = RoundedCornerShape(16.dp)
        ) { Text("Open network settings") }
    }
}
