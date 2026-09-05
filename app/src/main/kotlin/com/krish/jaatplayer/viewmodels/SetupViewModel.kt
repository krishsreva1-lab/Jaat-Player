package com.krish.jaatplayer.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
import com.krish.jaatplayer.constants.PreferredArtistsKey
import com.krish.jaatplayer.constants.PreferredLanguagesKey
import com.krish.jaatplayer.constants.SetupCompletedKey
import com.krish.jaatplayer.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext val context: Context,
) : ViewModel() {

    val languages = listOf("Punjabi", "Haryanvi", "Hindi", "English", "Bhojpuri", "Tamil", "Telugu", "Malayalam", "Kannada", "Bengali")

    val selectedLanguages = MutableStateFlow<Set<String>>(emptySet())
    val selectedArtists = MutableStateFlow<Set<ArtistItem>>(emptySet())

    val artists = MutableStateFlow<List<ArtistItem>>(emptyList())
    val isLoadingArtists = MutableStateFlow(false)

    // Live search query for searching any artist
    val searchQuery = MutableStateFlow("")
    val searchResults = MutableStateFlow<List<ArtistItem>>(emptyList())
    val isSearching = MutableStateFlow(false)

    // Track recently selected artist and their similar artists (e.g. Karan Aujla -> Sidhu Moosewala, Parmish Verma, etc.)
    val lastSelectedArtist = MutableStateFlow<ArtistItem?>(null)
    val similarArtists = MutableStateFlow<List<ArtistItem>>(emptyList())
    val isLoadingSimilar = MutableStateFlow(false)

    private var searchJob: Job? = null

    fun toggleLanguage(lang: String) {
        val current = selectedLanguages.value
        val newSet = if (current.contains(lang)) current - lang else current + lang
        selectedLanguages.value = newSet

        viewModelScope.launch(Dispatchers.IO) {
            fetchArtistsForLanguages(newSet)
        }
    }

    private suspend fun fetchArtistsForLanguages(langs: Set<String>) {
        if (langs.isEmpty()) {
            artists.value = emptyList()
            return
        }
        isLoadingArtists.value = true

        coroutineScope {
            val jobs = langs.flatMap { lang ->
                val queries = when (lang) {
                    "Punjabi" -> listOf(
                        "Punjabi top artists", "Punjabi singers", "Punjabi hit artists",
                        "Punjabi music singers", "Top Punjabi singers", "Punjabi pop artists"
                    )
                    "Haryanvi" -> listOf(
                        "Haryanvi top artists", "Haryanvi singers", "Haryanvi hit artists", "Haryanvi music singers"
                    )
                    "Hindi" -> listOf(
                        "Hindi top artists", "Bollywood singers", "Hindi hit singers", "Hindi pop artists", "Top Indian singers"
                    )
                    "English" -> listOf(
                        "English top artists", "Pop singers", "Global top artists", "English pop singers", "Top Western artists"
                    )
                    "Bhojpuri" -> listOf("Bhojpuri top artists", "Bhojpuri singers", "Bhojpuri hit artists")
                    "Tamil" -> listOf("Tamil top artists", "Tamil singers", "Tamil hit artists")
                    "Telugu" -> listOf("Telugu top artists", "Telugu singers", "Telugu hit artists")
                    "Malayalam" -> listOf("Malayalam top artists", "Malayalam singers")
                    "Kannada" -> listOf("Kannada top artists", "Kannada singers")
                    "Bengali" -> listOf("Bengali top artists", "Bengali singers")
                    else -> listOf("$lang top artists", "$lang singers")
                }

                queries.map { query ->
                    async(Dispatchers.IO) {
                        runCatching {
                            YouTube.search(query, filter = YouTube.SearchFilter.FILTER_ARTIST)
                                .getOrNull()?.items?.filterIsInstance<ArtistItem>().orEmpty()
                        }.getOrDefault(emptyList())
                    }
                }
            }

            val fetchedResults = jobs.awaitAll().flatten()
            val combined = fetchedResults.distinctBy { it.id }
            artists.value = combined
        }

        isLoadingArtists.value = false
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
        searchJob?.cancel()

        if (query.trim().length >= 2) {
            searchJob = viewModelScope.launch(Dispatchers.IO) {
                isSearching.value = true
                val results = runCatching {
                    YouTube.search(query.trim(), filter = YouTube.SearchFilter.FILTER_ARTIST)
                        .getOrNull()?.items?.filterIsInstance<ArtistItem>().orEmpty()
                }.getOrDefault(emptyList())
                searchResults.value = results
                isSearching.value = false
            }
        } else {
            searchResults.value = emptyList()
            isSearching.value = false
        }
    }

    fun toggleArtist(artist: ArtistItem) {
        val current = selectedArtists.value
        val isAlreadySelected = current.any { it.id == artist.id }

        if (isAlreadySelected) {
            val updated = current.filterNot { it.id == artist.id }.toSet()
            selectedArtists.value = updated
            if (lastSelectedArtist.value?.id == artist.id) {
                val remaining = updated.toList()
                if (remaining.isNotEmpty()) {
                    val newLast = remaining.last()
                    lastSelectedArtist.value = newLast
                    fetchSimilarArtistsFor(newLast)
                } else {
                    lastSelectedArtist.value = null
                    similarArtists.value = emptyList()
                }
            }
        } else {
            selectedArtists.value = current + artist
            lastSelectedArtist.value = artist

            // Asynchronously fetch related/similar artists (e.g. Karan Aujla -> Sidhu Moosewala, Parmish Verma)
            fetchSimilarArtistsFor(artist)
        }
    }

    private fun fetchSimilarArtistsFor(artist: ArtistItem) {
        viewModelScope.launch(Dispatchers.IO) {
            isLoadingSimilar.value = true

            val relatedList = mutableListOf<ArtistItem>()

            // 1. Get artist page and extract "Fans also like" / related sections
            runCatching {
                val page = YouTube.artist(artist.id).getOrNull()
                page?.sections?.forEach { section ->
                    relatedList.addAll(section.items.filterIsInstance<ArtistItem>())
                }
            }

            // 2. Search YouTube Music for similar artists
            runCatching {
                val searchRes = YouTube.search("${artist.title} similar artists", filter = YouTube.SearchFilter.FILTER_ARTIST)
                    .getOrNull()?.items?.filterIsInstance<ArtistItem>().orEmpty()
                relatedList.addAll(searchRes)
            }

            val filtered = relatedList
                .filterNot { it.id == artist.id }
                .distinctBy { it.id }

            if (filtered.isNotEmpty()) {
                similarArtists.value = filtered

                // Merge similar artists into main artist list so they are accessible and visible immediately
                val updatedMainList = (filtered + artists.value).distinctBy { it.id }
                artists.value = updatedMainList
            }

            isLoadingSimilar.value = false
        }
    }

    fun finishSetup(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit {
                it[PreferredLanguagesKey] = selectedLanguages.value
                it[PreferredArtistsKey] = selectedArtists.value.map { it.id }.toSet()
                it[SetupCompletedKey] = true
            }
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }
}
