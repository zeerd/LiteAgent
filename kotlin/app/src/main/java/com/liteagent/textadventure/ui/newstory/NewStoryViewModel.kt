package com.liteagent.textadventure.ui.newstory

import android.content.Context
import android.util.Log
import android.net.Uri
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
    val storyHistoryRepository: StoryHistoryRepository,
    private val conversationRepository: ConversationRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val liteRtLmService: LiteRtLmService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "NewStoryViewModel"
    }

    private val _uiState = MutableStateFlow(NewStoryUiState())
    val uiState: StateFlow<NewStoryUiState> = _uiState.asStateFlow()

    val storySettings: StateFlow<List<StorySetting>> = storySettingsDataSource.storySettings
    val selectedSetting: StateFlow<StorySetting?> = storySettingsDataSource.selectedSetting

    private var selectedSettingFileUri: Uri? = null
    private var selectedSettingFileName: String? = null
    private var selectedSettingDirPath: String? = null

    init {
        Log.d(TAG, "Initializing NewStoryViewModel")
        viewModelScope.launch {
            liteRtLmService.isInitialized.collect { isInitialized ->
                Log.d(TAG, "LiteRT-LM initialized state changed: $isInitialized")
                _uiState.update { it.copy(isModelInitialized = isInitialized) }
            }
        }
        loadLastSelectedFile()
    }

    private fun loadLastSelectedFile() {
        Log.d(TAG, "Loading last selected file from settings")
        val settings = appSettingsRepository.getSettings()
        selectedSettingFileName = settings.lastSelectedFileName
        selectedSettingFileUri = settings.lastSelectedFileUri?.let { Uri.parse(it) }
        selectedSettingDirPath = settings.lastSelectedFileDirPath

        if (selectedSettingFileName != null) {
            Log.d(TAG, "Restoring selection: $selectedSettingFileName")
            val fileNameWithoutExt = getFileNameWithoutExtension(selectedSettingFileName!!)
            _uiState.update {
                it.copy(
                    selectedFileName = fileNameWithoutExt,
                    canStartStory = true
                )
            }
        }
    }

    fun onSettingSelected(setting: StorySetting) {
        Log.d(TAG, "Story setting selected: ${setting.id}")
        storySettingsDataSource.selectSetting(setting)
    }

    fun onFileSelected(uri: Uri) {
        Log.d(TAG, "File selected: $uri")
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

                Log.d(TAG, "Copying selected file to: ${storyDir.absolutePath}")

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

                // Persist the selection
                Log.d(TAG, "Persisting file selection in settings")
                val currentSettings = appSettingsRepository.getSettings()
                appSettingsRepository.saveSettings(currentSettings.copy(
                    lastSelectedFileName = fileName,
                    lastSelectedFileUri = uri.toString(),
                    lastSelectedFileDirPath = storyDir.absolutePath
                ))

            } catch (e: Exception) {
                Log.e(TAG, "Error selecting file: ${e.message}", e)
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

        Log.d(TAG, "Starting new story with file: $fileName")

        val storyId = UUID.randomUUID().toString()
        _uiState.update { it.copy(startingStory = true, storyStarted = true, newStoryId = storyId, hasError = false) }

        // Use LiteRtLmService's long-lived scope to avoid JobCancellationException when navigating back
        liteRtLmService.getScope().launch {
            try {
                Log.d(TAG, "Background initialization started for storyId: $storyId")

                // Initial history entry (empty or with title)
                val initialHistoryEntry = StoryHistoryEntity(
                    id = storyId,
                    settingId = selectedSettingFileUri?.toString() ?: "",
                    settingTitle = selectedSettingFileName ?: "Unknown Setting",
                    storyBeginning = "Loading...",
                    messageCount = 0,
                    createdAt = System.currentTimeMillis(),
                    lastActive = System.currentTimeMillis()
                )
                Log.d(TAG, "Saving initial history entry for storyId: $storyId")
                storyHistoryRepository.addStory(initialHistoryEntry)

                withContext(Dispatchers.IO) {
                    // Read the selected file content
                    val settingFile = File(settingDirPath, fileName)
                    val settingContent = if (settingFile.exists()) {
                        settingFile.readText()
                    } else {
                        Log.e(TAG, "Setting file not found: ${settingFile.absolutePath}")
                        throw IllegalArgumentException("Setting file not found")
                    }

                    Log.d(TAG, "Background setting content read (${settingContent.length} chars)")

                    // Get app settings for model path
                    val appSettings = appSettingsRepository.getSettings()
                    val modelPath = appSettings.selectedModelPath

                    if (modelPath == null || !File(modelPath).exists()) {
                        Log.e(TAG, "Model path invalid: $modelPath")
                        throw IllegalStateException("Model not found. Please download or select a model in Settings first.")
                    }

                    // Extract skills from assets to internal storage
                    val skillDir = extractSkillsFromAssets()

                    Log.d(TAG, "Initializing LiteRT-LM for new story...")
                    // Initialize LiteRT-LM for text-adventure skill
                    val isInitSuccess = liteRtLmService.initialize(
                        engineConfig = liteRtLmService.getEngineConfig().copy(
                            modelPath = modelPath,
                            backend = if (appSettings.accelerationMode == "GPU")
                                com.google.ai.edge.litertlm.Backend.GPU()
                            else
                                com.google.ai.edge.litertlm.Backend.CPU(),
                            maxNumTokens = appSettings.maxTokens
                        ),
                        systemPrompt = "You are a text adventure game master using the text-adventure skill.",
                        skillDir = skillDir,
                        allowedSkills = listOf("text-adventure"),
                        compressionThreshold = 0.75f,
                        temperature = appSettings.temperature
                    )

                    if (!isInitSuccess) {
                        val errorMsg = liteRtLmService.error.value ?: "Unknown error"
                        Log.e(TAG, "LiteRT-LM initialization failed: $errorMsg")
                        throw IllegalStateException("Failed to initialize LiteRT-LM: $errorMsg")
                    }

                    Log.d(TAG, "LLM INITIALIZATION SUCCESSFUL")

                    Log.d(TAG, ">>> PREPARING FIRST LLM CALL <<<")
                    Log.d(TAG, "TARGET SYSTEM PROMPT: You are a text adventure game master using the text-adventure skill.")
                    Log.d(TAG, "TARGET USER PROMPT (Background Setting): $settingContent")

                    // Send the setting file content as the first user prompt
                    val response = liteRtLmService.chat(settingContent)
                    Log.d(TAG, "LLM RESPONSE RECEIVED: ${response.take(100)}...")

                    // Generate story intro based on the response
                    val intro = if (response.contains("\n")) {
                        response // Use the AI's response as intro
                    } else {
                        Log.w(TAG, "LLM response short/malformed, generating fallback intro")
                        generateStoryIntroFromSetting(settingContent)
                    }

                    Log.d(TAG, "Saving initial conversation messages")
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

                    Log.d(TAG, "Updating history entry with final intro")
                    // Save to history (Update with real intro)
                    val historyEntry = StoryHistoryEntity(
                        id = storyId,
                        settingId = selectedSettingFileUri?.toString() ?: "",
                        settingTitle = selectedSettingFileName ?: "Unknown Setting",
                        storyBeginning = intro,
                        messageCount = 2, // Initial setting + AI response
                        createdAt = initialHistoryEntry.createdAt,
                        lastActive = System.currentTimeMillis()
                    )
                    storyHistoryRepository.updateStory(historyEntry)
                }

                Log.d(TAG, "New story started successfully in background")

            } catch (e: Exception) {
                Log.e(TAG, "Error starting new story in background: ${e.message}", e)
                // Since we've already navigated away, we can't easily show this error in the NewStory UI,
                // but MainViewModel could observe an error state if we add it to the service.
            }
        }
    }

    private fun generateStoryIntroFromSetting(settingContent: String): String {
        // Generate a reasonable intro based on the setting content
        return "📖 *Story Initiated*\n\nBased on your selected setting, here begins your adventure.\n\n${settingContent.take(200)}...\n\nWhat would you like to do?"
    }

    fun onNewStoryDismiss() {
        _uiState.update { it.copy(storyStarted = false, startingStory = false) }
    }

    private fun resetSelection() {
        selectedSettingFileUri = null
        selectedSettingFileName = null
        selectedSettingDirPath = null
        storySettingsDataSource.clearSelection()
    }

    fun onCancel() {
        // No longer resetting selection here
    }

    fun onViewHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(showHistory = true) }
        }
    }

    fun hideHistory() {
        _uiState.update { it.copy(showHistory = false) }
    }

    fun deleteStory(storyId: String) {
        viewModelScope.launch {
            storyHistoryRepository.deleteStory(storyId)
        }
    }

    fun deleteSelectedStories(storyIds: List<String>) {
        viewModelScope.launch {
            storyIds.forEach { storyHistoryRepository.deleteStory(it) }
        }
    }

    fun onLoadStory(story: StoryHistoryEntity) {
        Log.d(TAG, "onLoadStory called for ID: ${story.id}")

        // Immediate navigation signal
        _uiState.update { it.copy(storyStarted = true, newStoryId = story.id) }

        // Use serviceScope to restore context in background
        liteRtLmService.getScope().launch {
            try {
                Log.d(TAG, "Re-initializing engine and restoring context for story: ${story.id}")

                // Get app settings for model path
                val appSettings = appSettingsRepository.getSettings()
                val modelPath = appSettings.selectedModelPath

                if (modelPath == null || !File(modelPath).exists()) {
                    Log.e(TAG, "Model path invalid: $modelPath")
                    return@launch
                }

                // Extract skills from assets
                val skillDir = extractSkillsFromAssets()

                // 1. Initialize LiteRT-LM
                val isInitSuccess = liteRtLmService.initialize(
                    engineConfig = liteRtLmService.getEngineConfig().copy(
                        modelPath = modelPath,
                        backend = if (appSettings.accelerationMode == "GPU")
                            com.google.ai.edge.litertlm.Backend.GPU()
                        else
                            com.google.ai.edge.litertlm.Backend.CPU(),
                        maxNumTokens = appSettings.maxTokens
                    ),
                    systemPrompt = "You are a text adventure game master using the text-adventure skill.",
                    skillDir = skillDir,
                    allowedSkills = listOf("text-adventure"),
                    compressionThreshold = 0.75f,
                    temperature = appSettings.temperature
                )

                if (isInitSuccess) {
                    // 2. Load all past messages for this session
                    val messages = withContext(Dispatchers.IO) {
                        conversationRepository.getMessagesBySessionSync(story.id)
                    }

                    Log.d(TAG, "Restoring ${messages.size} messages to LLM context")

                    // 3. Convert to service format and prime the engine
                    val serviceMessages = messages.map { entity ->
                        com.liteagent.textadventure.service.ContextCompressor.Message(
                            role = entity.role,
                            content = entity.text
                        )
                    }

                    liteRtLmService.restoreHistory(serviceMessages)

                    // 4. Update lastActive to trigger observer in MainViewModel
                    withContext(Dispatchers.IO) {
                        storyHistoryRepository.updateStory(story.copy(lastActive = System.currentTimeMillis()))
                    }

                    Log.d(TAG, "Context restoration complete for story: ${story.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Context restoration failed: ${e.message}")
            }
        }
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
