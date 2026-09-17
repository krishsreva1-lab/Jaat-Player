package com.krish.jaatplayer.ai

import android.content.Context
import com.krish.jaatplayer.constants.PreferredArtistsKey
import com.krish.jaatplayer.constants.PreferredLanguagesKey
import com.krish.jaatplayer.db.MusicDatabase
import com.krish.jaatplayer.db.entities.Song
import com.krish.jaatplayer.utils.dataStore
import com.krish.jaatplayer.utils.get
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.pages.HomePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * Single, unified engine behind the home screen's three hero sections and Speed Dial.
 *
 * Design (per what was asked for):
 * - Hero "Favorites": your setup choices (artists/language) PLUS anything you've favorited
 *   in-app since (liked songs, followed/liked artists) — an ongoing signal, not just a
 *   first-launch-only fallback.
 * - Hero "Taste": weighted toward whichever artist you've actually been playing most
 *   recently (e.g. lots of Diljit plays -> more Diljit + similar new songs), not just a
 *   single generic "related to last song" call.
 * - Hero "Queue": built directly from your current playback queue — no network call needed,
 *   updates instantly whenever the queue changes (that part lives in HomeScreen.kt, since it
 *   needs the live PlayerConnection).
 * - Speed Dial: a combined pool of Favorites + Taste + AI Recommendation output — explicitly
 *   NOT fed by Keep Listening, and the heroes above don't read FROM Speed Dial either — songs
 *   only flow one way, hero sections -> Speed Dial.
 * - YouTube's own home feed contributes to Favorites/Taste too (see [extractSongsFromHomePage]),
 *   broadened to pull from every shelf instead of requiring one shelf be 100% songs.
 */
object JaatAlgorithm {

    /**
     * YouTube's home feed is almost entirely shelves of mixes/playlists/albums, not raw song
     * shelves — the old code required an entire shelf to be songs, which came up empty almost
     * every time. This just collects any SongItem found across EVERY shelf instead.
     */
    fun extractSongsFromHomePage(homePage: HomePage?, limit: Int = 12): List<SongItem> {
        if (homePage == null) return emptyList()
        return homePage.sections
            .flatMap { section -> section.items }
            .filterIsInstance<SongItem>()
            .distinctBy { it.id }
            .take(limit)
    }

    private fun Song.toSongItem() = SongItem(
        id = id,
        title = title,
        artists = artists.map { com.music.innertube.models.Artist(name = it.name, id = it.id) },
        thumbnail = thumbnailUrl ?: "",
        explicit = false
    )

    /**
     * Hero "Favorites" — combines setup-time choices with ongoing in-app favorites, so this
     * keeps working even if setup was skipped or preferences change over time.
     */
    suspend fun buildFavoritesBasedSongs(
        context: Context,
        database: MusicDatabase,
        homePage: HomePage?,
        perArtist: Int = 4,
    ): List<SongItem> = supervisorScope {
        val preferredArtistIds = context.dataStore.get(PreferredArtistsKey, emptySet<String>())
        val preferredLanguages = context.dataStore.get(PreferredLanguagesKey, emptySet<String>())

        // "Any song or artist made favorite in the app" — not just setup choices.
        val likedSongs = runCatching { database.likedSongsByCreateDateAsc().first() }.getOrDefault(emptyList())
        val likedArtistIdsFromSongs = likedSongs.flatMap { it.artists }.map { it.id }
        val followedArtistIds = runCatching { database.artistsBookmarkedByCreateDateAsc().first() }
            .getOrDefault(emptyList())
            .map { it.id }

        val allArtistIds = (preferredArtistIds + likedArtistIdsFromSongs + followedArtistIds)
            .distinct()
            .shuffled() // otherwise setup artists (added first) would always crowd out
            // in-app-favorited ones whenever there are more than 12 combined
            .take(12) // enough variety without firing dozens of network calls

        val results = Collections.synchronizedList(mutableListOf<SongItem>())

        val artistJobs = allArtistIds.map { artistId ->
            launch(Dispatchers.IO) {
                YouTube.artist(artistId).onSuccess { page ->
                    page.sections.firstOrNull { it.items.any { item -> item is SongItem } }
                        ?.items?.filterIsInstance<SongItem>()?.take(perArtist)?.let { results.addAll(it) }
                }
            }
        }
        val languageJobs = preferredLanguages.map { language ->
            launch(Dispatchers.IO) {
                YouTube.search("$language songs", filter = YouTube.SearchFilter.FILTER_SONG).onSuccess { page ->
                    results.addAll(page.items.filterIsInstance<SongItem>().take(perArtist))
                }
            }
        }
        (artistJobs + languageJobs).forEach { it.join() }

        // YouTube's own home feed, broadened (see extractSongsFromHomePage).
        results.addAll(extractSongsFromHomePage(homePage, limit = 8))

        // Genuinely nothing configured or favorited anywhere, and the home feed had nothing
        // song-shaped either — one genre-neutral fallback so this isn't just empty forever.
        if (results.isEmpty()) {
            YouTube.search("Global Top 50", filter = YouTube.SearchFilter.FILTER_SONG).onSuccess { page ->
                results.addAll(page.items.filterIsInstance<SongItem>().take(10))
            }
        }

        results.toList().distinctBy { it.id }.shuffled()
    }

