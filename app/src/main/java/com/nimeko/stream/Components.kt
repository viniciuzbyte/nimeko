package com.nimeko.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException

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
        is Ui.Loading -> Loading()
        is Ui.Err -> ErrorView(ui.m, retry)
        is Ui.Ok -> content(ui.v)
    }
}

fun Modifier.blockTouch(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
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
        Button(onClick = retry) { Text("Try again") }
    }
}

@Composable
fun LargeTitle(t: String) {
    Text(t, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp))
}

@Composable
fun Section(t: String) {
    Text(t, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
}

@Composable
fun Cell(title: String, trailing: String = "", onClick: (() -> Unit)? = null) {
    val m = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Row(Modifier.fillMaxWidth().then(m).padding(16.dp, 14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
        Text(trailing, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)) { content() }
}

@Composable
fun Segmented(opts: List<String>, sel: String, labels: List<String> = opts, fill: Boolean = true, onSel: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val base = if (fill) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    Row(base.clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant).padding(2.dp)) {
        opts.forEachIndexed { i, o ->
            val on = o == sel
            val w = if (fill) Modifier.weight(1f) else Modifier
            Box(
                w.clip(RoundedCornerShape(8.dp))
                    .background(if (on) cs.surface else Color.Transparent)
                    .clickable { onSel(o) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                Alignment.Center
            ) {
                Text(labels[i], fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

@Composable
fun Poster(m: Movie, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant)) {
            AsyncImage(m.img, m.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000))))
            if (m.status == "ONGOING") {
                Text("NEW", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(cs.primary).padding(horizontal = 7.dp, vertical = 3.dp))
            }
            if ((m.score?.toFloatOrNull() ?: 0f) > 0f) {
                Text("★ ${m.score}", color = Color(0xFFFFD60A), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x8C000000)).padding(horizontal = 6.dp, vertical = 3.dp))
            }
            if (!m.ep.isNullOrBlank()) {
                Text("EP ${m.ep}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
        }
        Text(m.title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}
