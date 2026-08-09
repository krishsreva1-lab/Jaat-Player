/**
 * Playback resolution — ported from Jaat Player's flat-fallback design.
 *
 * Key structural change vs. the previous jaatplayer version:
 * Instead of classifying each track into normal/explicit/kids/live and giving
 * each category its own short client list (ContentAwareFallbackStrategy), this
 * uses ONE ordered fallback list for every track. No misclassification, no
 * category starved of a working client. Metadata (WEB_REMIX) is fetched
 * separately from stream resolution so a metadata hiccup never blocks playback.
 */

package com.krish.jaatplayer.utils

import android.net.ConnectivityManager
import androidx.media3.common.PlaybackException
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.MOBILE
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.music.innertube.models.YouTubeClient.Companion.WEB
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.response.PlayerResponse
import com.music.innertube.strategy.ContentHints
import com.krish.jaatplayer.constants.AudioQuality
import com.krish.jaatplayer.utils.YTPlayerUtils.MAIN_CLIENT
import com.krish.jaatplayer.utils.YTPlayerUtils.validateStatus
import com.krish.jaatplayer.utils.cipher.CipherDeobfuscator
import com.krish.jaatplayer.utils.potoken.PoTokenGenerator
import com.krish.jaatplayer.utils.potoken.PoTokenResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"

    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    // Track videoIds whose WEB_REMIX stream URL 403'd on the ExoPlayer GET, so the next resolution
    // falls through to the fallback clients instead of skipping HEAD validation and looping.
    // Kept for MusicService compatibility; the flat fallback list below makes this less load-bearing
    // than before, but MusicService still calls these so we keep them functional.
    private val webRemixFailedIds = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    fun markWebRemixFailed(videoId: String) {
        webRemixFailedIds.add(videoId)
    }

    fun clearWebRemixFailures() {
        webRemixFailedIds.clear()
    }

    // MAIN_CLIENT resolves the stream itself. ANDROID_VR needs no PoToken/cipher solving at all,
    // which is why it's robust without login. 1.65.10 is the newest VR build defined in
    // YouTubeClient.kt — DO NOT use 1.43.32, YouTube has deprecated that client version and it
    // now returns "Video unavailable" / UNPLAYABLE for every request regardless of content.
    private val MAIN_CLIENT: YouTubeClient = ANDROID_VR_1_65_10

    // METADATA_CLIENT is used only for richer metadata/tracking when logged in — it never gates
    // whether playback succeeds.
    private val METADATA_CLIENT: YouTubeClient = WEB_REMIX

    // Single flat, ordered fallback list tried for EVERY track — no content-type branching, no
    // category can end up starved of a working client.
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        ANDROID_VR_1_61_48,
        WEB_REMIX,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        TVHTML5,
        ANDROID_CREATOR,
        IPADOS,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        IOS,
        WEB,
        WEB_CREATOR,
    )

    /** Client names disabled by the user in Settings → Stream sources. Updated reactively by MusicService. */
    @Volatile
    var disabledStreamClients: Set<String> = emptySet()

    private const val POTOKEN_WARMUP_VIDEO_ID = "jNQXAC9IVRw"

    suspend fun prewarmPoToken() {
        val sessionId = YouTube.visitorData ?: return
        if (!MAIN_CLIENT.useWebPoTokens) return
        runCatching {
            withContext(Dispatchers.IO) {
                poTokenGenerator.getWebClientPoToken(POTOKEN_WARMUP_VIDEO_ID, sessionId)
            }
        }.onFailure { Timber.tag(TAG).w(it, "PoToken prewarm skipped: ${it.message}") }
    }

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val streamClient: String = "unknown",
    )

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        context: android.content.Context? = null,
        knownArtist: String? = null,
        knownTitle: String? = null,
        knownDurationMs: Long? = null,
        isDownload: Boolean = false,
        contentHints: ContentHints = ContentHints(), // kept for call-site compatibility; unused by
                                                       // the flat fallback list (that's the point —
                                                       // no more misclassification-starved lists)
    ): Result<PlaybackData> {
        suspend fun tryOnce(): Result<PlaybackData> =
            resolvePlaybackData(videoId, playlistId, audioQuality, connectivityManager, context, knownArtist, knownTitle)

        val firstAttempt = tryOnce()
        if (firstAttempt.isFailure && YouTube.cookie == null) {
            Timber.tag(TAG).w("Playback failed for guest. Rotating session and retrying...")
            BotDetectionMitigator.rotateGuestSession()
            val retryResult = tryOnce()
            retryResult.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
            return retryResult
        }
        firstAttempt.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
        return firstAttempt
    }

    private suspend fun resolvePlaybackData(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        context: android.content.Context? = null,
        knownArtist: String? = null,
        knownTitle: String? = null,
    ): Result<PlaybackData> = runCatching {
        Timber.tag(logTag).d("Fetching player response for videoId: $videoId, playlistId: $playlistId")

        val isLoggedIn = YouTube.cookie != null

        val signatureTimestamp = getSignatureTimestampOrNull(videoId)

        var poToken: PoTokenResult? = null
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            try {
                poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "PoToken generation failed: ${e.message}")
            }
        }

        Timber.tag(logTag).d("Attempting MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        var mainPlayerResponse = YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp.timestamp, poToken?.playerRequestPoToken).getOrThrow()

        var metadataResponse: PlayerResponse? = null
        if (isLoggedIn) {
            try {
                var metaPoToken: PoTokenResult? = null
                val metaSessionId = YouTube.dataSyncId
                if (METADATA_CLIENT.useWebPoTokens && metaSessionId != null) {
                    try {
                        metaPoToken = poTokenGenerator.getWebClientPoToken(videoId, metaSessionId)
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "Metadata PoToken generation failed")
                    }
                }
                metadataResponse = YouTube.player(
                    videoId, playlistId, METADATA_CLIENT,
                    signatureTimestamp.timestamp, metaPoToken?.playerRequestPoToken,
                ).getOrNull()
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "Failed to fetch metadata from METADATA_CLIENT")
            }
        }

        var usedAgeRestrictedClient: YouTubeClient? = null
        val mainStatus = mainPlayerResponse.playabilityStatus.status
        val wasOriginallyAgeRestricted = mainStatus in listOf(
            "AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED", "CONTENT_CHECK_REQUIRED",
        ) || (mainStatus == "LOGIN_REQUIRED" && mainPlayerResponse.playabilityStatus.reason?.contains("age", ignoreCase = true) == true)

        if (wasOriginallyAgeRestricted && isLoggedIn) {
            val creatorResponse = YouTube.player(videoId, playlistId, WEB_CREATOR, null, null)
                .onFailure { Timber.tag(logTag).e(it, "player() request FAILED for WEB_CREATOR") }
                .getOrNull()
            if (creatorResponse?.playabilityStatus?.status == "OK") {
                mainPlayerResponse = creatorResponse
                usedAgeRestrictedClient = WEB_CREATOR
            }
        }

        val audioConfig = metadataResponse?.playerConfig?.audioConfig ?: mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = metadataResponse?.videoDetails ?: mainPlayerResponse.videoDetails
        val playbackTracking = metadataResponse?.playbackTracking ?: mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        var streamClientName = "unknown"
        val retryMainPlayerResponse: PlayerResponse? = if (usedAgeRestrictedClient != null) mainPlayerResponse else null

        val currentStatus = mainPlayerResponse.playabilityStatus.status
        val isPrivateTrack = mainPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"
        val needsFallback = currentStatus in listOf(
            "AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED", "CONTENT_CHECK_REQUIRED", "UNPLAYABLE", "LOGIN_REQUIRED",
        )

        val startIndex = when {
            isPrivateTrack -> 0
            needsFallback -> 0
            else -> -1
        }

        val availableClients = STREAM_FALLBACK_CLIENTS.filterIndexed { idx, client ->
            client.clientName !in disabledStreamClients || idx == 0
        }.ifEmpty { STREAM_FALLBACK_CLIENTS.toList() }

        for (clientIndex in (startIndex until availableClients.size)) {
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            val client: YouTubeClient
            if (clientIndex == -1) {
                client = MAIN_CLIENT
                streamPlayerResponse = retryMainPlayerResponse ?: mainPlayerResponse
                streamClientName = usedAgeRestrictedClient?.clientName ?: MAIN_CLIENT.clientName
            } else {
                client = availableClients[clientIndex]
                if (client.clientName in disabledStreamClients) continue
                if (client.loginRequired && !isLoggedIn) continue

                if (client.useWebPoTokens && poToken == null && sessionId != null) {
                    try {
                        poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "Lazy PoToken generation failed")
                    }
                }

                val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null
                val clientSigTimestamp = if (wasOriginallyAgeRestricted) null else signatureTimestamp.timestamp
                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, clientSigTimestamp, clientPoToken)
                        .onFailure { Timber.tag(logTag).e(it, "player() request FAILED for %s", client.clientName) }
                        .getOrNull()
                streamClientName = client.clientName
            }

            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                format = findFormat(streamPlayerResponse, audioQuality, connectivityManager)
                if (format == null) continue

                streamUrl = findUrlOrNull(format, videoId, streamPlayerResponse, skipNewPipe = wasOriginallyAgeRestricted)
                if (streamUrl == null) continue

                val currentClient = if (clientIndex == -1) (usedAgeRestrictedClient ?: MAIN_CLIENT) else availableClients[clientIndex]
                val isPrivatelyOwned = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                if (currentClient.useWebPoTokens) {
                    try {
                        val transformed = CipherDeobfuscator.transformNParamInUrl(streamUrl!!)
                        if (transformed != streamUrl) streamUrl = transformed
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "N-transform failed: ${e.message}")
                    }
                }

                if (currentClient.useWebPoTokens && poToken?.streamingDataPoToken != null) {
                    val separator = if ("?" in streamUrl!!) "&" else "?"
                    streamUrl = "${streamUrl}${separator}pot=${poToken.streamingDataPoToken}"
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) continue

                if (clientIndex == availableClients.size - 1 || isPrivatelyOwned) {
                    break
                }

                if (validateStatus(streamUrl!!)) {
                    break
                } else if (currentClient.useWebPoTokens) {
                    try {
                        val nTransformed = CipherDeobfuscator.transformNParamInUrl(streamUrl!!)
                        if (nTransformed != streamUrl && validateStatus(nTransformed)) {
                            streamUrl = nTransformed
                            break
                        }
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "Cipher n-transform re-validation error")
                    }
                }
            } else {
                val status = streamPlayerResponse?.playabilityStatus?.status ?: "Unknown"
                val reason = streamPlayerResponse?.playabilityStatus?.reason ?: "No reason"
                Timber.tag(logTag).d("Client ${client.clientName} not OK: $status, reason: $reason")
            }
        }

        if (streamPlayerResponse == null) throw Exception("Bad stream player response")
        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            throw PlaybackException(streamPlayerResponse.playabilityStatus.reason, null, PlaybackException.ERROR_CODE_REMOTE_ERROR)
        }
        if (streamExpiresInSeconds == null) throw Exception("Missing stream expire time")
        if (format == null) throw Exception("Could not find format")
        if (streamUrl == null) throw Exception("Could not find stream url")

        PlaybackData(audioConfig, videoDetails, playbackTracking, format, streamUrl, streamExpiresInSeconds, streamClientName)
    }.onFailure { e ->
        Timber.tag(logTag).e(e, "Playback resolution failed")
    }

    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        return YouTube.player(videoId, playlistId, client = WEB_REMIX)
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? {
        return playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal }
            ?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.OPUS, AudioQuality.LOSSLESS -> 1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0)
            }
    }

    private fun validateStatus(url: String): Boolean {
        try {
            val requestBuilder = okhttp3.Request.Builder()
                .head()
                .url(url)
                .header("User-Agent", YouTubeClient.USER_AGENT_WEB)
            YouTube.cookie?.let { cookie -> requestBuilder.addHeader("Cookie", cookie) }
            val response = httpClient.newCall(requestBuilder.build()).execute()
            return response.isSuccessful
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
        }
        return false
    }

    data class SignatureTimestampResult(val timestamp: Int?, val isAgeRestricted: Boolean)

    private fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
            onSuccess = { timestamp -> SignatureTimestampResult(timestamp, isAgeRestricted = false) },
            onFailure = { error ->
                val isAgeRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true
                SignatureTimestampResult(null, isAgeRestricted)
            },
        )
    }

    suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false,
    ): String? {
        if (!format.url.isNullOrEmpty()) return format.url

        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (customDeobfuscatedUrl != null) return customDeobfuscatedUrl
        }

        if (skipNewPipe) return null

        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) return deobfuscatedUrl

        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) return streamUrl

            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any { it.itag == urlPair.first && it.isAudio } == true
            }?.second
            if (audioStream != null) return audioStream
        }

        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        Timber.tag(logTag).d("Force refreshing for videoId: $videoId")
    }
}