    /**
     * Hero "Taste" — weighted toward whatever artist dominates your recent listening, plus
     * genuinely new songs similar to the single most recent play.
     */
    suspend fun buildTasteBasedSongs(
        database: MusicDatabase,
        homePage: HomePage?,
        recentEventLimit: Int = 20,
        perDominantArtist: Int = 8,
    ): List<SongItem> = supervisorScope {
        val recentEvents = runCatching { database.events().first() }.getOrDefault(emptyList()).take(recentEventLimit)
        if (recentEvents.isEmpty()) return@supervisorScope emptyList()

        val results = Collections.synchronizedList(mutableListOf<SongItem>())

        // Which artist have you actually been playing the most lately? ("played Diljit songs"
        // -> show more Diljit, not just generically "similar" songs.)
        val dominantArtist = recentEvents
            .flatMap { it.song.artists }
            .groupingBy { it.id to it.name }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        val mostRecentSong = recentEvents.first().song

        launch(Dispatchers.IO) {
            if (dominantArtist != null) {
                YouTube.artist(dominantArtist.first).onSuccess { page ->
                    page.sections.firstOrNull { it.items.any { item -> item is SongItem } }
                        ?.items?.filterIsInstance<SongItem>()
                        ?.filter { it.id != mostRecentSong.id }
                        ?.take(perDominantArtist)
                        ?.let { results.addAll(it) }
                }
            }
        }

        launch(Dispatchers.IO) {
            // New, genuinely different songs similar to the single most recent play — same
            // engine as the app's own "play similar songs next" radio feature.
            val endpoint = YouTube.next(
                WatchEndpoint(videoId = mostRecentSong.id, playlistId = "RDAMVM${mostRecentSong.id}")
            ).getOrNull()?.relatedEndpoint
            if (endpoint != null) {
                YouTube.related(endpoint).onSuccess { page ->
                    results.addAll(
                        page.songs
                            .filter { it.id != mostRecentSong.id }
                            .shuffled()
                            .take(perDominantArtist)
                    )
                }
            }
        }

        results.addAll(extractSongsFromHomePage(homePage, limit = 6))

        results.toList().distinctBy { it.id }.shuffled()
    }

    /** Combined pool for Speed Dial — Favorites + Taste + AI Recommendation, NEVER Keep Listening. */
    fun buildSpeedDialPool(
        favoritesBasedSongs: List<SongItem>,
        tasteBasedSongs: List<SongItem>,
        queueBasedSongs: List<SongItem>,
        aiRecommendedSongs: List<Song>,
    ): List<YTItem> {
        val aiAsSongItems = aiRecommendedSongs.map { it.toSongItem() }
        return (favoritesBasedSongs + tasteBasedSongs + queueBasedSongs + aiAsSongItems)
            .distinctBy { it.id }
            .shuffled()
    }
}
