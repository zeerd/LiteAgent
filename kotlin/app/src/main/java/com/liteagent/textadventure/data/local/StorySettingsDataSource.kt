package com.liteagent.textadventure.data.local

import com.liteagent.textadventure.model.StorySetting
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorySettingsDataSource @Inject constructor() {
    private val _storySettings = MutableStateFlow<List<StorySetting>>(emptyList())
    val storySettings: StateFlow<List<StorySetting>> = _storySettings.asStateFlow()

    private val _selectedSetting = MutableStateFlow<StorySetting?>(null)
    val selectedSetting: StateFlow<StorySetting?> = _selectedSetting.asStateFlow()

    init {
        initializeStorySettings()
    }

    private fun initializeStorySettings() {
        val settings = listOf(
            StorySetting(
                id = "fantasy",
                title = "Fantasy Adventure",
                description = "Embark on an epic quest in a magical realm filled with mythical creatures, ancient prophecies, and powerful artifacts.",
                genre = StorySetting.Genre.FANTASY
            ),
            StorySetting(
                id = "scifi",
                title = "Sci-Fi Mystery",
                description = "Investigate a cosmic anomaly aboard a deep space station as you uncover corporate conspiracies and alien artifacts.",
                genre = StorySetting.Genre.SCIFI
            ),
            StorySetting(
                id = "horror",
                title = "Horror Thriller",
                description = "Survive a night in an abandoned mansion where the past refuses to stay buried and shadows have a life of their own.",
                genre = StorySetting.Genre.HORROR
            ),
            StorySetting(
                id = "romance",
                title = "Romance Drama",
                description = "Navigate love and heartbreak in a story of unexpected connections, challenging choices, and the power of forgiveness.",
                genre = StorySetting.Genre.ROMANCE
            ),
            StorySetting(
                id = "mystery",
                title = "Detective Mystery",
                description = "Solve intricate crimes in a noir world of secrets, lies, and moral ambiguity where nothing is as it appears.",
                genre = StorySetting.Genre.MYSTERY
            )
        )

        _storySettings.value = settings
    }

    fun selectSetting(setting: StorySetting) {
        _selectedSetting.value = setting
    }

    fun clearSelection() {
        _selectedSetting.value = null
    }

    suspend fun getSettingById(settingId: String): StorySetting? {
        return _storySettings.value.find { it.id == settingId }
    }
}
