package com.nimeko.stream

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Movie(val id: String, val title: String, val img: String?, val score: String?, val ep: String?, val status: String?) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("img", img).put("score", score).put("ep", ep).put("status", status)
}
data class Detail(val movie: Movie, val synopsis: String, val genres: List<String>, val meta: String)
data class Episode(val id: String, val label: String)
data class Server(val name: String, val quality: String, val type: String, val link: String, val size: String)
data class Stream(val movieId: String, val epId: String, val servers: List<Server>, val nextId: String?)
data class Hist(val movie: Movie, val label: String)

fun JSONObject.pick(vararg keys: String): String? {
    for (k in keys) {
        if (has(k) && !isNull(k)) {
            val s = opt(k).toString()
            if (s.isNotBlank()) return s
        }
    }
    return null
}

fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONObject.toMovie(idOverride: String? = null) = Movie(
    idOverride ?: pick("id", "id_movie", "movie_id") ?: "",
    pick("title", "name", "judul") ?: "-",
    pick("cover", "image", "poster", "thumbnail", "img", "url_cover", "image_cover"),
    pick("score", "rating"),
    pick("episode_last", "last_episode", "episode", "total_episode", "eps"),
    pick("status")
)

fun movieFromJson(o: JSONObject) = Movie(o.optString("id"), o.optString("title"), o.pick("img"), o.pick("score"), o.pick("ep"), o.pick("status"))

object Api {
    private const val BASE = "https://xyz-api.animein.net"
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()

    private suspend fun get(path: String, q: Map<String, Any> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        val b = (BASE + path).toHttpUrl().newBuilder()
        (mapOf<String, Any>("id_user" to "0", "key_client" to "guest", "apk_ver" to "5.2.2") + q).forEach { (k, v) -> b.addQueryParameter(k, v.toString()) }
        val req = Request.Builder().url(b.build())
            .header("User-Agent", "okhttp/4.12.0")
            .header("apk_ver", "5.2.2")
            .header("Accept", "application/json")
            .build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            val j = try { JSONObject(t) } catch (e: Exception) { throw IOException("Invalid response (${r.code})") }
            val bad = !r.isSuccessful || (j.has("error") && !j.isNull("error")) || (j.has("status") && j.optInt("status") != 200)
            if (bad) throw IOException(j.optString("message").ifBlank { "HTTP ${r.code}" })
            j.optJSONObject("data") ?: JSONObject()
        }
    }

    suspend fun latest(page: Int) = get("/3/2/explore/movie", mapOf("page" to page, "sort" to "latest")).optJSONArray("movie").objs().map { it.toMovie() }

    suspend fun ongoing() = get("/3/2/home/hot", mapOf("page" to 0)).optJSONArray("movie").objs().filter { it.optString("status") == "ONGOING" }.map { it.toMovie() }

    suspend fun schedule(day: String) = get("/3/2/schedule/data", mapOf("day" to day)).optJSONArray("movie").objs().map { it.toMovie() }

    suspend fun search(k: String) = get("/3/2/explore/movie", mapOf("page" to 0, "sort" to "latest", "keyword" to k)).optJSONArray("movie").objs().map { it.toMovie() }

    suspend fun detail(id: String): Detail {
        val d = get("/3/2/movie/detail/${Uri.encode(id)}")
        val g = d.optJSONArray("genre")
        val genres = (0 until (g?.length() ?: 0)).mapNotNull {
            val x = g!!.opt(it)
            if (x is JSONObject) x.pick("name", "title") else x?.toString()
        }
        val m = d.toMovie(id)
        val meta = listOfNotNull(m.status, d.pick("year", "release_year"), d.pick("type")).joinToString(" · ")
        return Detail(m, d.pick("synopsis", "description", "sinopsis") ?: "", genres, meta)
    }

    suspend fun episodes(id: String, page: Int): List<Episode> =
        get("/3/2/movie/episode/${Uri.encode(id)}", mapOf("page" to page)).optJSONArray("episode").objs().mapIndexed { i, e ->
            val raw = e.pick("title", "name", "episode", "number", "ep")
            val label = when {
                raw == null -> "Episode ${page * 30 + i + 1}"
                raw.all { c -> c.isDigit() } -> "Episode $raw"
                else -> raw
            }
            Episode(e.pick("id") ?: "", label)
        }

    suspend fun stream(id: String): Stream {
        val d = get("/3/2/episode/streamnew/${Uri.encode(id)}")
        val e = d.optJSONObject("episode") ?: throw IOException("Episode not found.")
        val sv = d.optJSONArray("server").objs()
            .filter { it.optString("link").isNotBlank() }
            .map { Server(it.pick("name") ?: "Server", it.pick("quality") ?: "", it.optString("type"), it.optString("link"), it.pick("key_file_size") ?: "") }
            .sortedByDescending { s -> s.quality.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        return Stream(e.pick("id_movie") ?: "", e.pick("id") ?: id, sv, d.optJSONObject("episode_next")?.pick("id"))
    }
}

class Prefs(c: Context) {
    private val p = c.getSharedPreferences("nimeko", Context.MODE_PRIVATE)

    var theme: String
        get() = p.getString("theme", "auto") ?: "auto"
        set(v) { p.edit().putString("theme", v).apply() }

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) { p.edit().putBoolean("onboarded", v).apply() }

    var notified: String
        get() = p.getString("notified", "") ?: ""
        set(v) { p.edit().putString("notified", v).apply() }

    fun favs(): List<Movie> = try {
        JSONArray(p.getString("fav", "[]")).objs().map { movieFromJson(it) }
    } catch (e: Exception) { emptyList() }

    fun saveFavs(l: List<Movie>) {
        p.edit().putString("fav", JSONArray(l.map { it.toJson() }).toString()).apply()
    }

    fun hist(): List<Hist> = try {
        JSONArray(p.getString("hist", "[]")).objs().map { Hist(movieFromJson(it.getJSONObject("m")), it.optString("label")) }
    } catch (e: Exception) { emptyList() }

    fun addHist(m: Movie, label: String) {
        val n = listOf(Hist(m, label)) + hist().filter { it.movie.id != m.id }
        val arr = JSONArray(n.take(30).map { JSONObject().put("m", it.movie.toJson()).put("label", it.label) })
        p.edit().putString("hist", arr.toString()).apply()
    }

    fun clearHist() {
        p.edit().putString("hist", "[]").apply()
    }
}

fun Context.onlineFlow(): Flow<Boolean> = callbackFlow {
    val cm = getSystemService(ConnectivityManager::class.java)
    fun now() = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val cb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(now()) }
        override fun onLost(network: Network) { trySend(now()) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(now()) }
    }
    trySend(now())
    cm.registerDefaultNetworkCallback(cb)
    awaitClose { cm.unregisterNetworkCallback(cb) }
}.distinctUntilChanged()
