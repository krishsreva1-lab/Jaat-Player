/**
 * Home background settings (ported from BeatWave, GPL-3.0, reworked for Jaat Player).
 */

package com.krish.jaatplayer.ui.screens.settings

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.krish.jaatplayer.LocalPlayerAwareWindowInsets
import com.krish.jaatplayer.R
import com.krish.jaatplayer.constants.HomeBackgroundAnimateKey
import com.krish.jaatplayer.constants.HomeBackgroundBlurKey
import com.krish.jaatplayer.constants.HomeBackgroundDimKey
import com.krish.jaatplayer.constants.HomeBackgroundEnabledKey
import com.krish.jaatplayer.constants.HomeBackgroundIsVideoKey
import com.krish.jaatplayer.constants.HomeBackgroundPathKey
import com.krish.jaatplayer.constants.HomeBackgroundQualityKey
import com.krish.jaatplayer.ui.component.IconButton
import com.krish.jaatplayer.ui.component.Material3SettingsGroup
import com.krish.jaatplayer.ui.component.Material3SettingsItem
import com.krish.jaatplayer.ui.component.HomeVideoBackground
import com.krish.jaatplayer.ui.component.rememberHomeBackgroundTargetSize
import com.krish.jaatplayer.ui.utils.backToMain
import com.krish.jaatplayer.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MaxImageBytes = 20L * 1024 * 1024
private const val MaxVideoBytes = 80L * 1024 * 1024

private fun mediaSizeBytes(context: android.content.Context, uri: Uri): Long =
    context.contentResolver
        .query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getLong(idx) else -1L
        } ?: -1L

/**
 * Copies the picked file into app storage (the original may disappear or lose permission).
 * Still images are decoded at the target size and stored as JPEG; videos and GIFs are copied as-is.
 */
