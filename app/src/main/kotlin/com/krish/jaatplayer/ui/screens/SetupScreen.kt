package com.krish.jaatplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.models.ArtistItem
import com.krish.jaatplayer.R
import com.krish.jaatplayer.viewmodels.SetupViewModel
import com.krish.jaatplayer.ui.component.JaatLoadingIndicator

@Composable
fun SetupScreen(
    navController: NavController,
    viewModel: SetupViewModel = hiltViewModel()
) {
    var step by remember { mutableIntStateOf(1) }
    val selectedLanguages by viewModel.selectedLanguages.collectAsState()
    val selectedArtists by viewModel.selectedArtists.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val isLoadingArtists by viewModel.isLoadingArtists.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val lastSelectedArtist by viewModel.lastSelectedArtist.collectAsState()
    val similarArtists by viewModel.similarArtists.collectAsState()
    val isLoadingSimilar by viewModel.isLoadingSimilar.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // App Header & Step Indicator
            Text(
                text = stringResource(R.string.welcome_to_jaat_player),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepBadge(step = 1, active = step == 1, label = "Language")
                Spacer(modifier = Modifier.width(12.dp))
                HorizontalDivider(modifier = Modifier.width(24.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.width(12.dp))
                StepBadge(step = 2, active = step == 2, label = "Artists")
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (step) {
                1 -> LanguageStep(
                    languages = viewModel.languages,
                    selectedLanguages = selectedLanguages,
                    onToggleLanguage = viewModel::toggleLanguage,
                    onNext = { step = 2 }
                )
                2 -> ArtistStep(
                    artists = artists,
                    selectedArtists = selectedArtists,
                    isLoading = isLoadingArtists,
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    isSearching = isSearching,
                    onSearchQueryChange = viewModel::onSearchQueryChanged,
                    lastSelectedArtist = lastSelectedArtist,
                    similarArtists = similarArtists,
                    isLoadingSimilar = isLoadingSimilar,
                    onToggleArtist = viewModel::toggleArtist,
                    onFinish = {
                        viewModel.finishSetup {
                            navController.navigate(Screens.Home.route) {
                                popUpTo("setup") { inclusive = true }
                            }
                        }
                    },
                    onBack = { step = 1 }
                )
            }
        }
    }
}

@Composable
fun StepBadge(step: Int, active: Boolean, label: String) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = step.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun LanguageStep(
    languages: List<String>,
    selectedLanguages: Set<String>,
    onToggleLanguage: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.choose_languages),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Select languages to discover your favorite artists",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(languages) { lang ->
                val isSelected = selectedLanguages.contains(lang)
                val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { onToggleLanguage(lang) },
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = lang,
                            style = MaterialTheme.typography.titleMedium,
                            color = textColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNext,
            enabled = selectedLanguages.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp)
        ) {
            Text(text = "Continue (${selectedLanguages.size} selected)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ArtistStep(
    artists: List<ArtistItem>,
    selectedArtists: Set<ArtistItem>,
    isLoading: Boolean,
    searchQuery: String,
    searchResults: List<ArtistItem>,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    lastSelectedArtist: ArtistItem?,
    similarArtists: List<ArtistItem>,
    isLoadingSimilar: Boolean,
    onToggleArtist: (ArtistItem) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.choose_artists),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (selectedArtists.size >= 3) "${selectedArtists.size} artists selected" else "Select at least ${3 - selectedArtists.size} more",
            style = MaterialTheme.typography.bodyMedium,
            color = if (selectedArtists.size >= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Selected Artists Horizontal Avatar Bar
        if (selectedArtists.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(selectedArtists.toList(), key = { it.id }) { artist ->
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clickable { onToggleArtist(artist) }
                    ) {
                        AsyncImage(
                            model = artist.thumbnail,
                            contentDescription = artist.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .align(Alignment.TopEnd),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Artist Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search any artist (e.g. Karan Aujla, Sidhu...)") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Content Area
        Box(modifier = Modifier.weight(1f)) {
            if (searchQuery.isNotBlank()) {
                // Search Results
                if (isSearching) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        JaatLoadingIndicator()
                    }
                } else if (searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No artists found for \"$searchQuery\"", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(searchResults, key = { it.id }) { artist ->
                            val isSelected = selectedArtists.any { it.id == artist.id }
                            ArtistChoiceItem(
                                artist = artist,
                                isSelected = isSelected,
                                onClick = { onToggleArtist(artist) }
                            )
                        }
                    }
                }
            } else if (isLoading && artists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    JaatLoadingIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // Similar Artists Section (Spotify-style dynamic recommendations when an artist is clicked)
                    if (lastSelectedArtist != null && similarArtists.isNotEmpty()) {
                        item(span = { GridItemSpan(3) }) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Similar to ${lastSelectedArtist.title}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (isLoadingSimilar) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(similarArtists, key = { "similar_${it.id}" }) { similarArtist ->
                                            val isSelected = selectedArtists.any { it.id == similarArtist.id }
                                            ArtistChoiceItem(
                                                artist = similarArtist,
                                                isSelected = isSelected,
                                                onClick = { onToggleArtist(similarArtist) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(span = { GridItemSpan(3) }) {
                            Text(
                                text = "More Popular Artists",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                    }

                    items(artists, key = { it.id }) { artist ->
                        val isSelected = selectedArtists.any { it.id == artist.id }
                        ArtistChoiceItem(
                            artist = artist,
                            isSelected = isSelected,
                            onClick = { onToggleArtist(artist) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                Text(text = "Back", fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = onFinish,
                enabled = selectedArtists.size >= 3,
                modifier = Modifier
                    .weight(2f)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                Text(
                    text = if (selectedArtists.size >= 3) stringResource(R.string.finish) + " (${selectedArtists.size})" else "Select ${3 - selectedArtists.size} more",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ArtistChoiceItem(
    artist: ArtistItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
        ) {
            AsyncImage(
                model = artist.thumbnail,
                contentDescription = artist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape
                    )
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = artist.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
