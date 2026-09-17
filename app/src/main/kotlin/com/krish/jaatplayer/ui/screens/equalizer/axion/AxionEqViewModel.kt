package com.krish.jaatplayer.ui.screens.equalizer.axion

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krish.jaatplayer.eq.EqualizerService
import com.krish.jaatplayer.eq.Reverb3DService
import com.krish.jaatplayer.eq.audio.Reverb3DPreset
import com.krish.jaatplayer.eq.data.EQProfileRepository
import com.krish.jaatplayer.eq.data.FilterType
import com.krish.jaatplayer.eq.data.ParametricEQBand
import com.krish.jaatplayer.eq.data.SavedEQProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.datastore.preferences.core.edit
import com.krish.jaatplayer.utils.dataStore
import com.krish.jaatplayer.constants.JaatBassSubMode
import com.krish.jaatplayer.constants.JaatStyleMode
import com.krish.jaatplayer.constants.JaatStylesBassSubModeKey
import com.krish.jaatplayer.constants.JaatStylesEnabledKey
import com.krish.jaatplayer.constants.JaatStylesIntensityKey
import com.krish.jaatplayer.constants.JaatStylesModeKey
import javax.inject.Inject

@HiltViewModel
class AxionEqViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val equalizerService: EqualizerService,
    private val reverb3DService: Reverb3DService,
    private val eqProfileRepository: EQProfileRepository
) : ViewModel() {

    private val prefs = context.getSharedPreferences("jaat_eq_prefs", Context.MODE_PRIVATE)

    val jaatStylesEnabled = context.dataStore.data.map { it[JaatStylesEnabledKey] ?: false }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val jaatStyleMode = context.dataStore.data.map {
        val modeStr = it[JaatStylesModeKey] ?: "BASS_DROP"
        runCatching { JaatStyleMode.valueOf(modeStr) }.getOrDefault(JaatStyleMode.BASS_DROP)
    }.stateIn(viewModelScope, SharingStarted.Lazily, JaatStyleMode.BASS_DROP)

    val jaatStylesIntensity = context.dataStore.data.map { it[JaatStylesIntensityKey] ?: 0.7f }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.7f)

    val jaatStylesBassSubMode = context.dataStore.data.map {
        val subModeStr = it[JaatStylesBassSubModeKey] ?: "BEAT_ADAPTIVE"
        runCatching { JaatBassSubMode.valueOf(subModeStr) }.getOrDefault(JaatBassSubMode.BEAT_ADAPTIVE)
    }.stateIn(viewModelScope, SharingStarted.Lazily, JaatBassSubMode.BEAT_ADAPTIVE)

    fun setJaatStylesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[JaatStylesEnabledKey] = enabled }
        }
    }

    fun setJaatStyleMode(mode: JaatStyleMode) {
        viewModelScope.launch {
            context.dataStore.edit { it[JaatStylesModeKey] = mode.name }
        }
    }

    fun setJaatStylesIntensity(intensity: Float) {
        viewModelScope.launch {
            context.dataStore.edit { it[JaatStylesIntensityKey] = intensity }
        }
    }

    fun setJaatStylesBassSubMode(subMode: JaatBassSubMode) {
        viewModelScope.launch {
            context.dataStore.edit { it[JaatStylesBassSubModeKey] = subMode.name }
        }
    }

    private val _reverbPreset = MutableStateFlow(
        runCatching { Reverb3DPreset.valueOf(prefs.getString("reverb_preset", null) ?: "NONE") }
            .getOrDefault(Reverb3DPreset.NONE)
    )
    val reverbPreset = _reverbPreset.asStateFlow()

    fun setReverbPreset(preset: Reverb3DPreset) {
        _reverbPreset.value = preset
        prefs.edit().putString("reverb_preset", preset.name).apply()
        reverb3DService.applyPreset(preset)
    }

    private val _enabled = MutableStateFlow(prefs.getBoolean("enabled", false))
    val enabled = _enabled.asStateFlow()

    private val bandFrequencies = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    
    private val _bandGains = MutableStateFlow(
        FloatArray(10) { prefs.getFloat("band_$it", 0f) }
    )
    val bandGains = _bandGains.asStateFlow()

    private val _mode = MutableStateFlow(prefs.getInt("mode", 0)) 
    val mode = _mode.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty = _isDirty.asStateFlow()

    val customProfiles = eqProfileRepository.profiles.map { profiles ->
        profiles.filter { it.isCustom && it.id != "jaat_tuning" }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        if (_enabled.value) {
            applyToService()
        }
        if (_reverbPreset.value != Reverb3DPreset.NONE) {
            reverb3DService.applyPreset(_reverbPreset.value)
        }
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        prefs.edit().putBoolean("enabled", enabled).apply()
        if (enabled) {
            applyToService()
        } else {
            viewModelScope.launch {
                eqProfileRepository.setActiveProfile(null)
            }
            equalizerService.disable()
        }
    }

    fun setMode(mode: Int) {
        _mode.value = mode
        prefs.edit().putInt("mode", mode).apply()
        _isDirty.value = false 
    }

    fun setBandGain(index: Int, gain: Float) {
        val newGains = _bandGains.value.copyOf()
        newGains[index] = gain
        _bandGains.value = newGains
        prefs.edit().putFloat("band_$index", gain).apply()
        _isDirty.value = true
        if (_enabled.value) {
            applyToService()
        }
    }

    fun setBandsGains(gains: FloatArray, fromUser: Boolean = false) {
        _bandGains.value = gains
        val editor = prefs.edit()
        gains.forEachIndexed { index, f -> editor.putFloat("band_$index", f) }
        editor.apply()
        _isDirty.value = fromUser 
        if (_enabled.value) {
            applyToService()
        }
    }

    fun reset() {
        val flat = FloatArray(10) { 0f }
        setBandsGains(flat)
    }

    fun saveCustomProfile(name: String) {
        viewModelScope.launch {
            val bands = _bandGains.value.mapIndexed { index, f ->
                ParametricEQBand(
                    frequency = bandFrequencies[index],
                    gain = f.toDouble() / 50.0,
                    q = 1.41,
                    filterType = FilterType.PK,
                    enabled = true
                )
            }
            
            val id = "custom_${System.currentTimeMillis()}"
            val profile = SavedEQProfile(
                id = id,
                name = name,
                deviceModel = "Equalizer",
                bands = bands,
                preamp = 0.0,
                isCustom = true,
                isActive = true
            )
            
            eqProfileRepository.saveProfile(profile)
            eqProfileRepository.setActiveProfile(profile.id)
            _isDirty.value = false
        }
    }

    fun deleteProfiles(ids: List<String>) {
        viewModelScope.launch {
            ids.forEach { id ->
                eqProfileRepository.deleteProfile(id)
            }
        }
    }

    private fun applyToService() {
        viewModelScope.launch {
            val bands = _bandGains.value.mapIndexed { index, f ->
                ParametricEQBand(
                    frequency = bandFrequencies[index],
                    gain = f.toDouble() / 50.0, 
                    q = 1.41,
                    filterType = FilterType.PK,
                    enabled = true
                )
            }
            
            val profile = SavedEQProfile(
                id = "jaat_tuning",
                name = "Jaat Tuning",
                deviceModel = "Equalizer",
                bands = bands,
                preamp = 0.0,
                isCustom = false,
                isActive = true
            )
            
            
            eqProfileRepository.saveProfile(profile)
            eqProfileRepository.setActiveProfile(profile.id)
            
            equalizerService.applyProfile(profile)
        }
    }
}
