package com.krish.jaatplayer.spotify

import com.krish.jaatplayer.canvas.CanvasArtwork
import android.content.Context
import androidx.datastore.preferences.core.edit
import com.krish.jaatplayer.constants.SpotifyAccessTokenExpiresAtKey
import com.krish.jaatplayer.constants.SpotifyAccessTokenKey
import com.krish.jaatplayer.constants.SpotifySpDcKey
import com.krish.jaatplayer.constants.SpotifySpKeyKey
import com.krish.jaatplayer.utils.dataStore
import kotlinx.coroutines.flow.first
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches the real Spotify Canvas (looping vertical video) for a song.
 *
 * Needs the user to be logged in to Spotify inside the app ([Spotify.accessToken]).
 * 1. Finds the matching Spotify track URI through [Spotify.search].
 * 2. Asks Spotify's canvaz-cache endpoint (protobuf) for that track's canvas.
 *
 * This is an unofficial endpoint. It can change or stop working at any time.
 */
object SpotifyCanvasProvider {
    private const val TAG = "SpotifyCanvas"
    private const val CANVAS_URL = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases"
    private const val HIT_TTL_MS = 24L * 60 * 60 * 1000
    private const val MISS_TTL_MS = 3L * 60 * 60 * 1000

    private data class Entry(val value: CanvasArtwork?, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    private val client by lazy {
        HttpClient(OkHttp) { expectSuccess = false }
    }

    /** Last status line, shown by the temporary on-screen canvas debug label. */
    @Volatile
    var lastStatus: String = ""

    /**
     * Spotify.accessToken is only filled when the Spotify Import screen was opened, so
     * restore (or refresh) it from the saved login here.
     */
    private suspend fun ensureToken(context: Context): Boolean {
        val prefs = context.dataStore.data.first()
        val stored = prefs[SpotifyAccessTokenKey].orEmpty()
        val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
        if (stored.isNotBlank() && expiresAt > System.currentTimeMillis() + 60_000L) {
            Spotify.accessToken = stored
            return true
        }
        val spDc = prefs[SpotifySpDcKey].orEmpty()
        if (spDc.isBlank()) {
            lastStatus = "Spotify: not logged in"
            return false
        }
        val token =
            SpotifyAuth.fetchAccessToken(spDc, prefs[SpotifySpKeyKey].orEmpty()).getOrElse {
                lastStatus = "Spotify: token refresh failed (${it.message?.take(60)})"
                return false
            }
        Spotify.accessToken = token.accessToken
        context.dataStore.edit {
            it[SpotifyAccessTokenKey] = token.accessToken
            it[SpotifyAccessTokenExpiresAtKey] = token.accessTokenExpirationTimestampMs
        }
        return true
    }

    suspend fun getBySongArtist(song: String, artist: String, context: Context): CanvasArtwork? {
        if (song.isBlank() || artist.isBlank()) return null
        val tokenOk =
            try {
                ensureToken(context.applicationContext)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastStatus = "Spotify: token error (${e.message?.take(60)})"
                false
            }
        if (!tokenOk) {
            println("$TAG: $lastStatus")
            return null
        }
        val key = "${song.trim().lowercase(Locale.ROOT)}|${artist.trim().lowercase(Locale.ROOT)}"
        cache[key]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.value }

        val result =
            try {
                withContext(Dispatchers.IO) { lookup(song, artist) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                println("$TAG: failed for '$song' / '$artist': ${e.message}")
                return null // do not cache errors
            }
        cache[key] = Entry(result, System.currentTimeMillis() + if (result != null) HIT_TTL_MS else MISS_TTL_MS)
        return result
    }

    private suspend fun lookup(song: String, artist: String): CanvasArtwork? {
        val found =
            Spotify
                .search(query = "$song $artist", types = listOf("track"), limit = 6)
                .getOrNull()
                ?.tracks
                ?.items
                .orEmpty()

        val wantedSong = norm(song)
        val wantedArtist = norm(artist)
        val track =
            found.firstOrNull { t ->
                val ts = norm(t.name)
                val songOk = ts.contains(wantedSong) || wantedSong.contains(ts)
                val artistOk =
                    t.artists.any { a ->
                        val n = norm(a.name)
                        n.isNotBlank() && (n.contains(wantedArtist) || wantedArtist.contains(n))
                    }
                songOk && artistOk && !t.uri.isNullOrBlank()
            }
        if (track == null) {
            lastStatus = "Spotify: no track match"
            println("$TAG: no Spotify track match for '$song' / '$artist'")
            return null
        }
        val uri = track.uri!!
        val url = requestCanvasUrl(uri)
        if (url == null) {
            lastStatus = "Spotify: track found, no canvas"
            println("$TAG: Spotify has no canvas for $uri")
            return null
        }
        lastStatus = "Spotify: canvas found"
        println("$TAG: found canvas for $uri")
        return CanvasArtwork(
            name = track.name,
            artist = track.artists.firstOrNull()?.name ?: artist,
            animated = url,
            videoUrl = url,
        )
    }

    private suspend fun requestCanvasUrl(trackUri: String): String? {
        val token = Spotify.accessToken ?: return null
        // EntityCanvazRequest { repeated Entity entities = 1 }, Entity { string entity_uri = 1 }
        val entity = ByteArrayOutputStream().also { writeString(it, 1, trackUri) }.toByteArray()
        val body = ByteArrayOutputStream().also { writeBytes(it, 1, entity) }.toByteArray()

        val response =
            client.post(CANVAS_URL) {
                header("Authorization", "Bearer $token")
                header("Content-Type", "application/x-protobuf")
                header("Accept", "application/protobuf")
                header("app-platform", "WebPlayer")
                header("Accept-Language", "en")
                setBody(body)
            }
        if (response.status.value !in 200..299) {
            lastStatus = "Spotify: canvas server HTTP ${response.status.value}"
            println("$TAG: canvaz-cache HTTP ${response.status.value}")
            return null
        }
        val bytes = response.readRawBytes()
        // EntityCanvazResponse { repeated Canvaz canvases = 1 }, Canvaz { string url = 2 }
        for (canvas in readFields(bytes, 1)) {
            for (u in readFields(canvas, 2)) {
                val s = String(u, Charsets.UTF_8)
                if (s.startsWith("http")) return s
            }
        }
        return null
    }

    private fun norm(s: String) =
        s
            .replace(Regex("\\s*[\\[(][^\\])]*[\\])]"), "")
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)

    // ---- minimal protobuf helpers ----

    private fun writeVarint(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v)
    }

    private fun writeBytes(out: ByteArrayOutputStream, field: Int, data: ByteArray) {
        writeVarint(out, (field shl 3) or 2)
        writeVarint(out, data.size)
        out.write(data)
    }

    private fun writeString(out: ByteArrayOutputStream, field: Int, s: String) =
        writeBytes(out, field, s.toByteArray(Charsets.UTF_8))

    /** Returns the payloads of every length-delimited occurrence of [field]. */
    private fun readFields(data: ByteArray, field: Int): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var i = 0
        fun varint(): Long {
            var shift = 0
            var r = 0L
            while (i < data.size) {
                val b = data[i++].toInt()
                r = r or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            return r
        }
        while (i < data.size) {
            val tag = varint().toInt()
            val num = tag ushr 3
            when (tag and 7) {
                0 -> varint()
                1 -> i += 8
                5 -> i += 4
                2 -> {
                    val len = varint().toInt()
                    if (len < 0 || i + len > data.size) return result
                    if (num == field) result.add(data.copyOfRange(i, i + len))
                    i += len
                }
                else -> return result
            }
        }
        return result
    }
}
