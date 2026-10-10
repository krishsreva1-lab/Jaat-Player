

package com.krish.jaatplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.krish.jaatplayer.localmedia.LocalSongScanConfig
import com.krish.jaatplayer.db.MusicDatabase
import com.krish.jaatplayer.localmedia.LocalSongScanSummary
import com.krish.jaatplayer.localmedia.LocalSongScanner
import com.krish.jaatplayer.utils.reportException
import javax.inject.Inject

import com.krish.jaatplayer.localmedia.LocalMetadataEnricher

@HiltViewModel
class LocalSongsViewModel
@Inject
constructor(
    database: MusicDatabase,
    private val localSongScanner: LocalSongScanner,
    private val metadataEnricher: LocalMetadataEnricher,
) : ViewModel() {
    private val _scanState = MutableStateFlow(LocalSongsScanState())
    val scanState = _scanState.asStateFlow()

    private val _enrichState = MutableStateFlow(LocalMetadataEnrichState())
    val enrichState = _enrichState.asStateFlow()

    val songs = database.localSongs().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun scanDevice(scanConfig: LocalSongScanConfig = LocalSongScanConfig()) {
        if (_scanState.value.isScanning) return
        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = _scanState.value.copy(isScanning = true, errorMessage = null)
            runCatching { localSongScanner.scanDevice(scanConfig) }
                .onSuccess { summary ->
                    _scanState.value = LocalSongsScanState(
                        isScanning = false,
                        lastSummary = summary,
                        errorMessage = null,
                    )
                }
                .onFailure { error ->
                    reportException(error)
                    _scanState.value = _scanState.value.copy(
                        isScanning = false,
                        errorMessage = error.message,
                    )
                }
        }
    }

    fun enrichMetadata() {
        if (_enrichState.value.isEnriching) return
        viewModelScope.launch(Dispatchers.IO) {
            _enrichState.value = LocalMetadataEnrichState(isEnriching = true, completed = 0, total = 0)
            runCatching {
                metadataEnricher.enrichAllLocalSongs { completed, total ->
                    _enrichState.value = LocalMetadataEnrichState(isEnriching = true, completed = completed, total = total)
                }
            }.onSuccess { enrichedCount ->
                _enrichState.value = LocalMetadataEnrichState(isEnriching = false, enrichedCount = enrichedCount)
            }.onFailure { error ->
                reportException(error)
                _enrichState.value = LocalMetadataEnrichState(isEnriching = false, errorMessage = error.message)
            }
        }
    }
}

data class LocalSongsScanState(
    val isScanning: Boolean = false,
    val lastSummary: LocalSongScanSummary? = null,
    val errorMessage: String? = null,
)

data class LocalMetadataEnrichState(
    val isEnriching: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val enrichedCount: Int = 0,
    val errorMessage: String? = null,
)