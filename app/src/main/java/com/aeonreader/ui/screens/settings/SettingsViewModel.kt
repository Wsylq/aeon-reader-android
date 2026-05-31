package com.aeonreader.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeonreader.data.repository.UserPreferencesRepository
import com.aeonreader.domain.ReadingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val readingPrefs: StateFlow<ReadingPreferences> = userPreferencesRepository.readingPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadingPreferences())

    fun setReadingPrefs(prefs: ReadingPreferences) {
        viewModelScope.launch {
            userPreferencesRepository.setReadingPreferences(prefs)
        }
    }
}
