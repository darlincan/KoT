package com.neonkaiju.kot.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neonkaiju.kot.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(val fastBots: Boolean = false)

/** ecran settings : lit et modifie les réglages persistés. */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map { SettingsUiState(fastBots = it.fastBots) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun toggleFastBots() {
        viewModelScope.launch {
            val current = settingsRepository.settings.first().fastBots
            settingsRepository.setFastBots(!current)
        }
    }
}
