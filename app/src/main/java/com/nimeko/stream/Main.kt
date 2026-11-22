package com.nimeko.stream

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = cs.background,
            bottomBar = {
                NavigationBar(containerColor = cs.surface.copy(alpha = 0.94f), tonalElevation = 0.dp) {
                    Tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = cs.primary, selectedTextColor = cs.primary,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = cs.onSurfaceVariant, unselectedTextColor = cs.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(bottom = pad.calculateBottomPadding()).statusBarsPadding()) {
                when (tab) {
                    0 -> HomeScreen(open)
                    1 -> ScheduleScreen(open)
                    2 -> SearchScreen(open)
                    3 -> LibraryScreen(prefs, open)
                    else -> SettingsScreen(prefs, theme, onTheme)
                }
            }
        }
        AnimatedVisibility(detailOn, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            detailId?.let { id ->
                DetailScreen(id, prefs, { detailOn = false }) { m, e ->
                    play = PlayReq(m, e.id, e.label)
                    playOn = true
                }
            }
        }
        AnimatedVisibility(playOn, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            play?.let { p ->
                PlayerScreen(p, prefs, { playOn = false }) { next -> play = PlayReq(p.movie, next, "Next episode") }
            }
        }
    }
    upd?.let { u ->
        AlertDialog(
            onDismissRequest = { upd = null },
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
fun Welcome(onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pages = listOf(
        Triple(Icons.Filled.PlayArrow, "Welcome to Nimeko", "Stream the latest anime episodes in a clean, fast player."),
        Triple(Icons.Filled.DateRange, "Never miss a day", "Check the weekly schedule and see what is airing right now."),
        Triple(Icons.Filled.Notifications, "Stay up to date", "Get a notification as soon as a new version of Nimeko is released.")
    )
    val st = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = st.currentPage == pages.size - 1
    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth().height(40.dp), Arrangement.End, Alignment.CenterVertically) {
            if (!last) TextButton(onDone) { Text("Skip") }
        }
        HorizontalPager(st, Modifier.weight(1f)) { i ->
            val (icon, title, text) = pages[i]
            Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(132.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF5E5CE6), cs.primary))),
                    Alignment.Center
                ) { Icon(icon, null, Modifier.size(64.dp), tint = Color.White) }
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 36.dp))
                Text(text, fontSize = 16.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), Arrangement.Center) {
            repeat(pages.size) { i ->
                Box(Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(if (i == st.currentPage) cs.primary else cs.surfaceVariant))
            }
        }
        Button(
            onClick = { if (last) onDone() else scope.launch { st.animateScrollToPage(st.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)
        ) { Text(if (last) "Get started" else "Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
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
        Icon(Icons.Filled.Warning, null, Modifier.size(64.dp), tint = cs.primary)
        Text("No internet connection", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
        Text(
            "Nimeko needs an internet connection. Connect to Wi-Fi or mobile data to continue.",
            color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        Button(
            onClick = { ctx.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
            shape = RoundedCornerShape(14.dp)
        ) { Text("Open network settings") }
    }
}
