package com.krish.jaatplayer.lyrics

import android.content.Context
import android.util.Base64
import com.krish.jaatplayer.constants.EnableMusixmatchKey
import com.krish.jaatplayer.utils.dataStore
import com.krish.jaatplayer.utils.get
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Musixmatch lyrics provider (synced LRC first, plain text as fallback).
 * Self-contained: uses only ktor/okhttp + kotlinx-serialization already in the app module.
 */
object MusixmatchLyricsProvider : LyricsProvider {
    override val name: String = "Musixmatch"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableMusixmatchKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = MusixmatchClient.getLyrics(title, artist, duration)
}

private object MusixmatchClient {
    private const val BASE_URL = "https://apic.musixmatch.com/ws/1.1"
    private const val APP_ID = "web-desktop-app-v1.0"
    private const val SIGNING_SECRET = "RJDefUswhwjkZDeM"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
            }
            expectSuccess = false
        }
    }

    private val tokenMutex = Mutex()
    private var cachedToken: String? = null

    /** Signs a full request URL the way Musixmatch's web client does (HMAC-SHA256 over url + yyyyMMdd UTC). */
    private fun sign(url: String): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SIGNING_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal((url + date).toByteArray(Charsets.UTF_8))
        val signature = Base64.encodeToString(raw, Base64.NO_WRAP)
        return "$url&signature=${URLEncoder.encode(signature, "UTF-8")}&signature_protocol=sha256"
    }

    private suspend fun fetchNewToken(): String {
        val text = client.get(sign("$BASE_URL/token.get?app_id=$APP_ID")).bodyAsText()
        return messageBody(text)?.get("user_token")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Musixmatch token request failed")
    }

    private suspend fun getToken(): String = tokenMutex.withLock {
        cachedToken ?: fetchNewToken().also { cachedToken = it }
    }

    private suspend fun clearToken() = tokenMutex.withLock { cachedToken = null }

    /** Signed GET; on 401/402 refreshes the token once and retries. */
    private suspend fun signedGet(buildUrl: (token: String) -> String): HttpResponse {
        val response = client.get(sign(buildUrl(getToken())))
        if (response.status.value != 401 && response.status.value != 402) return response
        clearToken()
        return client.get(sign(buildUrl(getToken())))
    }

    private fun messageBody(text: String): JsonObject? =
        runCatching {
            json.parseToJsonElement(text).jsonObject["message"]?.jsonObject?.get("body") as? JsonObject
        }.getOrNull()

    private class Track(val id: Long, val name: String, val artist: String, val length: Int?, val hasSubtitles: Boolean)

    private suspend fun searchTracks(title: String, artist: String): List<Track> {
        val text = signedGet { token ->
            "$BASE_URL/track.search?app_id=$APP_ID" +
                "&q_track=${URLEncoder.encode(title, "UTF-8")}" +
                "&q_artist=${URLEncoder.encode(artist, "UTF-8")}" +
                "&f_has_lyrics=1&s_track_rating=desc&quorum_factor=1&page_size=10&page=1" +
                "&usertoken=${URLEncoder.encode(token, "UTF-8")}"
        }.bodyAsText()
        val list = messageBody(text)?.get("track_list") as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return list.mapNotNull { item ->
            runCatching {
                val t = item.jsonObject["track"]!!.jsonObject
                Track(
                    id = t["track_id"]!!.jsonPrimitive.long,
                    name = t["track_name"]?.jsonPrimitive?.content.orEmpty(),
                    artist = t["artist_name"]?.jsonPrimitive?.content.orEmpty(),
                    length = t["track_length"]?.jsonPrimitive?.int,
                    hasSubtitles = (t["has_subtitles"]?.jsonPrimitive?.int ?: 0) == 1,
                )
            }.getOrNull()
        }
    }

    private fun score(track: Track, title: String, artist: String, duration: Int): Double {
        var s = 0.0
        val name = track.name.trim().lowercase()
        val wanted = title.trim().lowercase()
        s += when {
            name == wanted -> 80.0
            name.contains(wanted) || wanted.contains(name) -> 40.0
            else -> 0.0
        }
        if (track.artist.trim().lowercase().contains(artist.trim().lowercase())) s += 40.0
        track.length?.let { len ->
            val diff = abs(len - duration)
            s += when {
                diff <= 2 -> 30.0
                diff <= 5 -> 15.0
                diff <= 10 -> 5.0
                else -> -20.0
            }
        }
        return s
    }

    private suspend fun fetchSubtitleLrc(trackId: Long): String? {
        val text = signedGet { token ->
            "$BASE_URL/track.subtitle.get?app_id=$APP_ID&track_id=$trackId&subtitle_format=mxm" +
                "&usertoken=${URLEncoder.encode(token, "UTF-8")}"
        }.bodyAsText()
        val body = messageBody(text)
            ?.get("subtitle")?.jsonObject?.get("subtitle_body")?.jsonPrimitive?.content ?: return null
        val lines: JsonElement = json.parseToJsonElement(body)
        return buildString {
            for (line in lines.jsonArray) {
                val o = line.jsonObject
                val lineText = o["text"]?.jsonPrimitive?.content.orEmpty()
                if (lineText.isBlank()) continue
                val totalMs = ((o["time"]?.jsonObject?.get("total")?.jsonPrimitive?.double ?: continue) * 1000).toLong()
                val minutes = totalMs / 1000 / 60
                val seconds = (totalMs / 1000) % 60
                val millis = totalMs % 1000
                appendLine(String.format(Locale.US, "[%02d:%02d.%03d]%s", minutes, seconds, millis, lineText))
            }
        }.trim().takeIf { it.isNotBlank() }
    }

    private suspend fun fetchPlainLyrics(trackId: Long): String? {
        val text = signedGet { token ->
            "$BASE_URL/track.lyrics.get?app_id=$APP_ID&track_id=$trackId" +
                "&usertoken=${URLEncoder.encode(token, "UTF-8")}"
        }.bodyAsText()
        return messageBody(text)?.get("lyrics")?.jsonObject?.get("lyrics_body")?.jsonPrimitive?.content
            ?.trim()?.takeIf { it.isNotBlank() }
    }

    suspend fun getLyrics(title: String, artist: String, duration: Int): Result<String> =
        runCatching {
            val best = searchTracks(title, artist).maxByOrNull { score(it, title, artist, duration) }
                ?: throw IllegalStateException("No tracks found on Musixmatch")
            val synced = if (best.hasSubtitles) runCatching { fetchSubtitleLrc(best.id) }.getOrNull() else null
            synced ?: fetchPlainLyrics(best.id)
                ?: throw IllegalStateException("No lyrics available from Musixmatch")
        }
}
