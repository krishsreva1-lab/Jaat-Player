package com.krish.jaatplayer.ui.menu

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.res.stringResource
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.net.toUri
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.krish.jaatplayer.LocalDatabase
import com.krish.jaatplayer.LocalDownloadUtil
import com.krish.jaatplayer.LocalListenTogetherManager
import com.krish.jaatplayer.LocalPlayerConnection
import com.krish.jaatplayer.R
import com.krish.jaatplayer.constants.LiquidGlassAnimationStyleKey
import com.krish.jaatplayer.extensions.toggleRepeatMode
import com.krish.jaatplayer.models.MediaMetadata
import com.krish.jaatplayer.playback.ExoDownloadService
import com.krish.jaatplayer.ui.component.GlassMenu
import com.krish.jaatplayer.ui.component.LocalMenuGlassConfig
import com.krish.jaatplayer.ui.component.isGlassSupported
import com.krish.jaatplayer.ui.component.liquidGlass
import com.krish.jaatplayer.utils.rememberPreference
import com.music.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.log2
import kotlin.math.round

data class DockAction(
    val icon: Int,
    val label: String,
    val isActive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Floating 3x3 Liquid Glass "quick actions" panel. Emerges from the three-dot button with a
 * spring scale+fade animation, covers roughly the album artwork area, and leaves song info,
 * playback controls, and bottom navigation visible. Only rendered when the caller has already
 * decided Liquid Glass is enabled for the player menu; otherwise the caller should keep using
 * the normal Material [PlayerMenu] bottom sheet.
 */
@Composable
fun LiquidGlassPlayerDock(
    isVisible: Boolean,
    mediaMetadata: MediaMetadata,
    navController: NavController,
    onDismiss: () -> Unit,
    onMore: () -> Unit,
    onOpenEqualizerGlass: (() -> Unit)? = null,
) {
    val menuGlassConfig = LocalMenuGlassConfig.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val context = LocalContext.current
    val download by LocalDownloadUtil.current.getDownload(mediaMetadata.id).collectAsState(initial = null)
    var showTempoPitchDialog by remember { mutableStateOf(false) }
    var showChoosePlaylistDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()

    val actions = remember(mediaMetadata.id, shuffleModeEnabled, repeatMode, download?.state) {
        listOf(
            DockAction(
                icon = if (download?.state == Download.STATE_COMPLETED) R.drawable.offline else R.drawable.download,
                label = "Download",
                isActive = download?.state == Download.STATE_COMPLETED,
                onClick = {
                    if (download?.state == Download.STATE_COMPLETED) {
                        DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, mediaMetadata.id, false)
                    } else {
                        database.transaction { insert(mediaMetadata) }
                        val request = DownloadRequest.Builder(mediaMetadata.id, mediaMetadata.id.toUri())
                            .setCustomCacheKey(mediaMetadata.id)
                            .setData(mediaMetadata.title.toByteArray())
                            .build()
                        DownloadService.sendAddDownload(context, ExoDownloadService::class.java, request, false)
                    }
                }
            ),
            DockAction(
                icon = R.drawable.shuffle,
                label = "Shuffle",
                isActive = shuffleModeEnabled,
                onClick = { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled }
            ),
            DockAction(
                icon = R.drawable.playlist_add,
                label = "Add to Playlist",
                onClick = { showChoosePlaylistDialog = true; onDismiss() }
            ),
            DockAction(
                icon = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
                label = "Repeat",
                isActive = repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
                onClick = { playerConnection.player.toggleRepeatMode() }
            ),
            DockAction(
                icon = R.drawable.equalizer,
                label = "Equalizer",
                onClick = {
                    if (menuGlassConfig.isEnabledFor(GlassMenu.EQUALIZER)) {
                        if (onOpenEqualizerGlass != null) {
                            onOpenEqualizerGlass()
                        } else {
                            navController.navigate("equalizer_glass")
                        }
                    } else {
                        navController.navigate("settings/equalizer")
                    }
                    onDismiss()
                }
            ),
            DockAction(
                icon = R.drawable.tune,
                label = "Advanced",
                onClick = { showTempoPitchDialog = true }
            ),
            DockAction(
                icon = R.drawable.library_add,
                label = "Add to Library",
                onClick = { playerConnection.toggleLibrary() }
            ),
            DockAction(
                icon = R.drawable.more_horiz,
                label = "More",
                onClick = { onDismiss(); onMore() }
            ),
        )
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            database.transaction { insert(mediaMetadata) }
            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { YouTube.addToPlaylist(it, mediaMetadata.id) }
            }
            listOf(mediaMetadata.id)
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )

    val glassEffectConfig = menuGlassConfig.toGlassEffectConfig(
        globalEnabled = isVisible && menuGlassConfig.isEnabledFor(GlassMenu.PLAYER) && isGlassSupported()
    )

    var isPopupActive by remember { mutableStateOf(isVisible) }
    if (isVisible) isPopupActive = true
    val dockVisibleState = com.krish.jaatplayer.ui.component.rememberGlassVisibleState(isVisible)

    val (animStyle) = rememberPreference(
        key = LiquidGlassAnimationStyleKey,
        defaultValue = "capsule"
    )

    val dockEnterTransition = when (animStyle) {
        "fade" -> fadeIn(animationSpec = tween(280))
        "escape" -> slideInVertically(
            initialOffsetY = { it / 8 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        ) + scaleIn(
            initialScale = 0.65f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(animationSpec = tween(300))
        else -> scaleIn( // "capsule"
            initialScale = 0.22f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(animationSpec = tween(280))
    }

    val dockExitTransition = when (animStyle) {
        "fade" -> fadeOut(animationSpec = tween(200))
        "escape" -> slideOutVertically(
            targetOffsetY = { it / 14 },
            animationSpec = tween(220)
        ) + scaleOut(
            targetScale = 0.65f,
            animationSpec = tween(220)
        ) + fadeOut(animationSpec = tween(200))
        else -> scaleOut( // "capsule"
            targetScale = 0.20f,
            animationSpec = tween(220)
        ) + fadeOut(animationSpec = tween(180))
    }

    if (isPopupActive) {
        Popup(
            onDismissRequest = onDismiss,
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                excludeFromSystemGesture = false,
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visibleState = dockVisibleState,
                    enter = dockEnterTransition,
                    exit = dockExitTransition,
                ) {
                    DisposableEffect(Unit) {
                        onDispose {
                            isPopupActive = false
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(horizontal = 32.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .liquidGlass(config = glassEffectConfig, shape = RoundedCornerShape(32.dp))
                            .clickable(enabled = false) {}, // absorb clicks
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = showTempoPitchDialog,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.92f) togetherWith
                                fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.92f)
                            },
                            label = "DockContent"
                        ) { isSpeedMenu ->
                            if (isSpeedMenu) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = stringResource(R.string.tempo_and_pitch),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    var tempo by remember(playerConnection.player.playbackParameters.speed) {
                                        mutableFloatStateOf(playerConnection.player.playbackParameters.speed)
                                    }
                                    var transposeValue by remember(playerConnection.player.playbackParameters.pitch) {
                                        mutableIntStateOf(round(12 * log2(playerConnection.player.playbackParameters.pitch)).toInt())
                                    }
                                    val updatePlaybackParameters = {
                                        playerConnection.player.playbackParameters =
                                            PlaybackParameters(tempo, 2f.pow(transposeValue.toFloat() / 12))
                                    }
                                    val listenTogetherManager = LocalListenTogetherManager.current
                                    val isInRoom = listenTogetherManager?.isInRoom ?: false

                                    if (!isInRoom) {
                                        ValueAdjuster(
                                            icon = R.drawable.speed,
                                            label = stringResource(R.string.speed),
                                            currentValue = tempo,
                                            values = (0..35).map { round((0.25f + it * 0.05f) * 100) / 100 },
                                            onValueUpdate = {
                                                tempo = it
                                                updatePlaybackParameters()
                                            },
                                            valueText = { "x$it" },
                                            modifier = Modifier.padding(bottom = 12.dp),
                                        )
                                    }
                                    ValueAdjuster(
                                        icon = R.drawable.discover_tune,
                                        label = stringResource(R.string.pitch),
                                        currentValue = transposeValue,
                                        values = (-12..12).toList(),
                                        onValueUpdate = {
                                            transposeValue = it
                                            updatePlaybackParameters()
                                        },
                                        valueText = { "${if (it > 0) "+" else ""}$it" },
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                tempo = 1f
                                                transposeValue = 0
                                                updatePlaybackParameters()
                                            }
                                        ) {
                                            Text(stringResource(R.string.reset))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        TextButton(
                                            onClick = { showTempoPitchDialog = false }
                                        ) {
                                            Text(stringResource(android.R.string.ok))
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    actions.chunked(3).forEach { rowActions ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                        ) {
                                            rowActions.forEach { action ->
                                                DockTile(action = action, onDismiss = onDismiss, modifier = Modifier.weight(1f))
                                            }
                                            // pad out the last row so tiles stay aligned to a 3-column grid
                                            repeat(3 - rowActions.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockTile(action: DockAction, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val dismissingActions = setOf("Download", "Shuffle", "Add to Library")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                action.onClick()
                if (action.label in dismissingActions) {
                    onDismiss()
                }
            }
            .padding(vertical = 14.dp, horizontal = 8.dp),
    ) {
        Icon(
            painter = painterResource(action.icon),
            contentDescription = action.label,
            tint = if (action.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(30.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (action.isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (action.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
