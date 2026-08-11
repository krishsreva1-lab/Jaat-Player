package com.krish.jaatplayer.ui.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.krish.jaatplayer.LocalDatabase
import com.krish.jaatplayer.LocalDownloadUtil
import com.krish.jaatplayer.LocalPlayerConnection
import com.krish.jaatplayer.R
import com.krish.jaatplayer.extensions.toggleRepeatMode
import com.krish.jaatplayer.models.MediaMetadata
import com.krish.jaatplayer.playback.ExoDownloadService
import com.krish.jaatplayer.ui.component.GlassMenu
import com.krish.jaatplayer.ui.component.LocalMenuGlassConfig
import com.krish.jaatplayer.ui.component.isGlassSupported
import com.krish.jaatplayer.ui.component.liquidGlass

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
) {
    val menuGlassConfig = LocalMenuGlassConfig.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val context = LocalContext.current
    val download by LocalDownloadUtil.current.getDownload(mediaMetadata.id).collectAsState(initial = null)
    var showTempoPitchDialog by remember { mutableStateOf(false) }

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
                onClick = { onDismiss(); onMore() } // opens the full menu, which has the playlist picker
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
                onClick = { navController.navigate("equalizer"); onDismiss() }
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

    if (showTempoPitchDialog) {
        TempoPitchDialog(onDismiss = { showTempoPitchDialog = false })
    }

    val glassEffectConfig = menuGlassConfig.toGlassEffectConfig(
        globalEnabled = isVisible && menuGlassConfig.isEnabledFor(GlassMenu.PLAYER) && isGlassSupported()
    )

    var isPopupActive by remember { mutableStateOf(isVisible) }
    LaunchedEffect(isVisible) {
        if (isVisible) isPopupActive = true
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
                    visible = isVisible,
                    enter = scaleIn(
                        initialScale = 0.85f,
                        animationSpec = tween(300, easing = LinearOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(300)),
                    exit = scaleOut(
                        targetScale = 0.85f,
                        animationSpec = tween(250, easing = FastOutLinearInEasing)
                    ) + fadeOut(animationSpec = tween(250)),
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

@Composable
private fun DockTile(action: DockAction, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val dismissingActions = setOf("Download", "Shuffle", "Advanced", "Add to Library")
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
