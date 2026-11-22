package com.nimeko.stream

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private val DAYS = listOf("MINGGU", "SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU")
private val Span3: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(3) }

@Composable
fun MovieGrid(list: List<Movie>, empty: String, onOpen: (String) -> Unit) {
    if (list.isEmpty()) {
        Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        return
    }
    LazyVerticalGrid(
        GridCells.Fixed(3), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 130.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(list) { i, m -> Poster(m, Modifier.appear(i)) { onOpen(m.id) } }
    }
}

@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val ui = rememberLoad(null, retry) {
        coroutineScope {
            val a = async { Api.ongoing() }
            val b = async { Api.latest(0) }
            a.await() to b.await()
        }
    }
    Async(ui, { retry++ }) { (on, first) ->
        val items = remember { mutableStateListOf<Movie>().apply { addAll(first) } }
        var page by remember { mutableIntStateOf(1) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 130.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(span = Span3) { LargeTitle("Home") }
            val feat = on.ifEmpty { first }.take(6)
            if (feat.isNotEmpty()) item(span = Span3) { Banner(feat, onOpen) }
            if (on.isNotEmpty()) {
                item(span = Span3) { Section("Ongoing") }
                item(span = Span3) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(on) { m -> Poster(m, Modifier.width(112.dp)) { onOpen(m.id) } }
                    }
                }
            }
            item(span = Span3) { Section("Latest updates") }
            itemsIndexed(items) { i, m -> Poster(m, Modifier.appear(i)) { onOpen(m.id) } }
            item(span = Span3) {
                TextButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            try { items.addAll(Api.latest(page++)) } catch (e: Exception) { }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Loading…" else "Load more") }
            }
        }
    }
}

@Composable
fun ScheduleScreen(onOpen: (String) -> Unit) {
    var day by remember { mutableStateOf(DAYS[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1]) }
    var retry by remember { mutableIntStateOf(0) }
    val ui = rememberLoad(day, retry) { Api.schedule(day) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            LargeTitle("Schedule")
            Segmented(DAYS, day, DAYS.map { it.take(1) + it.drop(1).lowercase() }, false) { day = it }
        }
        Async(ui, { retry++ }) { MovieGrid(it, "Nothing scheduled.", onOpen) }
    }
}

@Composable
fun SearchScreen(onOpen: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<Ui<List<Movie>>?>(null) }
    LaunchedEffect(q) {
        if (q.isBlank()) { res = null; return@LaunchedEffect }
        delay(400)
        res = Ui.Loading
        res = try { Ui.Ok(Api.search(q.trim())) } catch (e: CancellationException) { throw e } catch (e: Exception) { Ui.Err(e.message ?: "Error") }
    }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            LargeTitle("Search")
            TextField(
                q, { q = it }, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                placeholder = { Text("Search anime") }, singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceVariant, unfocusedContainerColor = cs.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
        when (val r = res) {
            null -> {}
            is Ui.Loading -> Loading()
            is Ui.Err -> Text(r.m, color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            is Ui.Ok -> MovieGrid(r.v, "No results.", onOpen)
        }
    }
}

@Composable
fun LibraryScreen(prefs: Prefs, onOpen: (String) -> Unit) {
    val favs = remember { prefs.favs() }
    val hist = remember { prefs.hist() }
    val cs = MaterialTheme.colorScheme
    LazyVerticalGrid(
        GridCells.Fixed(3), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 130.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = Span3) { LargeTitle("Library") }
        item(span = Span3) { Section("Favorites") }
        if (favs.isEmpty()) item(span = Span3) { Text("No favorites yet.", color = cs.onSurfaceVariant) }
        itemsIndexed(favs) { i, m -> Poster(m, Modifier.appear(i)) { onOpen(m.id) } }
        item(span = Span3) { Section("History") }
        if (hist.isEmpty()) item(span = Span3) { Text("Nothing watched yet.", color = cs.onSurfaceVariant) }
        items(hist, span = { Span3() }) { h ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surface).clickable { onOpen(h.movie.id) }) {
                Cell(h.movie.title, h.label)
            }
        }
    }
}

@Composable
fun SettingsScreen(prefs: Prefs, theme: String, onTheme: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LargeTitle("Settings")
        Section("Appearance")
        Column(Modifier.padding(top = 10.dp)) {
            Segmented(listOf("auto", "light", "dark"), theme, listOf("Auto", "Light", "Dark"), true, onTheme)
        }
        Section("About")
        Column(Modifier.padding(top = 10.dp)) {
            Card {
                Cell("Version", BuildConfig.VERSION_NAME)
                Cell("Check for updates", "›") {
                    scope.launch {
                        val u = Updater.check()
                        if (u == null) Toast.makeText(ctx, "You are on the latest version.", Toast.LENGTH_SHORT).show()
                        else ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)))
                    }
                }
                Cell("Clear history", "›") {
                    prefs.clearHist()
                    Toast.makeText(ctx, "History cleared.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun DetailScreen(id: String, prefs: Prefs, onBack: () -> Unit, onPlay: (Movie, Episode) -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val cs = MaterialTheme.colorScheme
    val ui = rememberLoad(id, retry) {
        coroutineScope {
            val d = async { Api.detail(id) }
            val e = async { Api.episodes(id, 0) }
            d.await() to e.await()
        }
    }
    Box(Modifier.fillMaxSize().background(cs.background).blockTouch()) {
        Async(ui, { retry++ }) { (d, first) ->
            val eps = remember { mutableStateListOf<Episode>().apply { addAll(first) } }
            var page by remember { mutableIntStateOf(1) }
            var more by remember { mutableStateOf(first.size >= 30) }
            var fav by remember { mutableStateOf(prefs.favs().any { it.id == id }) }
            val scope = rememberCoroutineScope()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().height(380.dp)) {
                        AsyncImage(d.movie.img, d.movie.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to cs.background)))
                        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                            Text(d.movie.title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text(d.meta, color = cs.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            d.genres.forEach { g ->
                                Text(g, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant).padding(11.dp, 5.dp))
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { eps.firstOrNull()?.let { onPlay(d.movie, it) } },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.PlayArrow, null)
                                Text("Play")
                            }
                            OutlinedButton(
                                onClick = {
                                    val cur = prefs.favs()
                                    prefs.saveFavs(if (fav) cur.filter { it.id != id } else listOf(d.movie) + cur)
                                    fav = !fav
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.Favorite, null, tint = if (fav) Color(0xFFFF375F) else cs.onSurfaceVariant)
                            }
                        }
                        if (d.synopsis.isNotBlank()) Text(d.synopsis, color = cs.onSurfaceVariant, fontSize = 14.5.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 14.dp))
                        Section("Episodes")
                        Box(Modifier.height(10.dp))
                    }
                }
                items(eps) { e ->
                    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(cs.surface).clickable { onPlay(d.movie, e) }) { Cell(e.label, "▶") }
                }
                if (more) {
                    item {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        val l = Api.episodes(id, page++)
                                        eps.addAll(l)
                                        if (l.size < 30) more = false
                                    } catch (e: Exception) { }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Load more episodes") }
                    }
                }
            }
        }
        BackBtn(onBack)
    }
}

@Composable
fun BackBtn(onBack: () -> Unit, dark: Boolean = false) {
    Box(
        Modifier.statusBarsPadding().padding(12.dp).clip(CircleShape)
            .background(if (dark) Color(0x8C000000) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .clickable { onBack() }.padding(9.dp)
    ) {
        Icon(Icons.Filled.ArrowBack, "Back", tint = if (dark) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}
