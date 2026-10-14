

package com.krish.jaatplayer.ui.screens.search

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.krish.jaatplayer.ui.component.GlassComponent
import com.krish.jaatplayer.ui.component.LocalAppBackdrop
import com.krish.jaatplayer.ui.component.backdrop.backdrops.layerBackdrop
import com.krish.jaatplayer.ui.component.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import com.krish.jaatplayer.ui.component.LocalGlassEffectConfig
import com.krish.jaatplayer.ui.component.backdrop.catalog.utils.DampedDragAnimation
import com.krish.jaatplayer.ui.component.isGlassSupported
import com.krish.jaatplayer.ui.component.liquidGlass
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.utils.YouTubeUrlParser
import com.krish.jaatplayer.LocalDatabase
import com.krish.jaatplayer.LocalIsPlayerExpanded
import com.krish.jaatplayer.LocalPlayerAwareWindowInsets
import com.krish.jaatplayer.LocalPlayerConnection
import com.krish.jaatplayer.R
import com.krish.jaatplayer.constants.PauseSearchHistoryKey
import com.krish.jaatplayer.constants.SearchSource
import com.krish.jaatplayer.constants.SearchSourceKey
import com.krish.jaatplayer.db.entities.SearchHistory
import com.krish.jaatplayer.playback.queues.YouTubeQueue
import com.krish.jaatplayer.ui.component.NavigationTitle
import com.krish.jaatplayer.utils.rememberEnumPreference
import com.krish.jaatplayer.utils.rememberPreference
import com.krish.jaatplayer.viewmodels.MoodAndGenresViewModel
import com.krish.jaatplayer.viewmodels.ExploreViewModel
import com.krish.jaatplayer.ui.screens.search.suggestions.SuggestionsTabContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder
import androidx.compose.runtime.collectAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import com.krish.jaatplayer.ui.component.JaatLoadingIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.krish.jaatplayer.ui.component.LocalMenuState
import com.krish.jaatplayer.ui.component.YouTubeGridItem
import com.krish.jaatplayer.ui.menu.YouTubeAlbumMenu
import com.krish.jaatplayer.constants.GridThumbnailHeight
import com.krish.jaatplayer.constants.GridItemsSizeKey
import com.krish.jaatplayer.constants.GridItemSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    pureBlack: Boolean
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isPlayerExpanded = LocalIsPlayerExpanded.current
    val playerConnection = LocalPlayerConnection.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    var isFirstLaunch by rememberSaveable { mutableStateOf(true) }
    
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var showSearchContent by remember { mutableStateOf(false) }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            kotlinx.coroutines.delay(100)
            showSearchContent = true
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        } else {
            showSearchContent = false
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val searchBarHorizontalPadding by animateDpAsState(
        targetValue = if (searchActive) 0.dp else 16.dp,
        animationSpec = tween(durationMillis = 245, easing = FastOutSlowInEasing),
        label = "SearchBarHorizontalPadding"
    )
    val searchBarTopPadding by animateDpAsState(
        targetValue = if (searchActive) 0.dp else 8.dp,
        animationSpec = tween(durationMillis = 245, easing = FastOutSlowInEasing),
        label = "SearchBarTopPadding"
    )

    val onSearch: (String) -> Unit = remember {
        { searchQuery ->
            if (searchQuery.isNotEmpty()) {
                focusManager.clearFocus()
                when (val parsedUrl = YouTubeUrlParser.parse(searchQuery)) {
                    is YouTubeUrlParser.ParsedUrl.Video -> {
                        playerConnection?.playQueue(
                            YouTubeQueue(
                                WatchEndpoint(videoId = parsedUrl.id),
                            ),
                        )
                    }

                    is YouTubeUrlParser.ParsedUrl.Artist -> {
                        navController.navigate("artist/${parsedUrl.id}")
                    }

                    null -> {
                        navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}")
                    }
                }

                if (!pauseSearchHistory) {
                    coroutineScope.launch(Dispatchers.IO) {
                        database.query {
                            insert(SearchHistory(query = searchQuery))
                        }
                    }
                }
            }
        }
    }

    val onSearchFromSuggestion: (String) -> Unit = remember {
        { searchQuery ->
            if (searchQuery.isNotEmpty()) {
                focusManager.clearFocus()
                when (val parsedUrl = YouTubeUrlParser.parse(searchQuery)) {
                    is YouTubeUrlParser.ParsedUrl.Video -> {
                        playerConnection?.playQueue(
                            YouTubeQueue(
                                WatchEndpoint(videoId = parsedUrl.id),
                            ),
                        )
                    }

                    is YouTubeUrlParser.ParsedUrl.Artist -> {
                        navController.navigate("artist/${parsedUrl.id}")
                    }

                    null -> {
                        navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}")
                    }
                }

                if (!pauseSearchHistory) {
                    coroutineScope.launch(Dispatchers.IO) {
                        database.query {
                            insert(SearchHistory(query = searchQuery))
                        }
                    }
                }
            }
        }
    }

    val haptic = LocalHapticFeedback.current

    // The glass dock must NOT sample the app-wide backdrop: that layer is recorded from the
    // whole NavHost, which contains this screen and therefore the dock itself, so the dock
    // would be drawing a layer that contains itself and crash. It gets its own layer that only
    // records this screen's tab content (a sibling of the dock), same pattern as Player.kt.
    val searchBackdropBase = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val searchBackdrop = rememberLayerBackdrop {
        drawRect(searchBackdropBase)
        drawContent()
    }

    val searchGlassConfig = LocalGlassEffectConfig.current
    // Liquid glass search field: follows the nav bar glass setting, and only while the field is
    // collapsed (the expanded search lives in its own window, which can't sample the backdrop).
    val searchFieldGlass = searchGlassConfig.isEnabledFor(GlassComponent.NAV_BAR) &&
        isGlassSupported() && !searchActive
    val searchBarShape = RoundedCornerShape(percent = 50)
    val topBarBackground = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .then(
                        if (searchFieldGlass) {
                            // Transparent so the tab content scrolls UNDER the glass field and dock.
                            Modifier
                        } else {
                            Modifier
                                .background(topBarBackground)
                                .windowInsetsPadding(WindowInsets.statusBars)
                        }
                    )
            ) {
                if (searchFieldGlass) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(topBarBackground)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = searchBarTopPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!searchActive) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                navController.navigate("recognition")
                            },
                            modifier = Modifier
                                .padding(start = 12.dp, end = 2.dp)
                                .size(42.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_shazam_diamond),
                                contentDescription = "Recognize Music",
                                modifier = Modifier.fillMaxSize(),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    CompositionLocalProvider(LocalAppBackdrop provides searchBackdrop) {
                        SearchBar(
                            inputField = {
                                BasicTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .focusRequester(focusRequester),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { 
                                        onSearch(query.text)
                                        searchActive = false
                                    }),
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (searchActive) {
                                                        searchActive = false
                                                        query = TextFieldValue("") 
                                                    } else {
                                                        searchActive = true 
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    painter = painterResource(if (searchActive) R.drawable.arrow_back else R.drawable.search),
                                                    contentDescription = if (searchActive) stringResource(R.string.dismiss) else null,
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 4.dp)
                                            ) {
                                                if (query.text.isEmpty()) {
                                                    Text(
                                                        text = stringResource(
                                                            when (searchSource) {
                                                                SearchSource.LOCAL -> R.string.search_library
                                                                SearchSource.ONLINE -> R.string.search_yt_music
                                                            }
                                                        ),
                                                        style = TextStyle(
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                            fontSize = 16.sp
                                                        )
                                                    )
                                                }
                                                innerTextField()
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (query.text.isNotEmpty()) {
                                                    IconButton(onClick = { query = TextFieldValue("") }) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.close),
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        searchSource = if (searchSource == SearchSource.ONLINE) 
                                                            SearchSource.LOCAL else SearchSource.ONLINE
                                                    }
                                                ) {
                                                    Icon(
                                                        painter = painterResource(
                                                            when (searchSource) {
                                                                SearchSource.LOCAL -> R.drawable.library_music
                                                                SearchSource.ONLINE -> R.drawable.globe_search
                                                            }
                                                        ),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                )
                            },
                            expanded = searchActive,
                            onExpandedChange = { searchActive = it },
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            colors = SearchBarDefaults.colors(
                                containerColor = if (searchFieldGlass) Color.Transparent
                                    else if (pureBlack) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = searchBarShape,
                            tonalElevation = if (searchFieldGlass) 0.dp else SearchBarDefaults.TonalElevation,
                            shadowElevation = if (searchFieldGlass) 0.dp else SearchBarDefaults.ShadowElevation,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (searchFieldGlass) {
                                        Modifier.liquidGlass(config = searchGlassConfig, shape = searchBarShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(
                                    start = if (searchActive) 0.dp else 4.dp,
                                    end = if (searchActive) 0.dp else 12.dp
                                )
                        ) {
                        if (showSearchContent) {
                            when (searchSource) {
                                SearchSource.LOCAL -> LocalSearchScreen(
                                    query = query.text,
                                    navController = navController,
                                    onDismiss = { searchActive = false },
                                    pureBlack = pureBlack
                                )
                                SearchSource.ONLINE -> OnlineSearchScreen(
                                    query = query.text,
                                    onQueryChange = { query = it },
                                    navController = navController,
                                    onSearch = {
                                        onSearchFromSuggestion(it)
                                        searchActive = false
                                    },
                                    onDismiss = { searchActive = false },
                                    pureBlack = pureBlack
                                )
                            }
                        }
                    }
                    }

                }
            }
        },
        containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val bottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .padding(top = if (searchFieldGlass) 0.dp else paddingValues.calculateTopPadding())
                    .fillMaxSize()
                    .layerBackdrop(searchBackdrop)
            ) {
                if (!searchActive) {
                    val tabPadding = PaddingValues(
                        top = if (searchFieldGlass) paddingValues.calculateTopPadding() + 56.dp else 56.dp,
                        bottom = bottomPadding
                    )
                    when (selectedTabIndex) {
                        0 -> ExploreTabContent(navController = navController, contentPadding = tabPadding)
                        1 -> SuggestionsTabContent(navController = navController, contentPadding = tabPadding)
                        2 -> AlbumsTabContent(navController = navController, contentPadding = tabPadding)
                    }
                }
            }

            AnimatedVisibility(
                visible = !searchActive,
                enter = expandVertically(animationSpec = tween(durationMillis = 245, easing = FastOutSlowInEasing)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(durationMillis = 245, easing = FastOutSlowInEasing)) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    CompositionLocalProvider(LocalAppBackdrop provides searchBackdrop) {
                        SearchGlassDock(
                            selectedTabIndex = selectedTabIndex,
                            onTabSelected = { selectedTabIndex = it },
                            pureBlack = pureBlack
                        )
                    }
                }
            }
        }
    }

    
    DisposableEffect(lifecycleOwner, isPlayerExpanded) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlayerExpanded) {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        
        if (isPlayerExpanded) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
fun ExploreTabContent(
    navController: NavController,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val moodAndGenresList by viewModel.moodAndGenres.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        moodAndGenresList?.forEach { section ->
            item {
                NavigationTitle(title = section.title)
            }
            
            val rows = section.items.chunked(2)
            items(rows) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                ) {
                    row.forEach { item ->
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .weight(1f)
                                .padding(6.dp)
                                .height(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable {
                                    navController.navigate(
                                        "youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}"
                                    )
                                }
                                .padding(horizontal = 14.dp)
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    repeat(2 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (moodAndGenresList == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    JaatLoadingIndicator()
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun AlbumsTabContent(
    navController: NavController,
    viewModel: ExploreViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata by (playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) })
    val isPlaying by (playerConnection?.isEffectivelyPlaying?.collectAsState() ?: remember { mutableStateOf(false) })
    val coroutineScope = rememberCoroutineScope()
    
    val explorePage by viewModel.explorePage.collectAsState()
    val newReleaseAlbums = explorePage?.newReleaseAlbums

    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    if (newReleaseAlbums == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            JaatLoadingIndicator()
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 12.dp + contentPadding.calculateTopPadding(),
                end = 12.dp,
                bottom = 12.dp + contentPadding.calculateBottomPadding()
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = newReleaseAlbums.distinctBy { it.id },
                key = { it.id }
            ) { album ->
                YouTubeGridItem(
                    item = album,
                    isActive = mediaMetadata?.album?.id == album.id,
                    isPlaying = isPlaying,
                    coroutineScope = coroutineScope,
                    fillMaxWidth = true,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                navController.navigate("album/${album.id}")
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    YouTubeAlbumMenu(
                                        albumItem = album,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        )
                )
            }
        }
    }
}

@Composable
private fun SearchGlassDock(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    pureBlack: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (searchGyroPuckEnabled) = rememberPreference(
        com.krish.jaatplayer.constants.SearchGyroPuckEnabledKey,
        defaultValue = false
    )

    val (searchDockPuckStyle) = rememberPreference(
        com.krish.jaatplayer.constants.SearchDockPuckStyleKey,
        defaultValue = true
    )

    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassSupported()
    val dockShape = RoundedCornerShape(percent = 50)

    val dockBackgroundModifier = if (useGlass) {
        Modifier.liquidGlass(config = glassConfig, shape = dockShape)
    } else {
        Modifier.background(
            if (pureBlack) Color.Black.copy(alpha = 0.85f)
            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
            dockShape
        )
    }

    val tabs = listOf(
        R.drawable.explore_outlined to stringResource(R.string.tab_explore),
        R.drawable.trending_up to "Jaat Chart",
        R.drawable.album to stringResource(R.string.tab_album)
    )

    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val tabsCount = tabs.size
    val tabWidthPx = if (tabsCount > 0 && containerWidthPx > 0f) containerWidthPx / tabsCount else 1f

    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val animatedIndex by animateFloatAsState(
        targetValue = if (isDragging) {
            (selectedTabIndex + (dragOffsetPx / tabWidthPx.coerceAtLeast(1f))).coerceIn(0f, (tabsCount - 1).toFloat())
        } else {
            selectedTabIndex.toFloat()
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "animatedIndex"
    )

    com.krish.jaatplayer.ui.component.TiltTabStepper(
        enabled = searchGyroPuckEnabled,
        currentIndex = { selectedTabIndex },
        tabsCount = tabsCount,
        onStep = onTabSelected,
    )

    if (searchDockPuckStyle) {
        // Water-drop puck style (same puck as the home navigation bar).
        SearchPuckDock(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            tabs = tabs,
            pureBlack = pureBlack,
            modifier = modifier
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(dockShape)
                .then(dockBackgroundModifier)
                .border(
                    width = 1.dp,
                    color = if (useGlass) glassConfig.textColor.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = dockShape
                )
                .padding(4.dp)
                .onGloballyPositioned { coordinates ->
                    containerWidthPx = coordinates.size.width.toFloat()
                }
        ) {
            val density = LocalDensity.current
            val tabWidthDp = with(density) { tabWidthPx.toDp() }

            if (containerWidthPx > 0f) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset((animatedIndex * tabWidthPx).roundToInt(), 0) }
                        .width(tabWidthDp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragOffsetPx = 0f
                            },
                            onDragEnd = {
                                val targetIndex = (selectedTabIndex + (dragOffsetPx / tabWidthPx.coerceAtLeast(1f))).roundToInt().coerceIn(0, tabsCount - 1)
                                isDragging = false
                                dragOffsetPx = 0f
                                onTabSelected(targetIndex)
                            },
                            onDragCancel = {
                                isDragging = false
                                dragOffsetPx = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                dragOffsetPx += dragAmount
                            }
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, (iconRes, label) ->
                    val isSelected = selectedTabIndex == index

                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(200),
                        label = "contentColor"
                    )

                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable {
                                onTabSelected(index)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = contentColor
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}
