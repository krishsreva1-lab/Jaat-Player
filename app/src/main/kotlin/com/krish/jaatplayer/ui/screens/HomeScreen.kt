package com.krish.jaatplayer.ui.screens
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import com.krish.jaatplayer.playback.PlayerConnection
import com.krish.jaatplayer.ui.component.MenuState
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import com.krish.jaatplayer.ui.component.JaatLoadingIndicator
import com.krish.jaatplayer.ui.theme.bbhBartle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.request.ImageRequest
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.utils.completed
import com.music.innertube.utils.parseCookieString
import com.music.innertube.YouTube
import com.krish.jaatplayer.constants.GridItemSize
import com.krish.jaatplayer.constants.GridItemsSizeKey
import com.krish.jaatplayer.constants.GridThumbnailHeight
import com.krish.jaatplayer.constants.InnerTubeCookieKey
import com.krish.jaatplayer.constants.ListItemHeight
import com.krish.jaatplayer.constants.ListThumbnailSize
import com.krish.jaatplayer.constants.RandomizeHomeOrderKey
import com.krish.jaatplayer.constants.ShowSpeedDialKey
import com.krish.jaatplayer.constants.SmallGridThumbnailHeight
import com.krish.jaatplayer.constants.ThumbnailCornerRadius
import com.krish.jaatplayer.db.entities.Album
import com.krish.jaatplayer.db.entities.Artist
import com.krish.jaatplayer.db.entities.LocalItem
import com.krish.jaatplayer.db.entities.Playlist
import com.krish.jaatplayer.db.entities.PlaylistEntity
import com.krish.jaatplayer.db.entities.PlaylistSongMap
import com.krish.jaatplayer.db.entities.Song
import com.krish.jaatplayer.extensions.toMediaItem
import com.krish.jaatplayer.LocalDatabase
import com.krish.jaatplayer.LocalPlayerAwareWindowInsets
import com.krish.jaatplayer.LocalPlayerConnection
import com.krish.jaatplayer.models.toMediaMetadata
import com.krish.jaatplayer.playback.queues.ListQueue
import com.krish.jaatplayer.playback.queues.LocalAlbumRadio
import com.krish.jaatplayer.playback.queues.YouTubeAlbumRadio
import com.krish.jaatplayer.playback.queues.YouTubeQueue
import com.krish.jaatplayer.R
import com.krish.jaatplayer.ui.component.AlbumGridItem
import com.krish.jaatplayer.ui.component.ArtistGridItem
import com.krish.jaatplayer.ui.component.ChipsRow
import com.krish.jaatplayer.ui.component.HideOnScrollFAB
import com.krish.jaatplayer.ui.component.LocalBottomSheetPageState
import com.krish.jaatplayer.ui.component.LocalMenuState
import com.krish.jaatplayer.ui.component.NavigationTitle
import com.krish.jaatplayer.ui.component.PlaylistGridItem
import com.krish.jaatplayer.ui.component.RandomizeGridItem
import com.krish.jaatplayer.ui.component.shimmer.GridItemPlaceHolder
import com.krish.jaatplayer.ui.component.shimmer.ShimmerHost
import com.krish.jaatplayer.ui.component.shimmer.TextPlaceholder
import com.krish.jaatplayer.ui.component.SongGridItem
import com.krish.jaatplayer.ui.component.SongListItem
import com.krish.jaatplayer.ui.component.SpeedDialGridItem
import com.krish.jaatplayer.ui.component.YouTubeGridItem
import com.krish.jaatplayer.ui.component.YouTubeListItem
import com.krish.jaatplayer.ui.menu.AlbumMenu
import com.krish.jaatplayer.ui.menu.ArtistMenu
import com.krish.jaatplayer.ui.menu.PlaylistMenu
import com.krish.jaatplayer.ui.menu.SongMenu
import com.krish.jaatplayer.ui.menu.YouTubeAlbumMenu
import com.krish.jaatplayer.ui.menu.YouTubeArtistMenu
import com.krish.jaatplayer.ui.menu.YouTubePlaylistMenu
import com.krish.jaatplayer.ui.menu.YouTubeSongMenu
import com.krish.jaatplayer.ui.utils.SnapLayoutInfoProvider
import com.krish.jaatplayer.ui.utils.resize
import com.krish.jaatplayer.utils.listItemShape
import com.krish.jaatplayer.utils.rememberEnumPreference
import com.krish.jaatplayer.utils.rememberPreference
import com.krish.jaatplayer.viewmodels.CommunityPlaylistItem
import com.krish.jaatplayer.viewmodels.HomeViewModel
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krish.jaatplayer.viewmodels.DailyDiscoverItem

private fun NavController.navigateToPlaylistItem(playlist: PlaylistItem) {
    when (val playlistId = playlist.id.removePrefix("VL")) {
        "LM" -> navigate("auto_playlist/liked")
        "SE" -> navigate("auto_playlist/downloaded")
        else -> navigate("online_playlist/$playlistId")
    }
}

sealed class HomeSection(val id: String, val baseWeight: Int) {
    data object HeroBanner : HomeSection("hero_banner", 2000)
    data object TasteHero : HomeSection("taste_hero", 2000)
    data object DiscoveryHero : HomeSection("discovery_hero", 2000)
    data object SpeedDial : HomeSection("speed_dial", 1000)
    data object AiRecommendations : HomeSection("ai_recommendations", 950)
    data object QuickPicks : HomeSection("quick_picks", 900)
    data object DailyDiscover : HomeSection("daily_discover", 800)
    data object KeepListening : HomeSection("keep_listening", 950)
    data object AccountPlaylists : HomeSection("account_playlists", 300)
    data object ForgottenFavorites : HomeSection("forgotten_favorites", 250)
    data object FromTheCommunity : HomeSection("from_the_community", 450)
    data class SimilarRecommendation(val index: Int) : HomeSection("similar_recommendation_$index", 600)
    data class HomePageSection(val index: Int) : HomeSection("home_page_section_$index", 550)
    data object MoodAndGenres : HomeSection("mood_and_genres", 10)
}

