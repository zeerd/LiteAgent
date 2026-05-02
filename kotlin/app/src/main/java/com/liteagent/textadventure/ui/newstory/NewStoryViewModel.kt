package com.liteagent.textadventure.ui.newstory

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liteagent.textadventure.data.local.StorySettingsDataSource
import com.liteagent.textadventure.data.repository.StoryHistoryRepository
import com.liteagent.textadventure.data.db.StoryHistoryEntity
import com.liteagent.textadventure.data.repository.ConversationRepository
import com.liteagent.textadventure.data.db.ConversationEntity
import com.liteagent.textadventure.data.local.AppSettingsRepository
import com.liteagent.textadventure.model.StorySetting
import com.liteagent.textadventure.service.LiteRtLmService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NewStoryViewModel @Inject constructor(
    private val storySettingsDataSource: StorySettingsDataSource,
    private val storyHistoryRepository: StoryHistoryRepository,
    private val conversationRepository: ConversationRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val liteRtLmService: LiteRtLmService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewStoryUiState())
    val uiState: StateFlow<NewStoryUiState> = _uiState.asStateFlow()

    val storySettings: StateFlow<List<StorySetting>> = storySettingsDataSource.storySettings
    val selectedSetting: StateFlow<StorySetting?> = storySettingsDataSource.selectedSetting

    private var selectedSettingFileUri: Uri? = null
    private var selectedSettingFileName: String? = null
    private var selectedSettingDirPath: String? = null

    init {
        viewModelScope.launch {
            liteRtLmService.isInitialized.collect { isInitialized ->
                _uiState.update { it.copy(isModelInitialized = isInitialized) }
            }
        }
    }

    fun onSettingSelected(setting: StorySetting) {
        storySettingsDataSource.selectSetting(setting)
    }

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                // Get the file name from URI
                val fileName = getFileNameFromUri(uri) ?: "untitled"
                val fileNameWithoutExt = getFileNameWithoutExtension(fileName)

                // Store the URI and file name
                selectedSettingFileUri = uri
                selectedSettingFileName = fileName

                // Create directory under TextAdventure/stories/  {filename}/
                val storiesDir = File(context.getExternalFilesDir(null), "TextAdventure/stories")
                if (!storiesDir.exists()) {
                    storiesDir.mkdirs()
                }

                val storyDir = File(storiesDir, fileNameWithoutExt)
                if (!storyDir.exists()) {
                    storyDir.mkdirs()
                }

                selectedSettingDirPath = storyDir.absolutePath

                // Copy file to the new directory
                val inputStream = context.contentResolver.openInputStream(uri)
                val destinationFile = File(storyDir, fileName)
                if (inputStream != null) {
                    destinationFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    inputStream.close()
                }

                // Update UI state to show file name and enable button
                _uiState.update {
                    it.copy(
                        selectedFileName = fileNameWithoutExt,
                        canStartStory = true
                    )
                }

                Toast.makeText(context, "Selected: $fileName", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(context, "Failed to select file: ${e.message}", Toast.LENGTH_SHORT).show()
                _uiState.update {
                    it.copy(
                        selectedFileName = null,
                        canStartStory = false
                    )
                }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = android.provider.OpenableColumns.DISPLAY_NAME
                    val index = it.getColumnIndex(nameIndex)
                    if (index != -1) {
                        fileName = it.getString(index)
                    }
                }
            }
        }
        if (fileName == null) {
            fileName = uri.path?.substringAfterLast('/')
        }
        return fileName
    }

    private fun getFileNameWithoutExtension(fileName: String): String {
        return if (fileName.contains('.')) {
            fileName.substringBeforeLast('.')
        } else {
            fileName
        }
    }

    private fun extractSkillsFromAssets(): String {
        val skillsDir = File(context.filesDir, "skills")
        if (!skillsDir.exists()) {
            skillsDir.mkdirs()
        }

        try {
            val assetManager = context.assets
            // The folder structure in assets is skills/text-adventure/SKILL.md
            // We need to recreate this in internal storage
            val textAdventureDir = File(skillsDir, "text-adventure")
            if (!textAdventureDir.exists()) {
                textAdventureDir.mkdirs()
            }

            val skillFile = File(textAdventureDir, "SKILL.md")
            if (!skillFile.exists()) {
                assetManager.open("skills/text-adventure/SKILL.md").use { input ->
                    FileOutputStream(skillFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return skillsDir.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    fun onStartStory() {
        val fileName = selectedSettingFileName ?: return
        val settingDirPath = selectedSettingDirPath ?: return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(startingStory = true, hasError = false) }

                val storyId = UUID.randomUUID().toString()

                val storyIntro = withContext(Dispatchers.IO) {
                    // Read the selected file content
                    val settingFile = File(settingDirPath, fileName)
                    val settingContent = if (settingFile.exists()) {
                        settingFile.readText()
                    } else {
                        throw IllegalArgumentException("Setting file not found")
                    }

                    // Get app settings for model path
                    val appSettings = appSettingsRepository.getSettings()
                    val modelPath = appSettings.selectedModelPath

                    if (modelPath == null || !File(modelPath).exists()) {
                        throw IllegalStateException("Model not found. Please download or select a model in Settings first.")
                    }

                    // Extract skills from assets to internal storage
                    val skillDir = extractSkillsFromAssets()

                    // Initialize LiteRT-LM for text-adventure skill
                    val isInitialized = liteRtLmService.initialize(
                        engineConfig = liteRtLmService.getEngineConfig().copy(
                            modelPath = modelPath,
                            backend = if (appSettings.accelerationMode == "GPU")
                                com.google.ai.edge.litertlm.Backend.GPU()
                            else
                                com.google.ai.edge.litertlm.Backend.CPU()
                        ),
                        systemPrompt = "You are a text adventure game master using the text-adventure skill.",
                        skillDir = skillDir,
                        allowedSkills = listOf("text-adventure"),
                        compressionThreshold = 0.75f,
                        temperature = appSettings.temperature
                    )

                    if (!isInitialized) {
                        val errorMsg = liteRtLmService.error.value ?: "Unknown error"
                        throw IllegalStateException("Failed to initialize LiteRT-LM: $errorMsg")
                    }

                    // Send the setting file content as the first user prompt
                    val response = liteRtLmService.chat(settingContent)

                    // Generate story intro based on the response
                    val intro = if (response.contains("\n")) {
                        response // Use the AI's response as intro
                    } else {
                        generateStoryIntroFromSetting(settingContent)
                    }

                    // Save initial messages to conversation repository
                    conversationRepository.addMessage(
                        ConversationEntity(
                            messageId = UUID.randomUUID().toString(),
                            text = settingContent,
                            role = "user",
                            sessionId = storyId,
                            activeSessionId = storyId
                        )
                    )

                    conversationRepository.addMessage(
                        ConversationEntity(
                            messageId = UUID.randomUUID().toString(),
                            text = intro,
                            role = "ai",
                            sessionId = storyId,
                            activeSessionId = storyId
                        )
                    )

                    // Save to history
                    val historyEntry = StoryHistoryEntity(
                        id = storyId,
                        settingId = selectedSettingFileUri?.toString() ?: "",
                        settingTitle = selectedSettingFileName ?: "Unknown Setting",
                        storyBeginning = intro,
                        messageCount = 2 // Initial setting + AI response
                    )
                    storyHistoryRepository.addStory(historyEntry)

                    intro
                }

                _uiState.update {
                    it.copy(
                        startingStory = false,
                        storyStarted = true,
                        newStoryId = storyId,
                        storyIntro = storyIntro,
                        canStartStory = false,
                        selectedFileName = null
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        startingStory = false,
                        hasError = true,
                        errorMessage = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    private fun generateStoryIntroFromSetting(settingContent: String): String {
        // Generate a reasonable intro based on the setting content
        return "📖 *Story Initiated*\n\nBased on your selected setting, here begins your adventure.\n\n${settingContent.take(200)}...\n\nWhat would you like to do?"
    }

    fun onNewStoryDismiss() {
        resetSelection()
        _uiState.update { it.copy(storyStarted = false, startingStory = false) }
    }

    private fun resetSelection() {
        selectedSettingFileUri = null
        selectedSettingFileName = null
        selectedSettingDirPath = null
        storySettingsDataSource.clearSelection()
    }

    fun onCancel() {
        resetSelection()
    }

    fun onViewHistory() {
        viewModelScope.launch {
            val stories = storyHistoryRepository.getAllStories().first()
            _uiState.update { it.copy(showHistory = true) }
        }
    }

    fun hideHistory() {
        _uiState.update { it.copy(showHistory = false) }
    }
}

// UI State
data class NewStoryUiState(
    val showHistory: Boolean = false,
    val startingStory: Boolean = false,
    val storyStarted: Boolean = false,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val storyIntro: String? = null,
    val newStoryId: String? = null,
    val isModelInitialized: Boolean = false,
    val selectedFileName: String? = null,
    val canStartStory: Boolean = false
)
