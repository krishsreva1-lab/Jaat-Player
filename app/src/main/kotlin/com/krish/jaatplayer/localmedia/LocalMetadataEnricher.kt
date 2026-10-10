package com.krish.jaatplayer.localmedia

import android.content.Context
import com.krish.jaatplayer.db.MusicDatabase
import com.krish.jaatplayer.db.entities.AlbumEntity
import com.krish.jaatplayer.db.entities.ArtistEntity
import com.krish.jaatplayer.db.entities.Song
import com.krish.jaatplayer.db.entities.SongAlbumMap
import com.krish.jaatplayer.db.entities.SongArtistMap
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMetadataEnricher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .build()
    }

    private val artworkDir: File
        get() = File(context.filesDir, "local_artwork").apply {
            if (!exists()) mkdirs()
        }

    suspend fun enrichAllLocalSongs(
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val localSongs = database.localSongs().firstOrNull() ?: emptyList()
        val candidateSongs = localSongs.filter { song ->
            val thumb = song.song.thumbnailUrl
            thumb.isNullOrBlank() || !hasValidLocalOrOnlineArt(thumb)
        }

        if (candidateSongs.isEmpty()) {
            onProgress?.invoke(0, 0)
            return@withContext 0
        }

        var enrichedCount = 0
        candidateSongs.forEachIndexed { index, song ->
            val success = enrichSingleSong(song)
            if (success) enrichedCount++
            onProgress?.invoke(index + 1, candidateSongs.size)
        }
        return@withContext enrichedCount
    }

    private fun hasValidLocalOrOnlineArt(thumb: String): Boolean {
        if (thumb.startsWith("file://")) {
            val file = File(thumb.removePrefix("file://"))
            if (file.exists() && file.length() > 0) return true
        }
        if (thumb.startsWith("http://") || thumb.startsWith("https://")) {
            return true
        }
        return false
    }

    suspend fun enrichSingleSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        val cleanTitle = cleanSongTitle(song.song.title)
        val knownArtist = song.artists.firstOrNull()?.name?.takeIf {
            !it.contains("unknown", ignoreCase = true) && !it.contains("<unknown>", ignoreCase = true)
        }
        val searchQuery = if (knownArtist != null) "$cleanTitle $knownArtist" else cleanTitle

        try {
            val searchResult = YouTube.search(searchQuery, filter = YouTube.SearchFilter.FILTER_SONG).getOrNull()
            val matchedSong = searchResult?.items?.filterIsInstance<SongItem>()?.firstOrNull() ?: return@withContext false

            val highResArtUrl = matchedSong.thumbnail
            val savedLocalArtFile = downloadAndSaveArtwork(song.song.id, highResArtUrl)
            val localArtUri = savedLocalArtFile?.let { "file://${it.absolutePath}" } ?: highResArtUrl

            database.query {
                val matchedArtistName = matchedSong.artists.firstOrNull()?.name ?: knownArtist
                val matchedArtistId = matchedSong.artists.firstOrNull()?.id ?: song.artists.firstOrNull()?.id

                if (matchedArtistName != null) {
                    val artistId = matchedArtistId ?: "LA_${matchedArtistName.hashCode()}"
                    insert(
                        ArtistEntity(
                            id = artistId,
                            name = matchedArtistName,
                            thumbnailUrl = localArtUri,
                            channelId = null,
                            lastUpdateTime = LocalDateTime.now(),
                            isLocal = true,
                        )
                    )
                    deleteSongArtistMaps(song.song.id)
                    insert(
                        SongArtistMap(
                            songId = song.song.id,
                            artistId = artistId,
                            position = 0,
                        )
                    )
                }

                val matchedAlbumName = matchedSong.album?.name ?: song.album?.title
                val matchedAlbumId = matchedSong.album?.id ?: song.album?.id

                if (matchedAlbumName != null) {
                    val albumId = matchedAlbumId ?: "LAL_${matchedAlbumName.hashCode()}"
                    insert(
                        AlbumEntity(
                            id = albumId,
                            playlistId = null,
                            title = matchedAlbumName,
                            year = song.album?.year,
                            thumbnailUrl = localArtUri,
                            songCount = song.album?.songCount ?: 1,
                            duration = song.album?.duration ?: song.song.duration,
                            explicit = false,
                            lastUpdateTime = LocalDateTime.now(),
                            isLocal = true,
                        )
                    )
                    deleteSongAlbumMaps(song.song.id)
                    insert(
                        SongAlbumMap(
                            songId = song.song.id,
                            albumId = albumId,
                            index = 0,
                        )
                    )
                }

                val updatedSong = song.song.copy(
                    title = if (song.song.title.contains(".mp3", ignoreCase = true) || song.song.title.contains(".m4a", ignoreCase = true)) matchedSong.title else song.song.title,
                    thumbnailUrl = localArtUri,
                    albumId = matchedAlbumId ?: song.song.albumId,
                    albumName = matchedAlbumName ?: song.song.albumName,
                )
                update(updatedSong)
            }
            Timber.tag(TAG).d("Successfully enriched metadata and cached artwork for local song: %s", song.song.id)
            return@withContext true
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to enrich local metadata for song %s", song.song.id)
            return@withContext false
        }
    }

    private fun cleanSongTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("(?i)\\.(mp3|m4a|flac|wav|ogg|aac|wma)$"), "")
            .replace(Regex("[_\\-]+"), " ")
            .replace(Regex("(?i)(official video|official audio|lyric video|hd|4k|audio|video)"), "")
            .trim()
    }

    private fun downloadAndSaveArtwork(songId: String, imageUrl: String): File? {
        try {
            val sanitizedId = songId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val destinationFile = File(artworkDir, "$sanitizedId.jpg")
            val request = Request.Builder().url(imageUrl).build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.bytes()?.let { bytes ->
                    destinationFile.writeBytes(bytes)
                    return destinationFile
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed downloading artwork image for %s", songId)
        }
        return null
    }

    companion object {
        private const val TAG = "LocalMetadataEnricher"
    }
}
