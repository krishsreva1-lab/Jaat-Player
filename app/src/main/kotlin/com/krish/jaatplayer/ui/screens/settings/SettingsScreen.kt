

package com.krish.jaatplayer.ui.screens.settings

import com.krish.jaatplayer.R
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.krish.jaatplayer.BuildConfig
import com.krish.jaatplayer.LocalPlayerAwareWindowInsets
import com.krish.jaatplayer.ui.component.IconButton
import com.krish.jaatplayer.ui.component.Material3SettingsGroup
import com.krish.jaatplayer.ui.component.Material3SettingsItem
import com.krish.jaatplayer.ui.screens.Screens
import com.krish.jaatplayer.ui.utils.backToMain
import com.krish.jaatplayer.jaat.updater.getUpdateAvailableState
import com.krish.jaatplayer.ui.component.SupportProjectCard
import com.krish.jaatplayer.utils.AppLogo


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
highlightKey: String? = null) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isUpdateAvailable = getUpdateAvailableState(context) && com.krish.jaatplayer.jaat.updater.getAutoUpdateCheckSetting(context)

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchLower = searchQuery.lowercase()

    val accountText = stringResource(R.string.account)
    val appearanceText = stringResource(R.string.appearance)
    val playerText = stringResource(R.string.player_and_audio)
    val listenTogetherText = stringResource(R.string.listen_together)
    val contentText = stringResource(R.string.content)
    val aiLyricsText = stringResource(R.string.ai_lyrics_translation)
    val privacyText = stringResource(R.string.privacy)
    val storageText = stringResource(R.string.storage)
    val backupText = stringResource(R.string.backup_restore)
    val systemUpdateText = stringResource(R.string.system_update)
    val aboutText = stringResource(R.string.about)

    val scrollState = rememberScrollState()
    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 16.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Clear"
                        )
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 16.dp)
        )

        val contributeLosslessText = stringResource(R.string.contribute_to_lossless)

        val accountItem = Material3SettingsItem(
            isHighlighted = (highlightKey == accountText),
            icon = painterResource(R.drawable.account),
            title = { Text(accountText) },
            onClick = { navController.navigate("settings/account") }
        )

        val listenTogetherItem = Material3SettingsItem(
            isHighlighted = (highlightKey == listenTogetherText),
            icon = painterResource(R.drawable.group),
            title = { Text(listenTogetherText) },
            onClick = { navController.navigate(Screens.ListenTogether.route) }
        )

        val playerItem = Material3SettingsItem(
            isHighlighted = (highlightKey == playerText),
            icon = painterResource(R.drawable.play),
            title = { Text(playerText) },
            onClick = { navController.navigate("settings/player") }
        )

        val contentItem = Material3SettingsItem(
            isHighlighted = (highlightKey == contentText),
            icon = painterResource(R.drawable.language),
            title = { Text(contentText) },
            onClick = { navController.navigate("settings/content") }
        )

        val aiItem = Material3SettingsItem(
            isHighlighted = (highlightKey == aiLyricsText),
            customIcon = {
                Text(
                    text = "Ai",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (highlightKey == aiLyricsText)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                )
            },
            title = { Text(aiLyricsText) },
            onClick = { navController.navigate("settings/ai") }
        )

        val losslessItem = Material3SettingsItem(
            isHighlighted = (highlightKey == contributeLosslessText),
            customIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_apple_lossless),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (highlightKey == contributeLosslessText)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                )
            },
            title = { Text(contributeLosslessText) },
            onClick = { navController.navigate("settings/lossless") }
        )

        val appearanceItem = Material3SettingsItem(
            isHighlighted = (highlightKey == appearanceText),
            icon = painterResource(R.drawable.palette),
            title = { Text(appearanceText) },
            onClick = { navController.navigate("settings/appearance") }
        )

        val privacyItem = Material3SettingsItem(
            isHighlighted = (highlightKey == privacyText),
            icon = painterResource(R.drawable.security),
            title = { Text(privacyText) },
            onClick = { navController.navigate("settings/privacy") }
        )

        val storageItem = Material3SettingsItem(
            isHighlighted = (highlightKey == storageText),
            icon = painterResource(R.drawable.storage),
            title = { Text(storageText) },
            onClick = { navController.navigate("settings/storage") }
        )

        val backupItem = Material3SettingsItem(
            isHighlighted = (highlightKey == backupText),
            icon = painterResource(R.drawable.restore),
            title = { Text(backupText) },
            onClick = { navController.navigate("settings/backup_restore") }
        )

        val systemUpdateItem = Material3SettingsItem(
            isHighlighted = (highlightKey == systemUpdateText),
            icon = painterResource(if (isUpdateAvailable) AppLogo.glyphRes() else R.drawable.update),
            title = { Text(systemUpdateText) },
            description = if (isUpdateAvailable) {
                {
                    Text(
                        text = stringResource(R.string.update_available),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else null,
            onClick = { navController.navigate("settings/update") }
        )

        val supportedLinksItem = Material3SettingsItem(
            isHighlighted = (highlightKey == "supported links"),
            icon = painterResource(R.drawable.link),
            title = { Text("Supported Links") },
            onClick = {
                try {
                    val intent = Intent(
                        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    when (e) {
                        is ActivityNotFoundException, is SecurityException -> {
                            Toast.makeText(context, "Cannot open settings", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            Toast.makeText(context, "An error occurred", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )

        val aboutItem = Material3SettingsItem(
            isHighlighted = (highlightKey == aboutText),
            icon = painterResource(R.drawable.info),
            title = { Text(aboutText) },
            onClick = { navController.navigate("settings/about") }
        )

        if (searchQuery.isNotEmpty()) {
            val allMainItemsWithNames = buildList {
                if (accountText.lowercase().contains(searchLower)) add(accountItem)
                if (listenTogetherText.lowercase().contains(searchLower)) add(listenTogetherItem)
                if (playerText.lowercase().contains(searchLower)) add(playerItem)
                if (contentText.lowercase().contains(searchLower)) add(contentItem)
                if (aiLyricsText.lowercase().contains(searchLower)) add(aiItem)
                if (contributeLosslessText.lowercase().contains(searchLower)) add(losslessItem)
                if (appearanceText.lowercase().contains(searchLower)) add(appearanceItem)
                if (privacyText.lowercase().contains(searchLower)) add(privacyItem)
                if (storageText.lowercase().contains(searchLower)) add(storageItem)
                if (backupText.lowercase().contains(searchLower)) add(backupItem)
                if (systemUpdateText.lowercase().contains(searchLower)) add(systemUpdateItem)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && "supported links".contains(searchLower)) add(supportedLinksItem)
                if (aboutText.lowercase().contains(searchLower)) add(aboutItem)
            }

            val subSettings = getAllSearchableSettings()
            val matchedSubSettings = subSettings
                .filter { it.first.lowercase().contains(searchLower) }
                .map { (title, parentTitle, route) ->
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.search),
                        title = { Text(title) },
                        description = { Text(parentTitle) },
                        onClick = {
                            val encodedTitle = android.net.Uri.encode(title)
                            val finalRoute = if (route.contains("?")) "$route&highlightKey=$encodedTitle" else "$route?highlightKey=$encodedTitle"
                            navController.navigate(finalRoute)
                        }
                    )
                }

            val combinedSearchResults = allMainItemsWithNames + matchedSubSettings

            if (combinedSearchResults.isEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "No settings found for \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            } else {
                Material3SettingsGroup(
                    title = "Search Results",
                    scrollState = scrollState,
                    items = combinedSearchResults
                )
            }
        } else {
            // Category 1: Account & Social
            val accountSocialItems = listOf(accountItem, listenTogetherItem)
            Material3SettingsGroup(
                title = "Account & Social",
                scrollState = scrollState,
                items = accountSocialItems
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category 2: Playback & Features
            val playbackItems = listOf(playerItem, contentItem, aiItem, losslessItem)
            Material3SettingsGroup(
                title = "Playback & Features",
                scrollState = scrollState,
                items = playbackItems
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category 3: Interface & Appearance
            val appearanceItems = listOf(appearanceItem)
            Material3SettingsGroup(
                title = "Interface & Appearance",
                scrollState = scrollState,
                items = appearanceItems
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category 4: Data & Privacy
            val dataPrivacyItems = listOf(privacyItem, storageItem, backupItem)
            Material3SettingsGroup(
                title = "Data & Privacy",
                scrollState = scrollState,
                items = dataPrivacyItems
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category 5: System & Info
            val systemInfoItems = buildList {
                add(systemUpdateItem)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(supportedLinksItem)
                }
                add(aboutItem)
            }
            Material3SettingsGroup(
                title = "System & Info",
                scrollState = scrollState,
                items = systemInfoItems
            )
        }
        
        Spacer(modifier = Modifier.height(50.dp))
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            )
        )
    }

    TopAppBar(
        title = {
            androidx.compose.animation.AnimatedVisibility(
                visible = scrollState.value > 100,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}
