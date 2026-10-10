package com.krish.jaatplayer.ui.screens

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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.music.innertube.utils.parseCookieString
import com.krish.jaatplayer.BuildConfig
import com.krish.jaatplayer.R
import com.krish.jaatplayer.constants.AccountEmailKey
import com.krish.jaatplayer.constants.InnerTubeCookieKey
import com.krish.jaatplayer.constants.UseLoginForBrowse
import com.krish.jaatplayer.constants.YtmSyncKey
import com.krish.jaatplayer.constants.AudioQualityKey
import com.krish.jaatplayer.constants.AudioQuality
import com.krish.jaatplayer.ui.component.GlassMenu
import com.krish.jaatplayer.ui.component.LocalMenuGlassConfig
import com.krish.jaatplayer.ui.component.Material3SettingsGroup
import com.krish.jaatplayer.ui.component.Material3SettingsItem
import com.krish.jaatplayer.ui.component.isGlassSupported
import com.krish.jaatplayer.ui.component.liquidGlass
import com.krish.jaatplayer.utils.rememberPreference
import com.krish.jaatplayer.utils.rememberEnumPreference
import com.krish.jaatplayer.viewmodels.HomeViewModel
import androidx.compose.ui.layout.ContentScale

@Composable
fun SettingDialoge(
    onDismissRequest: () -> Unit,
    onNavigate: (String) -> Unit,
    homeViewModel: HomeViewModel
) {
    val menuGlassConfig = LocalMenuGlassConfig.current
    val useGlassEffect = remember(menuGlassConfig) {
        menuGlassConfig.isEnabledFor(GlassMenu.SETTINGS) && isGlassSupported()
    }

    if (useGlassEffect) {
        SettingDialogeGlass(onDismissRequest, onNavigate, homeViewModel, menuGlassConfig)
    } else {
        SettingDialogeMaterial(onDismissRequest, onNavigate, homeViewModel)
    }
}

/**
 * Original solid Material3 card, wrapped in a real [Dialog] (its own window). Used
 * whenever the Settings Menu liquid glass toggle (Liquid Glass beta settings) is off,
 * or the device doesn't support the backdrop blur pipeline.
 */
@Composable
private fun SettingDialogeMaterial(
    onDismissRequest: () -> Unit,
    onNavigate: (String) -> Unit,
    homeViewModel: HomeViewModel
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 540.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingDialogeContent(onDismissRequest, onNavigate, homeViewModel)
            }
        }
    }
}

/**
 * True Liquid Glass presentation: rendered as a same-window [Popup] (matching the
 * player's [com.krish.jaatplayer.ui.menu.LiquidGlassPlayerDock] pattern) so the
 * backdrop blur samples the real Home screen content behind it — including the
 * bottom mini player and navigation bar — instead of a frozen/misaligned capture,
 * which is what happens when a glass surface is wrapped in [Dialog].
 */