private suspend fun copyBackgroundMedia(
    context: android.content.Context,
    source: Uri,
    isVideo: Boolean,
    isGif: Boolean,
    maxWidth: Int,
    maxHeight: Int,
): String? = runCatching {
    val ext = if (isVideo) "mp4" else if (isGif) "gif" else "jpg"
    val dest = File(context.filesDir, "home_background_${System.currentTimeMillis()}.$ext")
    fun copyRaw(): String? {
        context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        return dest.absolutePath
    }
    if (isVideo || isGif) return@runCatching copyRaw()

    val request = ImageRequest.Builder(context)
        .data(source)
        .size(maxWidth, maxHeight)
        .allowHardware(false) // compress() needs readable pixels
        .build()
    val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
        ?: return@runCatching copyRaw()
    dest.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output) }
    dest.absolutePath
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeBackgroundSettings(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val (enabled, onEnabledChange) = rememberPreference(HomeBackgroundEnabledKey, defaultValue = false)
    val (path, onPathChange) = rememberPreference(HomeBackgroundPathKey, defaultValue = "")
    val (blur, onBlurChange) = rememberPreference(HomeBackgroundBlurKey, defaultValue = 20f)
    val (dim, onDimChange) = rememberPreference(HomeBackgroundDimKey, defaultValue = 0.4f)
    val (animate, onAnimateChange) = rememberPreference(HomeBackgroundAnimateKey, defaultValue = false)
    val (isVideo, onIsVideoChange) = rememberPreference(HomeBackgroundIsVideoKey, defaultValue = false)
    val (quality, onQualityChange) = rememberPreference(HomeBackgroundQualityKey, defaultValue = 1f)
    val (targetWidth, targetHeight) = rememberHomeBackgroundTargetSize(quality)

    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }

    fun applyPickedMedia(uri: Uri, video: Boolean, gif: Boolean) {
        val previous = path
        scope.launch {
            val newPath = withContext(Dispatchers.IO) {
                copyBackgroundMedia(context, uri, video, gif, targetWidth, targetHeight)
            }
            if (newPath != null) {
                onPathChange(newPath)
                onIsVideoChange(video)
                if (!enabled) onEnabledChange(true)
                if (previous.isNotEmpty()) withContext(Dispatchers.IO) { File(previous).delete() }
            } else {
                Toast.makeText(context, "Couldn't use that file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri)
        val video = mimeType?.startsWith("video/") == true
        val gif = mimeType == "image/gif"
        val cap = if (video) MaxVideoBytes else MaxImageBytes
        if (mediaSizeBytes(context, uri) > cap) {
            Toast.makeText(context, "File too large (max ${cap / (1024 * 1024)} MB)", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        if (video) pendingVideoUri = uri else applyPickedMedia(uri, video = false, gif = gif)
    }

    pendingVideoUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingVideoUri = null },
            title = { Text("Use a video background?") },
            text = { Text("A looping video keeps the screen busy and uses more battery than a still image. It pauses automatically when the app is in the background.") },
            confirmButton = {
                TextButton(onClick = {
                    applyPickedMedia(uri, video = true, gif = false)
                    pendingVideoUri = null
                }) { Text("Use video") }
            },
            dismissButton = { TextButton(onClick = { pendingVideoUri = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home background") },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Live preview of what Home will show behind its content.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (path.isNotEmpty() && isVideo) {
                    HomeVideoBackground(path = path, blur = blur, dim = dim)
                } else if (path.isNotEmpty()) {
                    val previewRequest = remember(path) { ImageRequest.Builder(context).data(File(path)).build() }
                    AsyncImage(
                        model = previewRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (blur > 0f) Modifier.blur(blur.dp) else Modifier),
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
                } else {
                    Text(
                        text = "No background selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            Material3SettingsGroup(
                title = "Custom Home background",
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.tune),
                        title = { Text("Use custom Home background") },
                        description = { Text("Off keeps the normal Home screen") },
                        trailingContent = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = onEnabledChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(if (enabled) R.drawable.check else R.drawable.close),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                },
                            )
                        },
                        onClick = { onEnabledChange(!enabled) },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.image),
                        title = { Text("Choose image or video") },
                        description = {
                            Text(
                                when {
                                    path.isEmpty() -> "Nothing selected yet"
                                    isVideo -> "Video selected"
                                    else -> "Image selected"
                                },
                            )
                        },
                        onClick = {
                            pickLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                            )
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.sliders),
                        title = { Text("Blur") },
                        description = {
                            Column {
                                Text("%.0f".format(blur))
                                Slider(value = blur, onValueChange = onBlurChange, valueRange = 0f..50f, enabled = enabled)
                            }
                        },
                        enabled = enabled,
                        disabledClickReason = "Enable Custom Home Background to modify this setting",
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.tune),
                        title = { Text("Dim") },
                        description = {
                            Column {
                                Text("%.0f%%".format(dim * 100))
                                Slider(value = dim, onValueChange = onDimChange, valueRange = 0f..1f, enabled = enabled)
                            }
                        },
                        enabled = enabled,
                        disabledClickReason = "Enable Custom Home Background to modify this setting",
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.sliders),
                        title = { Text("Image quality") },
                        description = {
                            Column {
                                Text("%.0f%% of screen resolution (applies to the next image you pick)".format(quality * 100))
                                Slider(value = quality, onValueChange = onQualityChange, valueRange = 0.3f..1f, enabled = enabled)
                            }
                        },
                        enabled = enabled,
                        disabledClickReason = "Enable Custom Home Background to modify this setting",
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.tune),
                        title = { Text("Animate blur") },
                        description = { Text("Starts sharp and eases into the chosen blur when Home opens") },
                        enabled = enabled,
                        disabledClickReason = "Enable Custom Home Background to modify this setting",
                        trailingContent = {
                            Switch(
                                checked = animate,
                                onCheckedChange = onAnimateChange,
                                enabled = enabled,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(if (animate) R.drawable.check else R.drawable.close),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                },
                            )
                        },
                        onClick = { onAnimateChange(!animate) },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.delete),
                        title = { Text("Remove background") },
                        enabled = enabled && path.isNotEmpty(),
                        disabledClickReason = "Enable Custom Home Background to modify this setting",
                        onClick = {
                            val current = path
                            if (current.isNotEmpty()) scope.launch(Dispatchers.IO) { File(current).delete() }
                            onPathChange("")
                            onIsVideoChange(false)
                            onEnabledChange(false)
                        },
                    ),
                ),
            )
            Spacer(
                Modifier
                    .height(16.dp)
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
            )
        }
    }
}
