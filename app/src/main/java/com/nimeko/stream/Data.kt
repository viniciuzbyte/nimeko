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
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun JSONObject.findArray(vararg keys: String): JSONArray? {
        for (key in keys) optJSONArray(key)?.let { return it }
        for (key in listOf("data", "result", "results", "payload")) {
            val nested = optJSONObject(key) ?: continue
            for (name in keys) nested.optJSONArray(name)?.let { return it }
        }
        return null
    }

    private suspend fun get(path: String, q: Map<String, Any> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        val b = (BASE + path).toHttpUrl().newBuilder()
        (mapOf<String, Any>("id_user" to "0", "key_client" to "guest", "apk_ver" to "5.2.2") + q)
            .forEach { (k, v) -> b.addQueryParameter(k, v.toString()) }

        val req = Request.Builder().url(b.build())
            .header("User-Agent", "Nimeko-Android/1.1 (OkHttp)")
            .header("apk_ver", "5.2.2")
            .header("Accept", "application/json")
            .build()

        http.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = try {
                JSONObject(body)
            } catch (_: Exception) {
                throw IOException("API returned invalid JSON (HTTP ${response.code}).")
            }

            val error = json.opt("error")
            val hasError = when (error) {
                null, JSONObject.NULL -> false
                is Boolean -> error
                is Number -> error.toInt() != 0
                is String -> error.isNotBlank() && error != "false" && error != "0"
                else -> true
            }
            val apiStatus = json.opt("status")
            val statusFailed = when (apiStatus) {
                is Number -> apiStatus.toInt() !in 200..299
                is String -> apiStatus.toIntOrNull()?.let { it !in 200..299 } ?: false
                else -> false
            }

            if (!response.isSuccessful || hasError || statusFailed) {
                val message = json.pick("message", "msg", "error_description")
                throw IOException(message ?: "API request failed (HTTP ${response.code}).")
            }

            val payload = json.optJSONObject("data")
                ?: json.optJSONObject("result")
                ?: json.optJSONObject("payload")
                ?: json.takeIf { it.has("movie") || it.has("episode") || it.has("server") }
                ?: throw IOException("HTTP ${response.code}, but the API response has no data object.")

            payload
        }
    }

    suspend fun latest(page: Int): List<Movie> {
        val json = get("/3/2/explore/movie", mapOf("page" to page, "sort" to "latest"))
        return json.findArray("movie", "movies", "items", "results").objs()
            .map { it.toMovie() }.filter { it.id.isNotBlank() }
    }

    suspend fun ongoing(): List<Movie> {
        val json = get("/3/2/home/hot", mapOf("page" to 0))
        return json.findArray("movie", "movies", "items", "results").objs()
            .map { it.toMovie() }
            .filter { it.id.isNotBlank() && it.status.equals("ONGOING", ignoreCase = true) }
    }

    suspend fun schedule(day: String): List<Movie> {
        val json = get("/3/2/schedule/data", mapOf("day" to day))
        return json.findArray("movie", "movies", "items", "results").objs()
            .map { it.toMovie() }.filter { it.id.isNotBlank() }
    }

    suspend fun search(k: String): List<Movie> {
        val json = get("/3/2/explore/movie", mapOf("page" to 0, "sort" to "latest", "keyword" to k))
        return json.findArray("movie", "movies", "items", "results").objs()
            .map { it.toMovie() }.filter { it.id.isNotBlank() }
    }

    suspend fun detail(id: String): Detail {
        val d = get("/3/2/movie/detail/${Uri.encode(id)}")
        val g = d.findArray("genre", "genres")
        val genres = (0 until (g?.length() ?: 0)).mapNotNull { i ->
            val x = g?.opt(i)
            if (x is JSONObject) x.pick("name", "title") else x?.toString()
        }
        val m = d.toMovie(id)
        val meta = listOfNotNull(m.status, d.pick("year", "release_year"), d.pick("type")).joinToString(" · ")
        return Detail(m, d.pick("synopsis", "description", "sinopsis") ?: "", genres, meta)
    }

    suspend fun episodes(id: String, page: Int): List<Episode> {
        val json = get("/3/2/movie/episode/${Uri.encode(id)}", mapOf("page" to page))
        return json.findArray("episode", "episodes", "items", "results").objs().mapIndexedNotNull { i, e ->
            val episodeId = e.pick("id", "episode_id") ?: return@mapIndexedNotNull null
            val raw = e.pick("title", "name", "episode", "number", "ep")
            val label = when {
                raw == null -> "Episode ${page * 30 + i + 1}"
                raw.all { it.isDigit() } -> "Episode $raw"
                else -> raw
            }
            Episode(episodeId, label)
        }
    }

    suspend fun stream(id: String): Stream {
        val d = get("/3/2/episode/streamnew/${Uri.encode(id)}")
        val e = d.optJSONObject("episode") ?: throw IOException("The API did not return episode details.")
        val servers = d.findArray("server", "servers", "sources", "links").objs()
            .mapNotNull { item ->
                val link = item.pick("link", "url", "src") ?: return@mapNotNull null
                if (!link.startsWith("https://") && !link.startsWith("http://")) return@mapNotNull null
                Server(
                    item.pick("name", "server", "label") ?: "Server",
                    item.pick("quality", "resolution") ?: "",
                    item.pick("type", "kind")?.lowercase() ?: "embed",
                    link,
                    item.pick("key_file_size", "size") ?: ""
                )
            }
            .distinctBy { it.link }
            .sortedByDescending { server -> server.quality.filter { it.isDigit() }.toIntOrNull() ?: 0 }

        return Stream(
            e.pick("id_movie", "movie_id") ?: "",
            e.pick("id", "episode_id") ?: id,
            servers,
            d.optJSONObject("episode_next")?.pick("id", "episode_id")
        )
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
