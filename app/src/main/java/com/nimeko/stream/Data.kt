package com.nimeko.stream

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Base64
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
import org.json.JSONTokener
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


/** Detail responses sometimes nest the real fields one level down; return the object that actually has a title. */
fun JSONObject.withTitle(): JSONObject {
    val c = listOf(this) + listOf("movie", "detail", "anime", "data", "info").mapNotNull { optJSONObject(it) }
    return c.firstOrNull { it.pick("title", "name", "judul") != null } ?: this
}

fun movieFromJson(o: JSONObject) = Movie(o.optString("id"), o.optString("title"), o.pick("img"), o.pick("score"), o.pick("ep"), o.pick("status"))

private fun dec(s: String) = String(Base64.decode(s, Base64.DEFAULT)).trim()

object ProviderA {
    private val BASE = dec("aHR0cHM6Ly94eXotYXBpLmFuaW1laW4ubmV0")
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
            val err = j.opt("error")
            val errBad = when (err) {
                null, JSONObject.NULL -> false
                is Boolean -> err
                is Number -> err.toInt() != 0
                is String -> err.isNotBlank() && err != "false" && err != "0"
                else -> true
            }
            val st = j.opt("status")
            val stBad = when (st) {
                is Number -> st.toInt() != 200
                is String -> st.toIntOrNull()?.let { it != 200 } ?: false
                else -> false
            }
            if (!r.isSuccessful || errBad || stBad) throw IOException(j.optString("message").ifBlank { "HTTP ${r.code}: ${t.take(120)}" })
            j.optJSONObject("data") ?: JSONObject()
        }
    }

    suspend fun latest(page: Int) = get("/3/2/explore/movie", mapOf("page" to page, "sort" to "latest")).optJSONArray("movie").objs().map { it.toMovie() }

    suspend fun ongoing() = get("/3/2/home/hot", mapOf("page" to 0)).optJSONArray("movie").objs().filter { it.optString("status") == "ONGOING" }.map { it.toMovie() }

    suspend fun schedule(day: String) = get("/3/2/schedule/data", mapOf("day" to day)).optJSONArray("movie").objs().map { it.toMovie() }

    suspend fun search(k: String) = get("/3/2/explore/movie", mapOf("page" to 0, "sort" to "latest", "keyword" to k)).optJSONArray("movie").objs().map { it.toMovie() }

    suspend fun detail(id: String): Detail {
        val root = get("/3/2/movie/detail/${Uri.encode(id)}")
        val d = root.withTitle()
        val g = d.optJSONArray("genre") ?: root.optJSONArray("genre")
        val genres = (0 until (g?.length() ?: 0)).mapNotNull {
            val x = g!!.opt(it)
            if (x is JSONObject) x.pick("name", "title") else x?.toString()
        }
        val m = d.toMovie(id).let { if (it.img == null) it.copy(img = root.toMovie(id).img) else it }
        val meta = listOfNotNull(m.status, d.pick("year", "release_year"), d.pick("type")).joinToString(" · ")
        return Detail(m, d.pick("synopsis", "description", "sinopsis") ?: root.pick("synopsis", "description", "sinopsis") ?: "", genres, meta)
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

object ProviderB {
    private val BASE = dec("aHR0cHM6Ly9tYWluYXBwc3YxLm5hbmltZWlkLnh5ei8yLjEuMA==")
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private const val P = "b:"
    private fun raw(id: String) = id.removePrefix(P)

    private suspend fun get(path: String, q: Map<String, Any> = emptyMap()): Any = withContext(Dispatchers.IO) {
        val b = (BASE + path).toHttpUrl().newBuilder()
        q.forEach { (k, v) -> b.addQueryParameter(k, v.toString()) }
        val req = Request.Builder().url(b.build()).header("User-Agent", "Dart/3.5 (dart:io)").header("Accept", "application/json").build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            try { JSONTokener(t).nextValue() } catch (e: Exception) { null } ?: throw IOException("Invalid response")
        }
    }

    private fun unwrap(x: Any): Any = if (x is JSONObject && x.opt("data") is JSONObject) x.getJSONObject("data") else x

    private fun findArray(x: Any?, prefer: List<String>, depth: Int = 0): JSONArray? {
        if (x is JSONArray) return if (x.length() == 0 || x.opt(0) is JSONObject) x else null
        if (x !is JSONObject || depth > 3) return null
        for (k in prefer) x.optJSONArray(k)?.let { if (it.length() == 0 || it.opt(0) is JSONObject) return it }
        for (k in x.keys()) findArray(x.opt(k), prefer, depth + 1)?.takeIf { it.length() > 0 }?.let { return it }
        return null
    }

    private fun JSONObject.toMovieB() = Movie(
        P + (pick("id", "_id", "slug", "anime_id") ?: ""),
        pick("title", "name", "judul", "anime_title") ?: "-",
        pick("poster", "cover", "image", "thumbnail", "img", "thumb", "image_url"),
        pick("score", "rating"),
        pick("episode", "episodes", "total_episode", "latest_episode", "last_episode", "eps"),
        pick("status")?.uppercase()
    )

    private fun list(x: Any) = findArray(unwrap(x), listOf("anime", "data", "results", "items", "list")).objs().map { it.toMovieB() }.filter { it.id != P }

    suspend fun latest(page: Int) = list(get("/anime/latest", mapOf("page" to page + 1, "limit" to 20)))

    suspend fun ongoing() = latest(0)

    suspend fun search(k: String) = list(get("/anime/search-paginated", mapOf("q" to k, "page" to 1, "limit" to 20)))

    suspend fun schedule(day: String): List<Movie> {
        val en = mapOf("SENIN" to "monday", "SELASA" to "tuesday", "RABU" to "wednesday", "KAMIS" to "thursday", "JUMAT" to "friday", "SABTU" to "saturday", "MINGGU" to "sunday")
        val keys = setOfNotNull(day.lowercase(), en[day.uppercase()])
        val x = unwrap(get("/anime/schedule"))
        if (x is JSONObject) {
            for (k in x.keys()) if (k.lowercase() in keys) return findArray(x.opt(k), emptyList()).objs().map { it.toMovieB() }
        }
        val arr = findArray(x, listOf("schedule", "anime", "data")).objs()
        val hasDay = arr.any { it.pick("day", "hari") != null }
        return (if (hasDay) arr.filter { it.pick("day", "hari")?.lowercase() in keys } else arr).map { it.toMovieB() }
    }

    suspend fun detail(id: String): Detail {
        val root = unwrap(get("/anime/${Uri.encode(raw(id))}")) as? JSONObject ?: throw IOException("Invalid response")
        val d = root.withTitle()
        val g = d.optJSONArray("genres") ?: d.optJSONArray("genre")
        val genres = (0 until (g?.length() ?: 0)).mapNotNull {
            val v = g!!.opt(it)
            if (v is JSONObject) v.pick("name", "title") else v?.toString()
        }
        val m = d.toMovieB().copy(id = id)
        val meta = listOfNotNull(m.status, d.pick("year", "release_year", "season"), d.pick("type")).joinToString(" · ")
        return Detail(m, d.pick("synopsis", "description", "sinopsis", "deskripsi") ?: "", genres, meta)
    }

    suspend fun episodes(id: String, page: Int): List<Episode> {
        if (page > 0) return emptyList()
        val arr = findArray(unwrap(get("/episode/anime/${Uri.encode(raw(id))}")), listOf("episodes", "episode", "data")).objs()
        val eps = arr.mapIndexed { i, e ->
            val r = e.pick("number", "episode", "ep", "title", "name")
            val n = r?.toDoubleOrNull()
            Triple(n ?: (i + 1).toDouble(), Episode(P + (e.pick("id", "_id", "slug") ?: ""), when {
                r == null -> "Episode ${i + 1}"
                n != null -> "Episode $r"
                else -> r
            }), n != null)
        }
        return if (eps.isNotEmpty() && eps.all { it.third }) eps.sortedByDescending { it.first }.map { it.second } else eps.map { it.second }
    }

    suspend fun stream(id: String): Stream {
        val root = unwrap(get("/episode/${Uri.encode(raw(id))}")) as? JSONObject ?: throw IOException("Episode not found.")
        val iframe = Regex("src=[\"']([^\"']+)")
        val sv = findArray(root, listOf("servers", "server", "streams", "stream", "sources", "embeds", "links", "mirrors")).objs().mapNotNull {
            var l = it.pick("url", "link", "embed", "embed_url", "src", "iframe") ?: return@mapNotNull null
            if (l.startsWith("<")) l = iframe.find(l)?.groupValues?.get(1) ?: return@mapNotNull null
            if (l.startsWith("//")) l = "https:$l"
            Server(it.pick("name", "server", "provider", "title") ?: "Server", it.pick("quality", "resolution", "res") ?: "", it.pick("type") ?: "embed", l, "")
        }.sortedByDescending { s -> s.quality.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val next = root.optJSONObject("next")?.pick("id", "_id", "slug") ?: root.pick("next_id", "nextEpisodeId")
        return Stream(P + (root.pick("anime_id", "animeId") ?: ""), id, sv, next?.let { P + raw(it) })
    }
}

/** Facade: picks the active source for lists, and routes by id prefix for detail/episodes/stream. */
object Api {
    @Volatile var source = 1
    private fun b(id: String) = id.startsWith("b:")

    suspend fun latest(page: Int) = if (source == 2) ProviderB.latest(page) else ProviderA.latest(page)
    suspend fun ongoing() = if (source == 2) ProviderB.ongoing() else ProviderA.ongoing()
    suspend fun schedule(day: String) = if (source == 2) ProviderB.schedule(day) else ProviderA.schedule(day)
    suspend fun search(k: String) = if (source == 2) ProviderB.search(k) else ProviderA.search(k)
    suspend fun detail(id: String) = if (b(id)) ProviderB.detail(id) else ProviderA.detail(id)
    suspend fun episodes(id: String, page: Int) = if (b(id)) ProviderB.episodes(id, page) else ProviderA.episodes(id, page)
    suspend fun stream(id: String) = if (b(id)) ProviderB.stream(id) else ProviderA.stream(id)
}

class Prefs(c: Context) {
    private val p = c.getSharedPreferences("nimeko", Context.MODE_PRIVATE)

    var source: Int
        get() = p.getInt("source", 1)
        set(v) { p.edit().putInt("source", v).apply() }

    var theme: String
        get() = p.getString("theme", "auto") ?: "auto"
        set(v) { p.edit().putString("theme", v).apply() }

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) { p.edit().putBoolean("onboarded", v).apply() }

    var notified: String
        get() = p.getString("notified", "") ?: ""
        set(v) { p.edit().putString("notified", v).apply() }

    var displayName: String
        get() = p.getString("display_name", "") ?: ""
        set(v) { p.edit().putString("display_name", v).apply() }

    var email: String
        get() = p.getString("email", "") ?: ""
        set(v) { p.edit().putString("email", v).apply() }

    val isRegistered: Boolean get() = email.isNotBlank() && displayName.isNotBlank()

    fun clearAccount() { p.edit().remove("display_name").remove("email").apply() }

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