@Composable
fun CommunityPlaylistCard(
    item: CommunityPlaylistItem,
    onClick: () -> Unit,
    onSongClick: (SongItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val dbPlaylist by database.playlistByBrowseId(item.playlist.id).collectAsState(initial = null)
    val isBookmarked = dbPlaylist?.playlist?.bookmarkedAt != null

    Card(
        modifier = modifier
            .width(320.dp)
            .height(420.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(28.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(
                                model = item.songs.getOrNull(0)?.thumbnail?.resize(544, 544),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                            AsyncImage(
                                model = item.songs.getOrNull(1)?.thumbnail?.resize(544, 544),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(
                                model = item.songs.getOrNull(2)?.thumbnail?.resize(544, 544),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                            AsyncImage(
                                model = item.songs.getOrNull(3)?.thumbnail?.resize(544, 544),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.playlist.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.playlist.author?.name ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                item.songs.take(3).forEach { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(onClick = { onSongClick(song) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = song.thumbnail.resize(544, 544),
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artists.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                IconButton(
                    onClick = {
                        item.playlist.playEndpoint?.let {
                            playerConnection?.playQueue(YouTubeQueue(it))
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_widget_play),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = {
                        item.playlist.radioEndpoint?.let {
                            playerConnection?.playQueue(YouTubeQueue(it))
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            if (dbPlaylist?.playlist == null) {
                                database.transaction {
                                    val playlistEntity = PlaylistEntity(
                                        name = item.playlist.title,
                                        browseId = item.playlist.id,
                                        thumbnailUrl = item.playlist.thumbnail,
                                        remoteSongCount = item.playlist.songCountText?.split(" ")?.firstOrNull()?.toIntOrNull(),
                                        playEndpointParams = item.playlist.playEndpoint?.params,
                                        shuffleEndpointParams = item.playlist.shuffleEndpoint?.params,
                                        radioEndpointParams = item.playlist.radioEndpoint?.params
                                    ).toggleLike()
                                    insert(playlistEntity)
                                    scope.launch(Dispatchers.IO) {
                                        item.songs.ifEmpty {
                                            YouTube.playlist(item.playlist.id).completed()
                                                .getOrNull()?.songs.orEmpty()
                                        }.map { it.toMediaMetadata() }
                                            .onEach(::insert)
                                            .mapIndexed { index, song ->
                                                PlaylistSongMap(
                                                    songId = song.id,
                                                    playlistId = playlistEntity.id,
                                                    position = index,
                                                    setVideoId = song.setVideoId
                                                )
                                            }
                                            .forEach(::insert)
                                    }
                                }
                            } else {
                                database.transaction {
                                    val currentPlaylist = dbPlaylist!!.playlist
                                    update(currentPlaylist.toggleLike())
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        painter = painterResource(if (isBookmarked) R.drawable.library_add_check else R.drawable.library_add),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DailyDiscoverCard(
    dailyDiscover: com.krish.jaatplayer.viewmodels.DailyDiscoverItem,
    onClick: () -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val database = LocalDatabase.current
    val playCount by database.getLifetimePlayCount(dailyDiscover.recommendation.id).collectAsState(initial = 0)
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    val song = dailyDiscover.recommendation as? SongItem
    val playsString = stringResource(R.string.plays)

    Card(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (song != null) {
                        menuState.show {
                            YouTubeSongMenu(
                                song = song,
                                navController = navController,
                                onDismiss = { menuState.dismiss() }
                            )
                        }
                    }
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(dailyDiscover.recommendation.thumbnail?.resize(1200, 1200))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
            )

            if (maxWidth > 200.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Black.copy(alpha = 0.9f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = dailyDiscover.recommendation.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = buildString {
                                append((dailyDiscover.recommendation as? SongItem)?.artists?.joinToString(", ") { it.name } ?: "")
                                if (playCount > 0) {
                                    append(" • $playCount $playsString")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    val messages = listOf(
                        R.string.daily_discover_sounds_like,
                        R.string.daily_discover_because_you_listen_to,
                        R.string.daily_discover_similar_to,
                        R.string.daily_discover_based_on,
                        R.string.daily_discover_for_fans_of
                    )
                    val messageRes = remember(dailyDiscover.seed.id) {
                        messages[kotlin.math.abs(dailyDiscover.seed.id.hashCode()) % messages.size]
                    }

                    val seedSubTitle = when (val seed = dailyDiscover.seed) {
                        is SongItem -> seed.artists.joinToString(", ") { it.name }
                        is AlbumItem -> seed.artists?.joinToString(", ") { it.name } ?: ""
                        else -> ""
                    }

                    Text(
                        text = stringResource(messageRes, "${dailyDiscover.seed.title}${if (seedSubTitle.isNotEmpty()) " • $seedSubTitle" else ""}"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeHeroCarousel(
    items: List<YTItem>,
    designType: String,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: com.krish.jaatplayer.ui.component.MenuState,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    scope: kotlinx.coroutines.CoroutineScope,
    title: String? = null
) {
    Column {
        if (title != null) {
            NavigationTitle(
                title = title,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        when (designType) {
            "A" -> {
                HorizontalCenteredHeroCarousel(
                    state = rememberCarouselState { items.size },
                    maxItemWidth = 260.dp,
                    itemSpacing = 8.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) { index ->
                    HomeHeroItem(
                        item = items[index],
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                        scope = scope
                    )
                }
            }
            "B" -> {
                // Design B: 3D Stack Carousel
                val pagerState = rememberPagerState { items.size }
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 80.dp),
                    pageSpacing = (-32).dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) { page ->
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                    
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                val scale = lerp(
                                    start = 0.82f,
                                    stop = 1f,
                                    fraction = 1f - pageOffset.coerceIn(0f, 1f)
                                )
                                scaleX = scale
                                scaleY = scale
                                alpha = lerp(
                                    start = 0.4f,
                                    stop = 1f,
                                    fraction = 1f - pageOffset.coerceIn(0f, 1f)
                                )
                                rotationY = lerp(
                                    start = 0f,
                                    stop = if (pagerState.currentPage > page) 20f else -20f,
                                    fraction = pageOffset.coerceIn(0f, 1f)
                                )
                                translationX = if (pagerState.currentPage > page) 40f * pageOffset else -40f * pageOffset
                            }
                    ) {
                        HeroItemWrapper {
                            HomeHeroItem(
                                item = items[page],
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                                scope = scope
                            )
                        }
                    }
                }
            }
            "C" -> {
                // Design C: Square Panoramic Carousel
                val pagerState = rememberPagerState { items.size }
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    pageSpacing = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) { page ->
                    HeroItemWrapper {
                        HomeHeroItem(
                            item = items[page],
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                            scope = scope
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroItemWrapper(content: @Composable CarouselItemScope.() -> Unit) {
    // This is a dummy wrapper that provides CarouselItemScope
    HorizontalCenteredHeroCarousel(
        state = rememberCarouselState { 1 },
        modifier = Modifier.fillMaxSize(),
        content = { content() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarouselItemScope.HomeHeroItem(
    item: YTItem,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: com.krish.jaatplayer.ui.component.MenuState,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .maskClip(shape)
            .maskBorder(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape
            )
            .combinedClickable(
                onClick = {
                    when (item) {
                        is SongItem -> playerConnection.playQueue(
                            YouTubeQueue(
                                item.endpoint ?: WatchEndpoint(videoId = item.id),
                                item.toMediaMetadata()
                            )
                        )
                        is AlbumItem -> navController.navigate("album/${item.id}")
                        is ArtistItem -> navController.navigate("artist/${item.id}")
                        is PlaylistItem -> navController.navigateToPlaylistItem(item)
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    menuState.show {
                        when (item) {
                            is SongItem -> YouTubeSongMenu(
                                song = item,
                                navController = navController,
                                onDismiss = menuState::dismiss
                            )
                            is AlbumItem -> YouTubeAlbumMenu(
                                albumItem = item,
                                navController = navController,
                                onDismiss = menuState::dismiss
                            )
                            is ArtistItem -> YouTubeArtistMenu(
                                artist = item,
                                onDismiss = menuState::dismiss
                            )
                            is PlaylistItem -> YouTubePlaylistMenu(
                                playlist = item,
                                coroutineScope = scope,
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                }
            )
    ) {
        AsyncImage(
            model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(item.thumbnail?.resize(1200, 1200))
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f)
                        )
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = when(item) {
                    is SongItem -> item.title
                    is AlbumItem -> item.title
                    is ArtistItem -> item.title
                    is PlaylistItem -> item.title
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when(item) {
                    is SongItem -> item.artists.joinToString { it.name }
                    is AlbumItem -> item.artists?.joinToString { it.name } ?: ""
                    is ArtistItem -> stringResource(R.string.artist)
                    is PlaylistItem -> item.author?.name ?: ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val quickPicks by viewModel.quickPicks.collectAsState()
    val aiRecommendedPlaylist by viewModel.aiRecommendedPlaylist.collectAsState()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val similarRecommendations by viewModel.similarRecommendations.collectAsState()
    val accountPlaylists by viewModel.accountPlaylists.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val explorePage by viewModel.explorePage.collectAsState()
    val dailyDiscover by viewModel.dailyDiscover.collectAsState()
    val communityPlaylists by viewModel.communityPlaylists.collectAsState()
    val tasteRecommendations by viewModel.tasteRecommendations.collectAsState()

    val allLocalItems by viewModel.allLocalItems.collectAsState()
    val allYtItems by viewModel.allYtItems.collectAsState()
    val speedDialItems by viewModel.speedDialItems.collectAsState()
    val selectedChip by viewModel.selectedChip.collectAsState()

    val isLoading: Boolean by viewModel.isLoading.collectAsState()
    val isMoodAndGenresLoading = isLoading && explorePage?.moodAndGenres == null
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isRandomizing by viewModel.isRandomizing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val quickPicksLazyGridState = rememberLazyGridState()
    val forgottenFavoritesLazyGridState = rememberLazyGridState()

    val accountName by viewModel.accountName.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val (randomizeHomeOrder) = rememberPreference(RandomizeHomeOrderKey, false)
    val (showSpeedDial) = rememberPreference(ShowSpeedDialKey, true)


    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val url = if (isLoggedIn) accountImageUrl else null

    val scope = rememberCoroutineScope()
    
    var randomizeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val lazylistState = rememberLazyListState()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    val currentGridHeight = if (gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()


    var randomSeed by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            randomSeed = System.currentTimeMillis()
        }
    }

    val foundInSettings = stringResource(R.string.found_in_settings_content)

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lazylistState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val len = lazylistState.layoutInfo.totalItemsCount
                if (lastVisibleIndex != null && lastVisibleIndex >= len - 3) {
                    viewModel.loadMoreYouTubeItems(homePage?.continuation)
                }
            }
    }

    NetworkReload(
        onReload = viewModel::refresh
    )

    if (selectedChip != null) {
        BackHandler {
            
            viewModel.toggleChip(selectedChip)
        }
    }

    val localGridItem: @Composable (LocalItem) -> Unit = {
        when (it) {
            is Song -> SongGridItem(
                song = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (it.id == mediaMetadata?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    YouTubeQueue.radio(it.toMediaMetadata()),
                                )
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                SongMenu(
                                    originalSong = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
                isActive = it.id == mediaMetadata?.id,
                isPlaying = isPlaying,
            )

            is Album -> AlbumGridItem(
                album = it,
                isActive = it.id == mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                coroutineScope = scope,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            navController.navigate("album/${it.id}")
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                AlbumMenu(
                                    originalAlbum = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
            )

            is Artist -> ArtistGridItem(
                artist = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            navController.navigate("artist/${it.id}")
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                ArtistMenu(
                                    originalArtist = it,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
            )

            is Playlist -> PlaylistGridItem(
                playlist = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            navController.navigate("local_playlist/${it.id}")
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                PlaylistMenu(
                                    playlist = it,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
            )
        }
    }

    val ytGridItem: @Composable (YTItem) -> Unit = { item ->
        YouTubeGridItem(
            item = item,
            isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
            isPlaying = isPlaying,
            coroutineScope = scope,
            thumbnailRatio = 1f,
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        when (item) {
                            is SongItem -> playerConnection.playQueue(
                                YouTubeQueue(
                                    item.endpoint ?: WatchEndpoint(
                                        videoId = item.id
                                    ), item.toMediaMetadata()
                                )
                            )

                            is AlbumItem -> navController.navigate("album/${item.id}")
                            is ArtistItem -> navController.navigate("artist/${item.id}")
                            is PlaylistItem -> navController.navigateToPlaylistItem(item)
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            when (item) {
                                is SongItem -> YouTubeSongMenu(
                                    song = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )

                                is AlbumItem -> YouTubeAlbumMenu(
                                    albumItem = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )

                                is ArtistItem -> YouTubeArtistMenu(
                                    artist = item,
                                    onDismiss = menuState::dismiss
                                )

                                is PlaylistItem -> YouTubePlaylistMenu(
                                    playlist = item,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    }
                )
        )
    }

    val homeSections = remember(
        randomizeHomeOrder,
        randomSeed,
        speedDialItems,
        quickPicks,
        dailyDiscover,
        keepListening,
        accountPlaylists,
        forgottenFavorites,
        communityPlaylists,
        similarRecommendations,
        homePage?.sections,
        explorePage?.moodAndGenres,
        aiRecommendedPlaylist,
        tasteRecommendations
    ) {
        val list = mutableListOf<HomeSection>()

        if (homePage?.sections?.isNotEmpty() == true || !quickPicks.isNullOrEmpty()) list.add(HomeSection.HeroBanner)
        if (tasteRecommendations.isNotEmpty() || keepListening?.any { it is Song } == true || !quickPicks.isNullOrEmpty()) list.add(HomeSection.TasteHero)
        if (dailyDiscover?.isNotEmpty() == true || !forgottenFavorites.isNullOrEmpty() || !quickPicks.isNullOrEmpty()) list.add(HomeSection.DiscoveryHero)
        
        if (showSpeedDial && speedDialItems.isNotEmpty()) list.add(HomeSection.SpeedDial)
        if (aiRecommendedPlaylist != null && aiRecommendedPlaylist!!.second.isNotEmpty()) list.add(HomeSection.AiRecommendations)
        if (quickPicks?.isNotEmpty() == true) list.add(HomeSection.QuickPicks)
        if (communityPlaylists?.isNotEmpty() == true) list.add(HomeSection.FromTheCommunity)
        if (dailyDiscover?.isNotEmpty() == true) list.add(HomeSection.DailyDiscover)
        if (keepListening?.isNotEmpty() == true) list.add(HomeSection.KeepListening)
        if (accountPlaylists?.isNotEmpty() == true) list.add(HomeSection.AccountPlaylists)
        if (forgottenFavorites?.isNotEmpty() == true) list.add(HomeSection.ForgottenFavorites)

        similarRecommendations?.indices?.forEach { i ->
            list.add(HomeSection.SimilarRecommendation(i))
        }

        homePage?.sections?.indices?.drop(if (homePage?.sections?.isNotEmpty() == true) 1 else 0)?.forEach { i ->
            list.add(HomeSection.HomePageSection(i))
        }

        if (explorePage?.moodAndGenres != null) list.add(HomeSection.MoodAndGenres)

        val defaultOrder = mutableMapOf(
            HomeSection.AiRecommendations to 850,
            HomeSection.FromTheCommunity to 450,
            HomeSection.DailyDiscover to 400,
            HomeSection.AccountPlaylists to 300,
            HomeSection.ForgottenFavorites to 250,
            HomeSection.MoodAndGenres to 10
        )

        // 3-way Hero swapping logic
        val heroList = listOf(HomeSection.HeroBanner, HomeSection.TasteHero, HomeSection.DiscoveryHero)
        val shuffledHeroes = heroList.shuffled(Random(randomSeed))
        val h1 = shuffledHeroes[0]
        val h2 = shuffledHeroes[1]
        val h3 = shuffledHeroes[2]

        // Design shuffling logic
        val designPool = listOf("A", "B", "C").shuffled(Random(randomSeed))
        val heroToDesign = mapOf(
            h1 to designPool[0],
            h2 to designPool[1],
            h3 to designPool[2]
        )

        list.sortedByDescending { section ->
            if (randomizeHomeOrder) {
                val sectionRandom = Random(randomSeed + section.id.hashCode())
                val base = when (section) {
                    h1 -> 2000
                    HomeSection.SpeedDial -> 1000
                    h2 -> 975
                    HomeSection.KeepListening -> 950
                    h3 -> 925
                    HomeSection.QuickPicks -> 900
                    HomeSection.DailyDiscover -> 500
                    HomeSection.AccountPlaylists,
                    HomeSection.ForgottenFavorites,
                    HomeSection.FromTheCommunity -> 400
                    else -> 600
                }
                base + sectionRandom.nextInt(-20, 20)
            } else {
                when (section) {
                    h1 -> 2000
                    HomeSection.SpeedDial -> 1000
                    h2 -> 975
                    HomeSection.KeepListening -> 950
                    h3 -> 925
                    HomeSection.QuickPicks -> 900
                    is HomeSection.SimilarRecommendation -> 600 - section.index
                    is HomeSection.HomePageSection -> 550 - section.index
                    else -> defaultOrder[section] ?: 0
                }
            }
        }.map { it to (heroToDesign[it] ?: "A") }
    }

    LaunchedEffect(quickPicks) {
        quickPicksLazyGridState.scrollToItem(0)
    }

    LaunchedEffect(forgottenFavorites) {
        forgottenFavoritesLazyGridState.scrollToItem(0)
    }

    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        indicator = {
            JaatLoadingIndicator(
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        }
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
            val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor
            val quickPicksSnapLayoutInfoProvider = remember(quickPicksLazyGridState) {
                SnapLayoutInfoProvider(
                    lazyGridState = quickPicksLazyGridState,
                    positionInLayout = { layoutSize, itemSize ->
                        (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                    }
                )
            }
            val forgottenFavoritesSnapLayoutInfoProvider = remember(forgottenFavoritesLazyGridState) {
                SnapLayoutInfoProvider(
                    lazyGridState = forgottenFavoritesLazyGridState,
                    positionInLayout = { layoutSize, itemSize ->
                        (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                    }
                )
            }

            // Hero sections should surface songs you're not already seeing in Speed Dial or
            // Keep Listening, not repeat them.
            val excludedFromHeroIds = remember(speedDialItems, keepListening) {
                (speedDialItems.map { it.id } + keepListening.orEmpty().map { it.id }).toSet()
            }

            LazyColumn(
                state = lazylistState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
            ) {
                item {
                    ChipsRow(
                        chips = homePage?.chips?.filter { 
                            !it.title.equals("Podcasts", ignoreCase = true) && 
                            !it.title.equals("Uploaded", ignoreCase = true)
                        }?.map { it to it.title } ?: emptyList(),
                        currentValue = selectedChip,
                        onValueUpdate = {
                            viewModel.toggleChip(it)
                        }
                    )
                }

                if (isLoading && homePage?.chips.isNullOrEmpty()) {
                    item(key = "chips_shimmer") {
                        ShimmerHost {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                repeat(5) {
                                    TextPlaceholder(
                                        height = 30.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.width(72.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                homeSections.forEach { pair ->
                    val (section, design) = pair
                    when (section) {
                        HomeSection.HeroBanner -> {
                            val heroSection = homePage?.sections?.firstOrNull { 
                                it.items.any { item -> item is SongItem } 
                            } ?: homePage?.sections?.firstOrNull()
                            
                            val homeSongItems = heroSection?.items?.filterIsInstance<SongItem>() ?: emptyList()
                            // YouTube's home feed is mostly playlist/mix/album shelves, not raw song
                            // shelves — this filter comes up empty most of the time regardless of
                            // network success. Fall back to local quick picks (no network dependency,
                            // pure DB query) so this hero reliably has something to show instead of
                            // depending on the home feed happening to contain a song-shaped shelf.
                            val quickPicksAsSongItems = quickPicks.orEmpty().map { song ->
                                SongItem(
                                    id = song.id,
                                    title = song.title,
                                    artists = song.artists.map { com.music.innertube.models.Artist(name = it.name, id = it.id) },
                                    thumbnail = song.thumbnailUrl ?: "",
                                    explicit = false
                                )
                            }
                            val songItems = homeSongItems.ifEmpty { quickPicksAsSongItems }
                                .filterNot { it.id in excludedFromHeroIds }
                            val bannerTitle = if (homeSongItems.isNotEmpty()) heroSection?.title else null
                            
                            if (songItems.isNotEmpty()) {
                                item(key = "hero_banner") {
                                    HomeHeroCarousel(
                                        items = songItems,
                                        designType = design,
                                        navController = navController,
                                        playerConnection = playerConnection,
                                        menuState = menuState,
                                        haptic = haptic,
                                        scope = scope,
                                        title = bannerTitle
                                    )
                                }
                            }
                        }
                        HomeSection.TasteHero -> {
                            val networkSongItems = tasteRecommendations.filterIsInstance<SongItem>()
                            val localFallbackSongItems = keepListening.orEmpty()
                                .filterIsInstance<Song>()
                                .map { song ->
                                    SongItem(
                                        id = song.id,
                                        title = song.title,
                                        artists = song.artists.map { com.music.innertube.models.Artist(name = it.name, id = it.id) },
                                        thumbnail = song.thumbnailUrl ?: "",
                                        explicit = false
                                    )
                                }
                            // Final guaranteed fallback: quickPicks is the same list HeroBanner
                            // falls back to, but reversed so the two don't show an identical
                            // carousel when nothing else has loaded yet — this hero should always
                            // have something once the library has any songs in it at all.
                            val guaranteedFallbackSongItems = quickPicks.orEmpty().reversed().map { song ->
                                SongItem(
                                    id = song.id,
                                    title = song.title,
                                    artists = song.artists.map { com.music.innertube.models.Artist(name = it.name, id = it.id) },
                                    thumbnail = song.thumbnailUrl ?: "",
                                    explicit = false
                                )
                            }
                            val songItems = networkSongItems.ifEmpty { localFallbackSongItems }.ifEmpty { guaranteedFallbackSongItems }
                            if (songItems.isNotEmpty()) {
                                item(key = "taste_hero") {
                                    HomeHeroCarousel(
                                        items = songItems,
                                        designType = design,
                                        navController = navController,
                                        playerConnection = playerConnection,
                                        menuState = menuState,
                                        haptic = haptic,
                                        scope = scope,
                                        title = "Based on your recent taste"
                                    )
                                }
                            }
                        }
                        HomeSection.DiscoveryHero -> {
                            val networkSongItems = dailyDiscover.orEmpty()
                                .map { it.recommendation }
                                .filterIsInstance<SongItem>()
                            val localFallbackSongItems = forgottenFavorites.orEmpty().map { song ->
                                SongItem(
                                    id = song.id,
                                    title = song.title,
                                    artists = song.artists.map { com.music.innertube.models.Artist(name = it.name, id = it.id) },
                                    thumbnail = song.thumbnailUrl ?: "",
                                    explicit = false
                                )
                            }
                            // Final guaranteed fallback, same reasoning as TasteHero above —
                            // shuffled with a fixed different order (dropped/rotated) so all three
                            // heroes don't ever render the exact same carousel.
                            val guaranteedFallbackSongItems = quickPicks.orEmpty().let { list ->
                                if (list.size > 1) list.drop(list.size / 2) + list.take(list.size / 2) else list
                            }.map { song ->
                                SongItem(
                                    id = song.id,
                                    title = song.title,
                                    artists = song.artists.map { com.music.innertube.models.Artist(name = it.name, id = it.id) },
                                    thumbnail = song.thumbnailUrl ?: "",
                                    explicit = false
                                )
                            }
                            val songItems = networkSongItems.ifEmpty { localFallbackSongItems }.ifEmpty { guaranteedFallbackSongItems }
                            if (songItems.isNotEmpty()) {
                                item(key = "discovery_hero") {
                                    HomeHeroCarousel(
                                        items = songItems,
                                        designType = design,
                                        navController = navController,
                                        playerConnection = playerConnection,
                                        menuState = menuState,
                                        haptic = haptic,
                                        scope = scope,
                                        title = "Daily Discovery"
                                    )
                                }
                            }
                        }
                        HomeSection.SpeedDial -> {
                            speedDialItems.takeIf { it.isNotEmpty() }?.let { items ->
                                item(key = "speed_dial_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.speed_dial),
                                        modifier = Modifier.animateItem()
                                    )
                                }

                                item(key = "speed_dial_list") {
                                    val targetItemSize = 160.dp
                                    val availableWidth = maxWidth - 32.dp
                                    val columns = (availableWidth / targetItemSize).toInt().coerceAtLeast(3)
                                    val rows = if (columns >= 6) 1 else if (columns >= 4) 2 else 3
                                    val itemsPerPage = columns * rows
                                    val itemWidth = availableWidth / columns

                                    val pagerState = rememberPagerState(pageCount = { (items.size + itemsPerPage - 1) / itemsPerPage })

                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .animateItem(),
                                    ) {
                                        HorizontalPager(
                                            state = pagerState,
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            pageSpacing = 16.dp,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(itemWidth * rows),
                                        ) { page ->
                                            val pageStartIndex = page * itemsPerPage
                                            val pageItems = items.drop(pageStartIndex).take(itemsPerPage)

                                            Column(modifier = Modifier.fillMaxSize()) {
                                                for (row in 0 until rows) {
                                                    Row(modifier = Modifier.fillMaxWidth()) {
                                                        for (col in 0 until columns) {
                                                            val itemIndex = row * columns + col

                                                            val isRandomizeSlot = (page == 0 && itemIndex == itemsPerPage - 1)

                                                            if (isRandomizeSlot) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .width(itemWidth)
                                                                        .height(itemWidth)
                                                                        .padding(4.dp)
                                                                ) {
                                                                    RandomizeGridItem(
                                                                        isLoading = isRandomizing,
                                                                        onClick = {
                                                                            if (isRandomizing) {
                                                                                randomizeJob?.cancel()
                                                                            } else {
                                                                                randomizeJob = scope.launch {
                                                                                    val randomItem = viewModel.getRandomItem()
                                                                                    if (randomItem != null) {
                                                                                        when (randomItem) {
                                                                                            is SongItem -> playerConnection.playQueue(
                                                                                                YouTubeQueue(
                                                                                                    randomItem.endpoint ?: WatchEndpoint(videoId = randomItem.id),
                                                                                                    randomItem.toMediaMetadata()
                                                                                                )
                                                                                            )
                                                                                            is AlbumItem -> navController.navigate("album/${randomItem.id}")
                                                                                            is ArtistItem -> navController.navigate("artist/${randomItem.id}")
                                                                                            is PlaylistItem -> navController.navigateToPlaylistItem(randomItem)
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    )
                                                                }
                                                            } else if (itemIndex < pageItems.size) {
                                                                val item = pageItems[itemIndex]
                                                                val isPinned by database.speedDialDao.isPinned(item.id).collectAsState(initial = false)

                                                                Box(
                                                                    modifier = Modifier
                                                                        .width(itemWidth)
                                                                        .height(itemWidth)
                                                                        .padding(4.dp)
                                                                ) {
                                                                    SpeedDialGridItem(
                                                                        item = item,
                                                                        isPinned = isPinned,
                                                                        isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
                                                                        isPlaying = isPlaying,
                                                                        modifier = Modifier
                                                                            .fillMaxSize()
                                                                            .combinedClickable(
                                                                                onClick = {
                                                                                    when (item) {
                                                                                        is SongItem -> playerConnection.playQueue(
                                                                                            YouTubeQueue(
                                                                                                item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                                                                item.toMediaMetadata()
                                                                                            )
                                                                                        )
                                                                                        is AlbumItem -> navController.navigate("album/${item.id}")
                                                                                        is ArtistItem -> navController.navigate("artist/${item.id}")

                                                                                        is PlaylistItem -> navController.navigateToPlaylistItem(item)
                                                                                    }
                                                                                },
                                                                                onLongClick = {
                                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                    menuState.show {
                                                                                        when (item) {
                                                                                            is SongItem -> YouTubeSongMenu(
                                                                                                song = item,
                                                                                                navController = navController,
                                                                                                onDismiss = menuState::dismiss
                                                                                            )
                                                                                            is AlbumItem -> YouTubeAlbumMenu(
                                                                                                albumItem = item,
                                                                                                navController = navController,
                                                                                                onDismiss = menuState::dismiss
                                                                                            )
                                                                                            is ArtistItem -> YouTubeArtistMenu(
                                                                                                artist = item,
                                                                                                onDismiss = menuState::dismiss
                                                                                            )
                                                                                            is PlaylistItem -> YouTubePlaylistMenu(
                                                                                                playlist = item,
                                                                                                coroutineScope = scope,
                                                                                                onDismiss = menuState::dismiss
                                                                                            )
                                                                                        }
                                                                                    }
                                                                                }
                                                                            )
                                                                    )
                                                                }
                                                            } else {
                                                                Spacer(modifier = Modifier.width(itemWidth))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (pagerState.pageCount > 1) {
                                            Row(
                                                modifier = Modifier
                                                    .height(24.dp)
                                                    .fillMaxWidth(),
                                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                repeat(pagerState.pageCount) { iteration ->
                                                    val color = if (pagerState.currentPage == iteration)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(4.dp)
                                                            .clip(CircleShape)
                                                            .background(color)
                                                            .size(8.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.AiRecommendations -> {
                            aiRecommendedPlaylist?.let { pair ->
                                val (playlist, songs) = pair
                                item(key = "ai_recommendation_title") {
                                    val lastUpdatedStr = playlist.playlist.lastUpdateTime?.let {
                                        "Last updated: " + it.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, h:mm a"))
                                    }
                                    NavigationTitle(
                                        title = playlist.title,
                                        label = lastUpdatedStr,
                                        onClick = {
                                            navController.navigate("local_playlist/${playlist.id}")
                                        },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                                item(key = "ai_recommendation_list") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.animateItem()
                                    ) {
                                        items(items = songs, key = { it.id }) { songObj ->
                                            localGridItem(songObj)
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.QuickPicks -> {
                            quickPicks?.takeIf { it.isNotEmpty() }?.let { quickPicks ->


                                item(key = "quick_picks_list") {
                                    val distinctQuickPicks = quickPicks.distinctBy { it.id }
                                    HorizontalCenteredHeroCarousel(
                                        state = rememberCarouselState { distinctQuickPicks.size },
                                        maxItemWidth = 250.dp,
                                        itemSpacing = 8.dp,
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(290.dp)
                                            .animateItem()
                                    ) { index ->
                                        val originalSong = distinctQuickPicks[index]
                                        val song by database.song(originalSong.id)
                                            .collectAsState(initial = originalSong)
                                        val isActive = song!!.id == mediaMetadata?.id

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .maskClip(MaterialTheme.shapes.extraLarge)
                                                .maskBorder(
                                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                    MaterialTheme.shapes.extraLarge
                                                )
                                                .focusable()
                                                .combinedClickable(
                                                    onClick = {
                                                        if (isActive) {
                                                            playerConnection.togglePlayPause()
                                                        } else {
                                                            playerConnection.playQueue(YouTubeQueue.radio(song!!.toMediaMetadata()))
                                                        }
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        menuState.show {
                                                            SongMenu(
                                                                originalSong = song!!,
                                                                navController = navController,
                                                                onDismiss = menuState::dismiss
                                                            )
                                                        }
                                                    }
                                                )
                                        ) {
                                            AsyncImage(
                                                model = coil3.request.ImageRequest.Builder(LocalContext.current)
                                                    .data(song!!.thumbnailUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color.Transparent,
                                                                Color.Transparent,
                                                                Color.Black.copy(alpha = 0.7f)
                                                            )
                                                        )
                                                    )
                                            )

                                            if (isActive && isPlaying) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(12.dp)
                                                        .size(32.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.primary,
                                                            CircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.volume_up),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            Column(
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(16.dp)
                                            ) {
                                                Text(
                                                    text = song!!.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = song!!.artists.joinToString { it.name },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.FromTheCommunity -> {
                            communityPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                                item(key = "community_playlists_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.from_the_community),
                                        modifier = Modifier.animateItem()
                                    )
                                }

                                item(key = "community_playlists_content") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier.animateItem()
                                    ) {
                                        items(playlists, key = { it.playlist.id }) { item ->
                                            CommunityPlaylistCard(
                                                item = item,
                                                onClick = {
                                                    navController.navigateToPlaylistItem(item.playlist)
                                                },
                                                onSongClick = { song ->
                                                    playerConnection.playQueue(
                                                        YouTubeQueue(
                                                            song.endpoint ?: WatchEndpoint(videoId = song.id),
                                                            song.toMediaMetadata()
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.DailyDiscover -> {
                            dailyDiscover?.takeIf { it.isNotEmpty() }?.let { discoverList ->
                                
                                item(key = "daily_discover_title") {
                                    val title = stringResource(R.string.your_daily_discover)
                                    NavigationTitle(
                                        title = title,
                                        onPlayAllClick = {
                                            val queueItems = discoverList.mapNotNull {
                                                (it.recommendation as? SongItem)?.toMediaMetadata()
                                            }

                                            if (queueItems.isNotEmpty()) {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = title,
                                                        items = queueItems.map { it.toMediaItem() }
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }
                                item(key = "daily_discover_content") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(340.dp)
                                            .padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val carouselState = rememberCarouselState { discoverList.size }
                                        HorizontalMultiBrowseCarousel(
                                            state = carouselState,
                                            preferredItemWidth = 320.dp,
                                            itemSpacing = 16.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(320.dp)
                                        ) { i ->
                                            val item = discoverList[i]
                                            DailyDiscoverCard(
                                                dailyDiscover = item,
                                                onClick = {
                                                    val song = item.recommendation as? SongItem
                                                    val mediaMetadata = song?.toMediaMetadata()
                                                    if (mediaMetadata != null) {
                                                        playerConnection.playQueue(
                                                            YouTubeQueue(
                                                                song.endpoint ?: WatchEndpoint(videoId = song.id),
                                                                mediaMetadata
                                                            )
                                                        )
                                                    }
                                                },
                                                navController = navController,
                                                modifier = Modifier.maskClip(MaterialTheme.shapes.extraLarge)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.KeepListening -> {
                            keepListening?.takeIf { it.isNotEmpty() }?.let { keepListening ->
                                item(key = "keep_listening_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.keep_listening),
                                        modifier = Modifier.animateItem()
                                    )
                                }

                                item(key = "keep_listening_list") {
                                    val rows = if (keepListening.size > 6) 2 else 1
                                    LazyHorizontalGrid(
                                        state = rememberLazyGridState(),
                                        rows = GridCells.Fixed(rows),
                                        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height((currentGridHeight + with(LocalDensity.current) {
                                                MaterialTheme.typography.bodyLarge.lineHeight.toDp() * 2 +
                                                        MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 2
                                            }) * rows)
                                            .animateItem()
                                    ) {
                                        items(keepListening, key = { it.id }) {
                                            localGridItem(it)
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.AccountPlaylists -> {
                            accountPlaylists?.takeIf { it.isNotEmpty() }?.let { accountPlaylists ->
                                item(key = "account_playlists_title") {
                                    NavigationTitle(
                                        label = stringResource(R.string.your_youtube_playlists),
                                        title = accountName,
                                        thumbnail = {
                                            if (url != null) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(url)
                                                        .diskCachePolicy(CachePolicy.ENABLED)
                                                        .diskCacheKey(url)
                                                        .crossfade(false)
                                                        .build(),
                                                    placeholder = painterResource(id = R.drawable.person),
                                                    error = painterResource(id = R.drawable.person),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(ListThumbnailSize)
                                                        .clip(CircleShape)
                                                )
                                            } else {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.person),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(ListThumbnailSize)
                                                )
                                            }
                                        },
                                        onClick = {
                                            navController.navigate("account")
                                        },
                                        modifier = Modifier.animateItem()
                                    )
                                }

                                item(key = "account_playlists_list") {
                                    LazyRow(
                                        contentPadding = WindowInsets.systemBars
                                            .only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                        modifier = Modifier.animateItem()
                                    ) {
                                        items(
                                            items = accountPlaylists.distinctBy { it.id },
                                            key = { it.id },
                                        ) { item ->
                                            ytGridItem(item)
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.ForgottenFavorites -> {
                            forgottenFavorites?.takeIf { it.isNotEmpty() }?.let { forgottenFavorites ->
                                item(key = "forgotten_favorites_title") {
                                    val forgottenFavoritesTitle = stringResource(R.string.forgotten_favorites)
                                    NavigationTitle(
                                        title = forgottenFavoritesTitle,
                                        modifier = Modifier.animateItem(),
                                        onPlayAllClick = {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = forgottenFavoritesTitle,
                                                    items = forgottenFavorites.distinctBy { it.id }.map { it.toMediaItem() }
                                                )
                                            )
                                        }
                                    )
                                }

                                item(key = "forgotten_favorites_list") {
                                    
                                    val rows = min(4, forgottenFavorites.size)
                                    LazyHorizontalGrid(
                                        state = forgottenFavoritesLazyGridState,
                                        rows = GridCells.Fixed(rows),
                                        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                        flingBehavior = rememberSnapFlingBehavior(
                                            forgottenFavoritesSnapLayoutInfoProvider
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(ListItemHeight * rows)
                                            .animateItem()
                                    ) {
                                        itemsIndexed(
                                            items = forgottenFavorites.distinctBy { it.id },
                                            key = { _, it -> it.id }
                                        ) { index, originalSong ->
                                            val song by database.song(originalSong.id)
                                                .collectAsState(initial = originalSong)

                                            SongListItem(
                                                song = song!!,
                                                showInLibraryIcon = true,
                                                isActive = song!!.id == mediaMetadata?.id,
                                                isPlaying = isPlaying,
                                                isSwipeable = false,
                                                shape = listItemShape(index = index % rows, count = rows),
                                                trailingContent = {
                                                    IconButton(
                                                        onClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            menuState.show {
                                                                SongMenu(
                                                                    originalSong = song!!,
                                                                    navController = navController,
                                                                    onDismiss = menuState::dismiss
                                                                )
                                                            }
                                                        }
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.more_vert),
                                                            contentDescription = null
                                                        )
                                                    }
                                                },
                                                modifier = Modifier
                                                    .width(horizontalLazyGridItemWidth)
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (song!!.id == mediaMetadata?.id) {
                                                                playerConnection.togglePlayPause()
                                                            } else {
                                                                playerConnection.playQueue(
                                                                    YouTubeQueue.radio(
                                                                        song!!.toMediaMetadata()
                                                                    )
                                                                )
                                                            }
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            menuState.show {
                                                                SongMenu(
                                                                    originalSong = song!!,
                                                                    navController = navController,
                                                                    onDismiss = menuState::dismiss
                                                                )
                                                            }
                                                        }
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        is HomeSection.SimilarRecommendation -> {
                            val recommendation = similarRecommendations?.getOrNull(section.index)
                            recommendation?.let {
                                item(key = "similar_to_title_${section.index}") {
                                    NavigationTitle(
                                        label = stringResource(R.string.similar_to),
                                        title = recommendation.title.title,
                                        thumbnail = recommendation.title.thumbnailUrl?.let { thumbnailUrl ->
                                            {
                                                val shape =
                                                    if (recommendation.title is Artist) CircleShape else RoundedCornerShape(
                                                        ThumbnailCornerRadius
                                                    )
                                                AsyncImage(
                                                    model = thumbnailUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(ListThumbnailSize)
                                                        .clip(shape)
                                                )
                                            }
                                        },
                                        onClick = {
                                            when (recommendation.title) {
                                                is Song -> navController.navigate("album/${recommendation.title.album!!.id}")
                                                is Album -> navController.navigate("album/${recommendation.title.id}")
                                                is Artist -> navController.navigate("artist/${recommendation.title.id}")
                                                is Playlist -> {}
                                            }
                                        },
                                        modifier = Modifier.animateItem()
                                    )
                                }

                                item(key = "similar_to_list_${section.index}") {
                                    LazyRow(
                                        contentPadding = WindowInsets.systemBars
                                            .only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                        modifier = Modifier.animateItem()
                                    ) {
                                        items(recommendation.items, key = { it.id }) { item ->
                                            ytGridItem(item)
                                        }
                                    }
                                }
                            }
                        }
                        is HomeSection.HomePageSection -> {
                            val sectionData = homePage?.sections?.getOrNull(section.index)
                            sectionData?.let {
                                
                                val sectionSongs = sectionData.items.filterIsInstance<SongItem>()
                                val hasPlayableSongs = sectionSongs.isNotEmpty()
                                
                                val isSongsOnlySection = sectionData.items.isNotEmpty() &&
                                        sectionData.items.all { it is SongItem }

                                item(key = "home_section_title_${section.index}") {
                                    NavigationTitle(
                                        title = sectionData.title,
                                        label = sectionData.label,
                                        thumbnail = sectionData.thumbnail?.let { thumbnailUrl ->
                                            {
                                                val shape =
                                                    if (sectionData.endpoint?.isArtistEndpoint == true) CircleShape else RoundedCornerShape(
                                                        ThumbnailCornerRadius
                                                    )
                                                AsyncImage(
                                                    model = thumbnailUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(ListThumbnailSize)
                                                        .clip(shape)
                                                )
                                            }
                                        },
                                        onClick = sectionData.endpoint?.let { endpoint ->
                                            {
                                                when {
                                                    endpoint.browseId == "FEmusic_moods_and_genres" ->
                                                        navController.navigate("mood_and_genres")
                                                    endpoint.params != null ->
                                                        navController.navigate("youtube_browse/${endpoint.browseId}?params=${endpoint.params}")
                                                    else ->
                                                        navController.navigate("browse/${endpoint.browseId}")
                                                }
                                            }
                                        },
                                        onPlayAllClick = if (hasPlayableSongs) {
                                            {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = sectionData.title,
                                                        items = sectionSongs.map { it.toMediaMetadata().toMediaItem() }
                                                    )
                                                )
                                            }
                                        } else null,
                                        modifier = Modifier.animateItem()
                                    )
                                }

                                if (isSongsOnlySection) {
                                    
                                    item(key = "home_section_list_${section.index}") {
                                        LazyHorizontalGrid(
                                            state = rememberLazyGridState(),
                                            rows = GridCells.Fixed(4),
                                            contentPadding = WindowInsets.systemBars
                                                .only(WindowInsetsSides.Horizontal)
                                                .asPaddingValues(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(ListItemHeight * 4)
                                                .animateItem()
                                        ) {
                                            itemsIndexed(
                                                items = sectionSongs.distinctBy { it.id },
                                                key = { _, it -> it.id }
                                            ) { index, song ->
                                                YouTubeListItem(
                                                    item = song,
                                                    isActive = song.id == mediaMetadata?.id,
                                                    isPlaying = isPlaying,
                                                    isSwipeable = false,
                                                    shape = listItemShape(index = index % 4, count = 4),
                                                    trailingContent = {
                                                        IconButton(
                                                            onClick = {
                                                                menuState.show {
                                                                    YouTubeSongMenu(
                                                                        song = song,
                                                                        navController = navController,
                                                                        onDismiss = menuState::dismiss
                                                                    )
                                                                }
                                                            }
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.more_vert),
                                                                contentDescription = null
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .width(horizontalLazyGridItemWidth)
                                                        .combinedClickable(
                                                            onClick = {
                                                                if (song.id == mediaMetadata?.id) {
                                                                    playerConnection.togglePlayPause()
                                                                } else {
                                                                    playerConnection.playQueue(
                                                                        YouTubeQueue.radio(song.toMediaMetadata())
                                                                    )
                                                                }
                                                            },
                                                            onLongClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                menuState.show {
                                                                    YouTubeSongMenu(
                                                                        song = song,
                                                                        navController = navController,
                                                                        onDismiss = menuState::dismiss
                                                                    )
                                                                }
                                                            }
                                                        )
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    
                                    item(key = "home_section_list_${section.index}") {
                                        LazyRow(
                                            contentPadding = WindowInsets.systemBars
                                                .only(WindowInsetsSides.Horizontal)
                                                .asPaddingValues(),
                                            modifier = Modifier.animateItem()
                                        ) {
                                            items(sectionData.items, key = { it.id }) { item ->
                                                ytGridItem(item)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.MoodAndGenres -> {
                            explorePage?.moodAndGenres?.let { moodAndGenres ->
                                item(key = "mood_and_genres_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.mood_and_genres),
                                        onClick = {
                                            navController.navigate("mood_and_genres")
                                        },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                                item(key = "mood_and_genres_list") {
                                    LazyHorizontalGrid(
                                        rows = GridCells.Fixed(4),
                                        contentPadding = PaddingValues(6.dp),
                                        modifier = Modifier
                                            .height((MoodAndGenresButtonHeight + 12.dp) * 4 + 12.dp)
                                            .animateItem()
                                    ) {
                                        items(moodAndGenres.distinctBy { it.title }, key = { it.title }) {
                                            MoodAndGenresButton(
                                                title = it.title,
                                                onClick = {
                                                    navController.navigate("youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}")
                                                },
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .width(180.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            // Handle other sections if needed or just skip
                        }
                    }
                }

                if (isLoading || homePage?.continuation != null && homePage?.sections?.isNotEmpty() == true) {
                    item(key = "loading_shimmer") {
                        ShimmerHost(
                            modifier = Modifier.animateItem()
                        ) {
                            // 1. Quick Picks Skeleton
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues())
                            ) {
                                repeat(3) {
                                    Spacer(
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 12.dp)
                                            .width(250.dp)
                                            .height(290.dp)
                                            .clip(MaterialTheme.shapes.extraLarge)
                                            .background(MaterialTheme.colorScheme.onSurface)
                                    )
                                }
                            }

                            // 2. Speed Dial Skeleton
                            TextPlaceholder(
                                height = 36.dp,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .width(200.dp),
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                            ) {
                                repeat(2) {
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        repeat(3) {
                                            GridItemPlaceHolder(
                                                modifier = Modifier.weight(1f),
                                                fillMaxWidth = true
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. Generic Row Skeleton
                            TextPlaceholder(
                                height = 36.dp,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .width(250.dp),
                            )
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues())
                            ) {
                                repeat(4) {
                                    GridItemPlaceHolder()
                                }
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }

        }
    }
}
