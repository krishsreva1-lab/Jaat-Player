

package com.krish.jaatplayer.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage
import com.music.innertube.utils.completed
import com.krish.jaatplayer.constants.HideExplicitKey
import com.krish.jaatplayer.constants.HideVideoSongsKey
import com.krish.jaatplayer.constants.HideYoutubeShortsKey
import com.krish.jaatplayer.constants.InnerTubeCookieKey
import com.krish.jaatplayer.constants.PreferredArtistsKey
import com.krish.jaatplayer.constants.PreferredLanguagesKey
import com.krish.jaatplayer.constants.QuickPicks
import com.krish.jaatplayer.constants.QuickPicksKey
import com.krish.jaatplayer.db.MusicDatabase
import com.krish.jaatplayer.db.entities.Album
import com.krish.jaatplayer.db.entities.LocalItem
import com.krish.jaatplayer.db.entities.Song
import com.krish.jaatplayer.db.entities.SpeedDialItem
import com.krish.jaatplayer.extensions.filterVideoSongs
import com.krish.jaatplayer.extensions.toEnum
import com.krish.jaatplayer.models.SimilarRecommendation
import com.krish.jaatplayer.utils.SyncUtils
import com.krish.jaatplayer.utils.dataStore
import com.krish.jaatplayer.utils.get
import com.krish.jaatplayer.utils.reportException
import com.krish.jaatplayer.models.toMediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

data class DailyDiscoverItem(
    val seed: YTItem,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)