@Composable
private fun SettingDialogeGlass(
    onDismissRequest: () -> Unit,
    onNavigate: (String) -> Unit,
    homeViewModel: HomeViewModel,
    menuGlassConfig: com.krish.jaatplayer.ui.component.MenuGlassConfig
) {
    val glassEffectConfig = menuGlassConfig.toGlassEffectConfig(globalEnabled = true)
    var isPopupActive by remember { mutableStateOf(true) }
    // Drives the actual enter/exit animation. Requesting a dismiss (scrim tap, back
    // press, a menu item navigating away) sets this false first instead of tearing
    // the Popup down immediately, so the exit transition gets to play; the real
    // onDismissRequest() (which unmounts this composable at the call site) only
    // fires once that exit animation has actually finished, from the
    // DisposableEffect below.
    var isVisible by remember { mutableStateOf(true) }
    val requestDismiss: () -> Unit = { isVisible = false }

    if (isPopupActive) {
        Popup(
            onDismissRequest = requestDismiss,
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
                        onClick = requestDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(
                        initialOffsetY = { it / 8 },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                    ) + scaleIn(
                        initialScale = 0.85f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(animationSpec = tween(300)),
                    exit = slideOutVertically(
                        targetOffsetY = { it / 14 },
                        animationSpec = tween(220)
                    ) + scaleOut(
                        targetScale = 0.9f,
                        animationSpec = tween(220)
                    ) + fadeOut(animationSpec = tween(200)),
                ) {
                    DisposableEffect(Unit) {
                        onDispose {
                            isPopupActive = false
                            onDismissRequest()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .padding(24.dp)
                            .widthIn(max = 540.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .liquidGlass(config = glassEffectConfig, shape = RoundedCornerShape(28.dp))
                            .clickable(enabled = false) {}, // absorb clicks so they don't fall through to dismiss
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 16.dp, horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingDialogeContent(requestDismiss, onNavigate, homeViewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SettingDialogeContent(
    onDismissRequest: () -> Unit,
    onNavigate: (String) -> Unit,
    homeViewModel: HomeViewModel
) {
    val uriHandler = LocalUriHandler.current
    val (audioQuality) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.OPUS
    )
    val (innerTubeCookie, _) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        innerTubeCookie.isNotEmpty() && "SAPISID" in parseCookieString(innerTubeCookie)
    }

    val (accountEmail, _) = rememberPreference(AccountEmailKey, "")
    val accountName by homeViewModel.accountName.collectAsState()

    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)

    val (selectedProfileAvatar) = rememberPreference(com.krish.jaatplayer.constants.SelectedProfileAvatarKey, 1)
    val avatarRes = com.krish.jaatplayer.constants.getProfileAvatarDrawableRes(selectedProfileAvatar)

    val primaryColor = MaterialTheme.colorScheme.onSurface
    val onSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Spacer(modifier = Modifier.size(24.dp))
                    
                    Text(
                        text = "Jaat Player",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = primaryColor,
                        textAlign = TextAlign.Center
                    )

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.close),
                            contentDescription = "Close",
                            tint = primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Account Group
                Material3SettingsGroup(
                    title = "Account",
                    compact = true,
                    items = buildList {
                        add(
                            Material3SettingsItem(
                                title = { Text(if (isLoggedIn) accountName else "Guest Account") },
                                description = { Text(if (isLoggedIn) accountEmail.ifEmpty { "Logged In" } else "Not Logged In") },
                                icon = painterResource(R.drawable.account),
                                trailingContent = {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(avatarRes),
                                        contentDescription = "Profile Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                },
                                onClick = { if (isLoggedIn) onNavigate("settings/account") else onNavigate("login") }
                            )
                        )
                        add(
                            Material3SettingsItem(
                                title = { Text(androidx.compose.ui.res.stringResource(R.string.ai_lyrics_translation)) },
                                customIcon = {
                                    Text(
                                        text = "Ai",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                    )
                                },
                                onClick = {
                                    onDismissRequest()
                                    onNavigate("settings/ai")
                                }
                            )
                        )
                    }
                )

                if (isLoggedIn) {
                    Material3SettingsGroup(
                        title = "Preferences",
                        compact = true,
                        items = listOf(
                            Material3SettingsItem(
                                title = { Text("Use Account for Browsing") },
                                icon = painterResource(R.drawable.add_circle),
                                trailingContent = {
                                    Switch(
                                        checked = useLoginForBrowse,
                                        onCheckedChange = {
                                            com.music.innertube.YouTube.useLoginForBrowse = it
                                            onUseLoginForBrowseChange(it)
                                        },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                },
                                onClick = {
                                    val newVal = !useLoginForBrowse
                                    com.music.innertube.YouTube.useLoginForBrowse = newVal
                                    onUseLoginForBrowseChange(newVal)
                                }
                            ),
                            Material3SettingsItem(
                                title = { Text("YouTube Music Sync") },
                                icon = painterResource(R.drawable.cached),
                                trailingContent = {
                                    Switch(
                                        checked = ytmSync,
                                        onCheckedChange = onYtmSyncChange,
                                        modifier = Modifier.scale(0.8f)
                                    )
                                },
                                onClick = { onYtmSyncChange(!ytmSync) }
                            )
                        )
                    )
                }

                Material3SettingsGroup(
                    title = "App",
                    compact = true,
                    items = listOf(
                        Material3SettingsItem(
                            title = { Text("Settings") },
                            icon = painterResource(R.drawable.settings),
                            onClick = { onNavigate("settings") }
                        ),
                        Material3SettingsItem(
                            title = { Text("About") },
                            icon = painterResource(R.drawable.info),
                            trailingContent = { Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { onNavigate("settings/about") }
                        )
                    )
                )

                // Footer Links
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSecondaryColor,
                        modifier = Modifier.clickable { uriHandler.openUri("https://jaatplayerr.web.app/") }.padding(4.dp)
                    )
                    Text(text = " • ", color = onSecondaryColor, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "Terms of Service",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSecondaryColor,
                        modifier = Modifier.clickable { uriHandler.openUri("https://jaatplayerr.web.app/") }.padding(4.dp)
                    )
                }
}
