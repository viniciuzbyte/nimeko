package com.nimeko.stream

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

sealed interface Ui<out T> {
    object Loading : Ui<Nothing>
    data class Ok<T>(val v: T) : Ui<T>
    data class Err(val m: String) : Ui<Nothing>
}

@Composable
fun <T> rememberLoad(key: Any?, retry: Int, f: suspend () -> T): Ui<T> {
    val s = produceState<Ui<T>>(Ui.Loading, key, retry) {
        value = Ui.Loading
        value = try { Ui.Ok(f()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Ui.Err(e.message ?: "Error") }
    }
    return s.value
}

@Composable
fun <T> Async(ui: Ui<T>, retry: () -> Unit, content: @Composable (T) -> Unit) {
    when (ui) {
        is Ui.Loading -> Skeleton()
        is Ui.Err -> ErrorView(ui.m, retry)
        is Ui.Ok -> content(ui.v)
    }
}

fun Modifier.blockTouch(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}

fun Modifier.softShadow(r: Dp = 16.dp): Modifier =
    shadow(10.dp, RoundedCornerShape(r), clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x38000000))

fun Modifier.appear(i: Int = 0): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((i % 9) * 45L)
        a.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 48f
        scaleX = 0.92f + 0.08f * a.value
        scaleY = 0.92f + 0.08f * a.value
    }
}

@Composable
fun Shimmer(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "x")
    val base = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(
            Brush.horizontalGradient(listOf(base, base.copy(alpha = 0.4f), base), startX = -300f + x * 1500f, endX = x * 1500f + 300f)
        )
    )
}

@Composable
fun Skeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Shimmer(Modifier.padding(top = 12.dp).fillMaxWidth(0.5f).height(34.dp))
        Shimmer(Modifier.padding(top = 16.dp).fillMaxWidth().height(190.dp))
        repeat(2) {
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { Shimmer(Modifier.weight(1f).aspectRatio(2f / 3f)) }
            }
        }
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
}

@Composable
fun ErrorView(msg: String, retry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("Something went wrong", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
        Button(onClick = retry, shape = RoundedCornerShape(16.dp)) { Text("Try again") }
    }
}

@Composable
fun LargeTitle(t: String) {
    Text(t, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp))
}

@Composable
fun Section(t: String) {
    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 5.dp, height = 22.dp).clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))))
        Text(t, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
fun Cell(title: String, trailing: String = "", onClick: (() -> Unit)? = null) {
    val m = if (onClick != null) Modifier.sfxClickable { onClick() } else Modifier
    Row(Modifier.fillMaxWidth().then(m).padding(16.dp, 14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
        Text(trailing, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().softShadow(20.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)) { content() }
}

@Composable
fun Segmented(opts: List<String>, sel: String, labels: List<String> = opts, fill: Boolean = true, onSel: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val base = if (fill) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    Row(base.clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant).padding(3.dp)) {
        opts.forEachIndexed { i, o ->
            val on = o == sel
            val bg by animateColorAsState(if (on) cs.primaryContainer else Color.Transparent, tween(240), label = "seg")
            val w = if (fill) Modifier.weight(1f) else Modifier
            Box(
                w.clip(RoundedCornerShape(11.dp)).background(bg).sfxClickable { onSel(o) }.padding(horizontal = 14.dp, vertical = 9.dp),
                Alignment.Center
            ) {
                Text(labels[i], fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = if (on) cs.onPrimaryContainer else cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun Poster(m: Movie, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.94f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    val click = rememberSfxOnClick(onClick)
    Column(modifier.graphicsLayer { scaleX = sc; scaleY = sc }.clickable(src, null, onClick = click)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).softShadow(16.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceVariant)) {
            AsyncImage(m.img, m.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000))))
            if (m.status == "ONGOING") {
                Text("NEW", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(9.dp)).background(Brush.horizontalGradient(listOf(cs.primary, cs.secondary))).padding(horizontal = 7.dp, vertical = 3.dp))
            }
            if ((m.score?.toFloatOrNull() ?: 0f) > 0f) {
                Text("★ ${m.score}", color = Color(0xFFFFD60A), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(9.dp)).background(Color(0x8C000000)).padding(horizontal = 6.dp, vertical = 3.dp))
            }
            if (!m.ep.isNullOrBlank()) {
                Text("EP ${m.ep}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
        }
        Text(m.title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
fun Banner(list: List<Movie>, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val st = rememberPagerState { list.size }
    LaunchedEffect(list.size) {
        while (list.size > 1) {
            delay(4500)
            st.animateScrollToPage((st.currentPage + 1) % list.size, animationSpec = tween(700, easing = FastOutSlowInEasing))
        }
    }
    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
        HorizontalPager(st, Modifier.fillMaxWidth(), pageSpacing = 12.dp) { p ->
            val m = list[p]
            val off = ((st.currentPage - p) + st.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth().height(210.dp)
                    .graphicsLayer { val s = 1f - 0.07f * off; scaleX = s; scaleY = s; alpha = 1f - 0.4f * off }
                    .softShadow(26.dp).clip(RoundedCornerShape(26.dp)).background(cs.surfaceVariant)
                    .sfxClickable { onOpen(m.id) }
            ) {
                AsyncImage(m.img, m.title, Modifier.fillMaxSize().graphicsLayer { translationX = off * 40f }, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.25f to Color.Transparent, 1f to Color(0xE6000000))))
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Text(
                        if (m.status == "ONGOING") "ONGOING" else "FEATURED", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(cs.primary).padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                    Text(m.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, end = 60.dp))
                    Text(
                        "Watch now", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(list.size) { i ->
                val w by animateDpAsState(if (i == st.currentPage) 20.dp else 7.dp, tween(300), label = "dot")
                Box(Modifier.padding(start = 4.dp).height(7.dp).size(w, 7.dp).clip(CircleShape).background(if (i == st.currentPage) Color.White else Color(0x80FFFFFF)))
            }
        }
    }
}