data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isRandomizing = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val dailyDiscover = MutableStateFlow<List<DailyDiscoverItem>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val tasteRecommendations = MutableStateFlow<List<YTItem>>(emptyList())
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    val aiRecommendedPlaylist = database.playlistsByNameAsc()
        .map { playlists -> playlists.find { it.playlist.name == "Recommended by AI" } }
        .flatMapLatest { playlist -> 
            if (playlist != null && playlist.songCount > 0) {
                database.playlistSongs(playlist.playlist.id).map { playlistSongs -> 
                    playlist to playlistSongs.map { it.song }
                }
            } else {
                flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    val speedDialItems: StateFlow<List<YTItem>> =
        combine(
            database.speedDialDao.getAll(),
            keepListening,
            quickPicks
        ) { pinned, keepListening, quick ->
            val pinnedItems = pinned.map { it.toYTItem() }
            val filled = pinnedItems.toMutableList()
            val targetSize = 27

            if (filled.size < targetSize) {
                
                keepListening?.let { k ->
                    val needed = targetSize - filled.size
                    val available = k.filter { item ->
                        filled.none { p -> p.id == item.id }
                    }.mapNotNull { item ->
                        when (item) {
                            is Song -> SongItem(
                                id = item.id,
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                thumbnail = item.thumbnailUrl ?: "",
                                explicit = false
                            )
                            is Album -> AlbumItem(
                                browseId = item.id,
                                playlistId = item.album.playlistId ?: "",
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                year = item.album.year,
                                thumbnail = item.thumbnailUrl ?: ""
                            )
                            else -> null
                        }
                    }
                    filled.addAll(available.take(needed))
                }
            }

            if (filled.size < targetSize) {
                
                quick?.let { q ->
                    val needed = targetSize - filled.size
                    val available = q.filter { song ->
                        filled.none { p -> p.id == song.id }
                    }.map { song ->
                        SongItem(
                            id = song.id,
                            title = song.title,
                            artists = song.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = song.thumbnailUrl ?: "",
                            explicit = false
                        )
                    }
                    filled.addAll(available.take(needed))
                }
            }

            filled.take(targetSize)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun getRandomItem(): YTItem? {
        try {
            isRandomizing.value = true
            
            kotlinx.coroutines.delay(1000)

            val userSongs = mutableListOf<YTItem>()
            val otherSources = mutableListOf<YTItem>()

            quickPicks.value?.let { songs ->
                userSongs.addAll(songs.map { song ->
                    SongItem(
                        id = song.id,
                        title = song.title,
                        artists = song.artists.map { Artist(name = it.name, id = it.id) },
                        thumbnail = song.thumbnailUrl ?: "",
                        explicit = false
                    )
                })
            }

            keepListening.value?.let { items ->
                items.forEach { item ->
                    when (item) {
                        is Song -> userSongs.add(SongItem(
                            id = item.id,
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = item.thumbnailUrl ?: "",
                            explicit = false
                        ))
                        is Album -> otherSources.add(AlbumItem(
                            browseId = item.id,
                            playlistId = item.album.playlistId ?: "",
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            year = item.album.year,
                            thumbnail = item.thumbnailUrl ?: ""
                        ))
                        else -> {}
                    }
                }
            }

            otherSources.addAll(allYtItems.value)

            
            val item = if (userSongs.isNotEmpty() && (otherSources.isEmpty() || Random.nextFloat() < 0.8f)) {
                userSongs.distinctBy { it.id }.shuffled().firstOrNull()
            } else {
                otherSources.distinctBy { it.id }.shuffled().firstOrNull()
            } ?: userSongs.firstOrNull() ?: otherSources.firstOrNull()

            return item
        } finally {
            isRandomizing.value = false
        }
    }

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    fun togglePin(item: YTItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val speedDialItem = SpeedDialItem.fromYTItem(item)
            val isPinned = database.speedDialDao.isPinned(speedDialItem.id).first()
            if (isPinned) {
                database.speedDialDao.delete(speedDialItem.id)
            } else {
                database.speedDialDao.insert(speedDialItem)
            }
        }
    }
    
    private var lastProcessedCookie: String? = null
    
    private var isProcessingAccountData = false

    private suspend fun getDailyDiscover() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val recentlyPlayed = database.events().first().take(10).map { it.song }.distinctBy { it.id }
        val likedSongs = database.likedSongsByCreateDateAsc().first()
        val preferredLanguages = context.dataStore.get(PreferredLanguagesKey, emptySet<String>())
        val preferredArtists = context.dataStore.get(PreferredArtistsKey, emptySet<String>())

        // Previously this seeded only from likedSongs, so a song you'd merely played (not
        // explicitly liked) never influenced this section — it fell straight through to generic
        // "Global Top 50" style queries. Recently PLAYED songs now take priority, same signal
        // getTasteRecommendations() already uses, so this personalizes from the very first song
        // you play, not just from songs you've liked.
        val seeds = if (recentlyPlayed.isNotEmpty()) {
            recentlyPlayed.take(1).map { song ->
                SongItem(
                    id = song.id,
                    title = song.title,
                    artists = song.artists.map { com.music.innertube.models.Artist(name = it.name, id = it.id) },
                    thumbnail = song.thumbnailUrl ?: "",
                    explicit = false
                )
            }
        } else if (likedSongs.isEmpty() && preferredArtists.isEmpty()) {
            // No liked songs and no preferred artists, use languages or default trending
            val trendingQueries = if (preferredLanguages.isNotEmpty()) {
                preferredLanguages.map { "$it latest hits" }
            } else {
                listOf("Global Top 50", "Trending Music English", "New Punjabi Songs", "Top Hindi Hits")
            }
            val fallbackSeeds = java.util.Collections.synchronizedList(mutableListOf<YTItem>())
            coroutineScope {
                trendingQueries.map { query ->
                    launch(Dispatchers.IO) {
                        YouTube.search(query, filter = YouTube.SearchFilter.FILTER_SONG).onSuccess { page ->
                            fallbackSeeds.addAll(page.items.filterIsInstance<SongItem>().take(2))
                        }
                    }
                }.forEach { it.join() }
            }
            fallbackSeeds.toList().shuffled()
        } else if (likedSongs.isEmpty()) {
            // Use preferred artists as seeds
            val artistSeeds = java.util.Collections.synchronizedList(mutableListOf<YTItem>())
            coroutineScope {
                preferredArtists.take(10).map { artistId ->
                    launch(Dispatchers.IO) {
                        YouTube.artist(artistId).onSuccess { page ->
                            page.sections.firstOrNull { it.items.any { it is SongItem } }?.items?.filterIsInstance<SongItem>()?.take(2)?.let {
                                artistSeeds.addAll(it)
                            }
                        }
                    }
                }.forEach { it.join() }
            }
            artistSeeds.toList().shuffled()
        } else {
            likedSongs.shuffled().distinctBy { it.id }.take(10).map { song ->
                SongItem(
                    id = song.id,
                    title = song.title,
                    artists = song.artists.map { com.music.innertube.models.Artist(name = it.name, id = it.id) },
                    thumbnail = song.thumbnailUrl ?: "",
                    explicit = false
                )
            }
        }

        val singleSeed = seeds.firstOrNull()
        val items = java.util.Collections.synchronizedList(mutableListOf<DailyDiscoverItem>())

        if (singleSeed != null) {
            suspend fun attempt(): Boolean {
                val endpoint = YouTube.next(WatchEndpoint(videoId = singleSeed.id)).getOrNull()?.relatedEndpoint
                    ?: return false
                val page = YouTube.related(endpoint).getOrNull() ?: return false
                val recommendations = page.songs
                    .filter { item ->
                        if (hideVideoSongs && item.isVideoSong) return@filter false
                        if (item.explicit) return@filter false
                        item.id != singleSeed.id
                    }
                    .shuffled()

                // One related-songs page from a single seed easily has enough songs for a whole
                // hero carousel — no need for several seeds just to get enough items.
                recommendations.forEach { recommendation ->
                    items.add(
                        DailyDiscoverItem(
                            seed = singleSeed,
                            recommendation = recommendation,
                            relatedEndpoint = endpoint
                        )
                    )
                }
                return recommendations.isNotEmpty()
            }

            // Mirrors the same bot-detection recovery already used for playback resolution
            // and related-song caching — retry once with a fresh guest session before giving up.
            if (!attempt()) {
                runCatching { com.krish.jaatplayer.utils.BotDetectionMitigator.rotateGuestSession() }
                attempt()
            }
        }

        dailyDiscover.value = items.toList().distinctBy { it.recommendation.id }.shuffled()
    }

    private suspend fun getTasteRecommendations() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val preferredLanguages = context.dataStore.get(PreferredLanguagesKey, emptySet<String>())
        
        val recentEvents = database.events().first()
        val recommendations = java.util.Collections.synchronizedList(mutableListOf<YTItem>())

        // Same YouTube.next()+related() call the app's own "play similar songs next" radio
        // feature uses for a single song — this needs only ONE song's history, not a built-up
        // pool of several, so it works the moment you've played anything at all.
        val seed = recentEvents.firstOrNull()?.song
        if (seed != null) {
            suspend fun attempt(): Boolean {
                val nextResult = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()
                val relatedEndpoint = nextResult?.relatedEndpoint ?: return false
                val page = YouTube.related(relatedEndpoint).getOrNull() ?: return false
                val items = page.songs
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs)
                    .filter { it.id != seed.id }
                recommendations.addAll(items)
                return items.isNotEmpty()
            }

            // Mirrors the same bot-detection recovery already used for playback resolution.
            if (!attempt()) {
                runCatching { com.krish.jaatplayer.utils.BotDetectionMitigator.rotateGuestSession() }
                attempt()
            }
        } else {
            // Fallback Diverse Pool (Punjabi, English, Hindi, Trending, Global)
            val queries = if (preferredLanguages.isNotEmpty()) {
                preferredLanguages.map { "$it latest songs 2026" }
            } else {
                listOf(
                    "Punjabi latest songs 2026", 
                    "Billboard Hot 100", 
                    "Global Viral Hits", 
                    "New English Pop", 
                    "Top Trending Music India",
                    "Coke Studio Pakistan",
                    "UK Top 40",
                    "Latin Music Hits"
                )
            }
            coroutineScope {
                queries.shuffled().take(5).map { query ->
                    launch(Dispatchers.IO) {
                        YouTube.search(query, filter = YouTube.SearchFilter.FILTER_SONG).onSuccess { page ->
                            recommendations.addAll(page.items.filterIsInstance<SongItem>().take(6))
                        }
                    }
                }.forEach { it.join() }
            }
        }
        
        tasteRecommendations.value = recommendations.toList().distinctBy { it.id }.shuffled()
    }

    private suspend fun getQuickPicks() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val relatedSongs = database.quickPicks().first().filterVideoSongs(hideVideoSongs)
                val forgotten = database.forgottenFavorites().first().filterVideoSongs(hideVideoSongs).take(8)

                
                val recentSong = database.events().first().firstOrNull()?.song
                val ytSimilarSongs = mutableListOf<Song>()

                if (recentSong != null) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = recentSong.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            
                            page.songs.take(10).forEach { ytSong ->
                                database.song(ytSong.id).first()?.let { localSong ->
                                    if (!hideVideoSongs || !localSong.song.isVideo) {
                                        ytSimilarSongs.add(localSong)
                                    }
                                }
                            }
                        }
                    }
                }

                
                // forgotten used to be mixed in at equal weight with relatedSongs/ytSimilarSongs
                // and the whole thing shuffled — when the fresh, taste-based signal (relatedSongs/
                // ytSimilarSongs) was thin, old long-unplayed songs from "forgotten" could end up
                // dominating the shuffle, which is why old/default-feeling songs kept surfacing
                // here even with plenty of recent listening history. Recent taste now gets priority
                // and "forgotten" is capped to a small minority of the final list.
                val recentTasteBased = (relatedSongs + ytSimilarSongs).distinctBy { it.id }.shuffled()
                val combined = (recentTasteBased.take(16) + forgotten.shuffled().take(4))
                    .distinctBy { it.id }
                    .shuffled()

                quickPicks.value = combined.ifEmpty { relatedSongs.shuffled().take(20) }
            }
            QuickPicks.LAST_LISTEN -> {
                val song = database.events().first().firstOrNull()?.song
                if (song != null && database.hasRelatedSongs(song.id)) {
                    quickPicks.value = database.getRelatedSongs(song.id).first().filterVideoSongs(hideVideoSongs).shuffled().take(20)
                }
            }
        }
    }

    private suspend fun getCommunityPlaylists() {
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 4
        val preferredArtists = context.dataStore.get(PreferredArtistsKey, emptySet<String>())

        val artistSeeds = (database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .map { it.id } + preferredArtists)
            .distinct()
            .shuffled().take(3)
        val songSeeds = database.mostPlayedSongs(fromTimeStamp, limit = 5).first()
            .shuffled().take(2)

        val candidatePlaylists = java.util.Collections.synchronizedList(mutableListOf<PlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            artistSeeds.map { artistId ->
                launch(Dispatchers.IO) {
                    YouTube.artist(artistId).onSuccess { page ->
                        page.sections.forEach { section ->
                            section.items.filterIsInstance<PlaylistItem>().forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" &&
                                    playlist.author?.name != "YouTube" &&
                                    playlist.author?.name != "Playlist" &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }

            songSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            page.playlists.forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" &&
                                    playlist.author?.name != "YouTube" &&
                                    playlist.author?.name != "Playlist" &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
        }

        val uniqueCandidates = candidatePlaylists.distinctBy { it.id }.shuffled().take(5)

        val playlists = java.util.Collections.synchronizedList(mutableListOf<CommunityPlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            uniqueCandidates.map { playlist ->
                launch(Dispatchers.IO) {
                    YouTube.playlist(playlist.id).onSuccess { page ->
                        val songs = page.songs.take(10)
                        if (songs.isNotEmpty()) {
                            
                            val songCountText = page.playlist.songCountText ?: playlist.songCountText
                            val updatedPlaylist = playlist.copy(songCountText = songCountText)
                            playlists.add(CommunityPlaylistItem(updatedPlaylist, songs))
                        }
                    }
                }
            }.forEach { it.join() }
        }

        communityPlaylists.value = playlists.shuffled()
    }

    
    private suspend fun loadLocalDataPhase() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)

        getQuickPicks()

        forgottenFavorites.value = database.forgottenFavorites().first()
            .filterVideoSongs(hideVideoSongs).shuffled().take(20)

        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 2
        val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5).first()
            .filterVideoSongs(hideVideoSongs).shuffled().take(10)
        val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2).first()
            .filter { it.album.thumbnailUrl != null }.shuffled().take(5)
        val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp).first()
            .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
        keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()

        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album }
    }

    
    private suspend fun loadSimilarRecommendations() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 2

        coroutineScope {
            val artistDeferreds = database.mostPlayedArtists(fromTimeStamp, limit = 15).first()
                .filter { it.artist.isYouTubeArtist }
                .shuffled().take(4)
                .map { artist ->
                    async(Dispatchers.IO) {
                        val items = mutableListOf<YTItem>()
                        YouTube.artist(artist.id).onSuccess { page ->
                            page.sections.takeLast(3).forEach { section -> items += section.items }
                        }
                        SimilarRecommendation(
                            title = artist,
                            items = items
                                .distinctBy { item -> item.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .take(12)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val songDeferreds = database.mostPlayedSongs(fromTimeStamp, limit = 15).first()
                .filter { it.album != null }
                .shuffled().take(3)
                .map { song ->
                    async(Dispatchers.IO) {
                        val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                            ?: return@async null
                        val page = YouTube.related(endpoint).getOrNull() ?: return@async null
                        SimilarRecommendation(
                            title = song,
                            items = (page.songs.shuffled().take(10) +
                                    page.albums.shuffled().take(5) +
                                    page.artists.shuffled().take(3) +
                                    page.playlists.shuffled().take(3))
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val albumDeferreds = database.mostPlayedAlbums(fromTimeStamp, limit = 10).first()
                .filter { it.album.thumbnailUrl != null }
                .shuffled().take(2)
                .map { album ->
                    async(Dispatchers.IO) {
                        val items = mutableListOf<YTItem>()
                        YouTube.album(album.id).onSuccess { page ->
                            page.otherVersions.let { items += it }
                        }
                        album.artists.firstOrNull()?.id?.let { artistId ->
                            YouTube.artist(artistId).onSuccess { page ->
                                page.sections.lastOrNull()?.items?.let { items += it }
                            }
                        }
                        SimilarRecommendation(
                            title = album,
                            items = items
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .take(10)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val results = (artistDeferreds + songDeferreds + albumDeferreds).awaitAll()
            similarRecommendations.value = results.filterNotNull().shuffled()
        }
    }

    
    private suspend fun loadNetworkDataPhase() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        // supervisorScope, not coroutineScope: these fetches are independent. With plain
        // coroutineScope, ANY one of these throwing (community playlists, similar recommendations,
        // etc.) cancels every sibling immediately — including all three hero fetches, even mid-retry.
        // That structured-concurrency cancellation, not bot detection, was silently taking down
        // all three heroes together.
        supervisorScope {
            launch(Dispatchers.IO) { runCatching { getDailyDiscover() }.onFailure { reportException(it) } }
            launch(Dispatchers.IO) { runCatching { getCommunityPlaylists() }.onFailure { reportException(it) } }
            launch(Dispatchers.IO) { runCatching { loadSimilarRecommendations() }.onFailure { reportException(it) } }
            launch(Dispatchers.IO) { runCatching { getTasteRecommendations() }.onFailure { reportException(it) } }
            launch(Dispatchers.IO) {
                runCatching {
                    fun applyHomePage(page: com.music.innertube.pages.HomePage) {
                        homePage.value = page.copy(
                            sections = page.sections.mapNotNull { section ->
                                val filteredItems = section.items
                                    .filterExplicit(hideExplicit)
                                    .filterVideoSongs(hideVideoSongs)
                                    .filterYoutubeShorts(hideYoutubeShorts)
                                if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                            }
                        )
                    }

                    var result = YouTube.home(params = selectedChip.value?.endpoint?.params)
                    if (result.isFailure) {
                        // Mirrors the same bot-detection recovery already used for playback resolution
                        // and related-song caching — retry once with a fresh guest session before giving up.
                        runCatching { com.krish.jaatplayer.utils.BotDetectionMitigator.rotateGuestSession() }
                        result = YouTube.home(params = selectedChip.value?.endpoint?.params)
                    }
                    result.onSuccess { page -> applyHomePage(page) }
                }.onFailure { reportException(it) }
            }
            launch(Dispatchers.IO) {
                runCatching {
                    YouTube.explore().onSuccess { page ->
                        explorePage.value = page.copy(
                            newReleaseAlbums = page.newReleaseAlbums.filterExplicit(hideExplicit)
                        )
                    }.onFailure { reportException(it) }
                }.onFailure { reportException(it) }
            }
            if (YouTube.cookie != null) {
                launch(Dispatchers.IO) { runCatching { loadAccountPlaylists() }.onFailure { reportException(it) } }
            }
        }

        
        allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value?.sections?.flatMap { it.items }.orEmpty()
    }

    private suspend fun load() {
        isLoading.value = true

        // Each phase is isolated so a failure in one (e.g. a local database query) can never
        // silently prevent the other from running — previously an uncaught exception in
        // loadLocalDataPhase() would kill this whole coroutine before loadNetworkDataPhase()
        // (which feeds all three hero sections) ever got a chance to execute.
        runCatching { loadLocalDataPhase() }.onFailure { reportException(it) }
        isLoading.value = false

        runCatching { loadNetworkDataPhase() }.onFailure { reportException(it) }
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            var currentContinuation = continuation
            var hasNewItems = false

            while (currentContinuation != null && !hasNewItems) {
                val nextSections = YouTube.home(currentContinuation).getOrNull() ?: break
                currentContinuation = nextSections.continuation

                val newSections = nextSections.sections.mapNotNull { section ->
                    val filteredItems = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts)
                    if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                }

                if (newSections.isNotEmpty()) {
                    hasNewItems = true
                }

                homePage.value = nextSections.copy(
                    chips = homePage.value?.chips,
                    continuation = currentContinuation,
                    sections = homePage.value?.sections.orEmpty() + newSections
                )
            }
            _isLoadingMore.value = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            previousHomePage.value = homePage.value
        }

        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            val nextSections = YouTube.home(params = chip.endpoint?.params).getOrNull() ?: return@launch

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = nextSections.sections.map { section ->
                    section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                }
            )
            selectedChip.value = chip
        }
    }

    private suspend fun loadAccountPlaylists() {
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
            accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>()
                .filterNot { it.id == "SE" }
                .filterYoutubeShorts(hideYoutubeShorts)
        }.onFailure {
            reportException(it)
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                isRefreshing.value = true
                load()
            } finally {
                isRefreshing.value = false
            }
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }
    }

    init {

        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .first()

            load()
        }

        
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }

        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .collect { cookie ->
                    
                    if (isProcessingAccountData) return@collect

                    
                    lastProcessedCookie = cookie
                    isProcessingAccountData = true

                    try {
                        if (cookie != null && cookie.isNotEmpty()) {

                            
                            YouTube.cookie = cookie

                            
                            YouTube.accountInfo().onSuccess { info ->
                                accountName.value = info.name
                                accountImageUrl.value = info.thumbnailUrl
                            }.onFailure {
                                reportException(it)
                            }
                        } else {
                            accountName.value = "Guest"
                            accountImageUrl.value = null
                            accountPlaylists.value = null
                        }
                    } finally {
                        isProcessingAccountData = false
                    }
                }
        }

        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[HideYoutubeShortsKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    if (YouTube.cookie != null && accountPlaylists.value != null) {
                        loadAccountPlaylists()
                    }
                }
        }
    }
}
