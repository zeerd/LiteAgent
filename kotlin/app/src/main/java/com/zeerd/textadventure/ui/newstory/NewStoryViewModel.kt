package com.zeerd.textadventure.ui.newstory

import android.content.Context
import android.util.Log
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeerd.textadventure.data.local.StorySettingsDataSource
import com.zeerd.textadventure.data.repository.StoryHistoryRepository
import com.zeerd.textadventure.data.db.StoryHistoryEntity
import com.zeerd.textadventure.data.repository.ConversationRepository
import com.zeerd.textadventure.data.db.ConversationEntity
import com.zeerd.textadventure.data.local.AppSettingsRepository
import com.zeerd.textadventure.model.StorySetting
import com.zeerd.textadventure.service.LiteRtLmService
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

/**
 * 新故事界面的视图模型。
 * 负责处理故事背景选择、文件导入以及启动新冒险的逻辑。
 */
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
        private const val TAG = "TextAdventure-NewStoryViewModel"
    }

    // UI 状态
    private val _uiState = MutableStateFlow(NewStoryUiState())
    val uiState: StateFlow<NewStoryUiState> = _uiState.asStateFlow()

    // 预定义故事设置和当前选中的设置
    val storySettings: StateFlow<List<StorySetting>> = storySettingsDataSource.storySettings
    val selectedSetting: StateFlow<StorySetting?> = storySettingsDataSource.selectedSetting

    // 外部导入文件的相关信息
    private var selectedSettingFileUri: Uri? = null
    private var selectedSettingFileName: String? = null
    private var selectedSettingDirPath: String? = null

    init {
        Log.d(TAG, "Initializing NewStoryViewModel")
        // 观察 AI 模型的就绪状态
        viewModelScope.launch {
            liteRtLmService.isInitialized.collect { isInitialized ->
                _uiState.update { it.copy(isModelInitialized = isInitialized) }
            }
        }
        // 加载上次选择的文件记录
        loadLastSelectedFile()
    }

    /**
     * 从配置中恢复上次选择的文件。
     */
    private fun loadLastSelectedFile() {
        val settings = appSettingsRepository.getSettings()
        selectedSettingFileName = settings.lastSelectedFileName
        selectedSettingFileUri = settings.lastSelectedFileUri?.let { Uri.parse(it) }
        selectedSettingDirPath = settings.lastSelectedFileDirPath

        if (selectedSettingFileName != null) {
            val fileNameWithoutExt = getFileNameWithoutExtension(selectedSettingFileName!!)
            _uiState.update {
                it.copy(
                    selectedFileName = fileNameWithoutExt,
                    canStartStory = true
                )
            }
        }
    }

    /**
     * 当选中一个预设的故事背景时触发。
     */
    fun onSettingSelected(setting: StorySetting) {
        storySettingsDataSource.selectSetting(setting)
    }

    /**
     * 当用户从外部选择了一个背景文件时触发。
     */
    fun onFileSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                // 获取文件名并处理
                val fileName = getFileNameFromUri(uri) ?: "untitled"
                val fileNameWithoutExt = getFileNameWithoutExtension(fileName)

                selectedSettingFileUri = uri
                selectedSettingFileName = fileName

                // 在外部存储中创建该故事的私有目录
                val storiesDir = File(context.getExternalFilesDir(null), "TextAdventure/stories")
                if (!storiesDir.exists()) storiesDir.mkdirs()

                val storyDir = File(storiesDir, fileNameWithoutExt)
                if (!storyDir.exists()) storyDir.mkdirs()

                selectedSettingDirPath = storyDir.absolutePath

                // 将选中的文件拷贝到应用程序的内部存储中
                val inputStream = context.contentResolver.openInputStream(uri)
                val destinationFile = File(storyDir, fileName)
                if (inputStream != null) {
                    destinationFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    inputStream.close()
                }

                // 更新 UI 状态
                _uiState.update {
                    it.copy(
                        selectedFileName = fileNameWithoutExt,
                        canStartStory = true
                    )
                }

                // 持久化当前选择
                val currentSettings = appSettingsRepository.getSettings()
                appSettingsRepository.saveSettings(currentSettings.copy(
                    lastSelectedFileName = fileName,
                    lastSelectedFileUri = uri.toString(),
                    lastSelectedFileDirPath = storyDir.absolutePath
                ))

            } catch (e: Exception) {
                Log.e(TAG, "Error selecting file: ${e.message}", e)
                _uiState.update {
                    it.copy(selectedFileName = null, canStartStory = false)
                }
            }
        }
    }

    /**
     * 从 URI 中提取文件名。
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = android.provider.OpenableColumns.DISPLAY_NAME
                    val index = it.getColumnIndex(nameIndex)
                    if (index != -1) fileName = it.getString(index)
                }
            }
        }
        if (fileName == null) fileName = uri.path?.substringAfterLast('/')
        return fileName
    }

    private fun getFileNameWithoutExtension(fileName: String): String {
        return if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName
    }

    /**
     * 将 assets 中的技能定义提取到内部存储，以便 AI 引擎加载。
     */
    private fun extractSkillsFromAssets(): String {
        val skillsDir = File(context.filesDir, "skills")
        if (!skillsDir.exists()) skillsDir.mkdirs()

        try {
            val assetManager = context.assets
            val textAdventureDir = File(skillsDir, "text-adventure")
            if (!textAdventureDir.exists()) textAdventureDir.mkdirs()

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
            return ""
        }
    }

    /**
     * 点击“开始故事”后的逻辑流程。
     */
    fun onStartStory() {
        val fileName = selectedSettingFileName ?: return
        val settingDirPath = selectedSettingDirPath ?: return

        val storyId = UUID.randomUUID().toString()
        _uiState.update { it.copy(startingStory = true, storyStarted = true, newStoryId = storyId, hasError = false) }

        // 使用服务的长生命周期作用域进行初始化，防止由于页面导航导致 Job 取消
        liteRtLmService.getScope().launch {
            try {
                // 1. 在历史数据库中创建初始条目
                val initialHistoryEntry = StoryHistoryEntity(
                    id = storyId,
                    settingId = selectedSettingFileUri?.toString() ?: "",
                    settingTitle = selectedSettingFileName ?: "Unknown Setting",
                    storyBeginning = "Loading...",
                    messageCount = 0,
                    createdAt = System.currentTimeMillis(),
                    lastActive = System.currentTimeMillis()
                )
                storyHistoryRepository.addStory(initialHistoryEntry)

                withContext(Dispatchers.IO) {
                    // 读取选中的背景文件内容
                    val settingFile = File(settingDirPath, fileName)
                    val settingContent = if (settingFile.exists()) {
                        settingFile.readText()
                    } else {
                        throw IllegalArgumentException("Setting file not found")
                    }

                    val appSettings = appSettingsRepository.getSettings()
                    val modelPath = appSettings.selectedModelPath

                    if (modelPath == null || !File(modelPath).exists()) {
                        throw IllegalStateException("Model not found. Please select a model in Settings first.")
                    }

                    val skillDir = extractSkillsFromAssets()

                    // 2. 初始化 AI 引擎
                    val isInitSuccess = liteRtLmService.initialize(
                        engineConfig = liteRtLmService.getEngineConfig().copy(
                            modelPath = modelPath,
                            backend = if (appSettings.accelerationMode == "GPU")
                                com.google.ai.edge.litertlm.Backend.GPU()
                            else
                                com.google.ai.edge.litertlm.Backend.CPU(),
                            maxNumTokens = appSettings.maxTokens
                        ),
                        skillDir = skillDir,
                        allowedSkills = listOf("text-adventure"),
                        compressionThreshold = 0.75f,
                        temperature = appSettings.temperature
                    )

                    if (!isInitSuccess) {
                        throw IllegalStateException("Failed to initialize LiteRT-LM")
                    }

                    // 3. 将背景内容发送给 AI，获取故事开篇
                    val response = liteRtLmService.chat(settingContent)
                    val intro = if (response.contains("\n")) response else generateStoryIntroFromSetting(settingContent)

                    // 4. 将背景消息和 AI 开篇存入对话数据库
                    conversationRepository.addMessage(
                        ConversationEntity(
                            messageId = UUID.randomUUID().toString(),
                            text = settingContent,
                            role = "system", // 背景信息使用 system 角色
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

                    // 5. 更新历史记录中的最终开篇描述
                    storyHistoryRepository.updateStory(initialHistoryEntry.copy(
                        storyBeginning = intro,
                        messageCount = 2
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting new story: ${e.message}", e)
            }
        }
    }

    private fun generateStoryIntroFromSetting(settingContent: String): String {
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

    fun onCancel() {}

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

    /**
     * 加载现有的历史故事并跳转。
     */
    fun onLoadStory(story: StoryHistoryEntity) {
        // 立即发送导航信号
        _uiState.update { it.copy(storyStarted = true, newStoryId = story.id) }

        // 在后台恢复 AI 上下文环境
        liteRtLmService.getScope().launch {
            try {
                val appSettings = appSettingsRepository.getSettings()
                val modelPath = appSettings.selectedModelPath

                if (modelPath == null || !File(modelPath).exists()) return@launch

                val skillDir = extractSkillsFromAssets()

                // 1. 初始化引擎
                val isInitSuccess = liteRtLmService.initialize(
                    engineConfig = liteRtLmService.getEngineConfig().copy(
                        modelPath = modelPath,
                        backend = if (appSettings.accelerationMode == "GPU")
                            com.google.ai.edge.litertlm.Backend.GPU()
                        else
                            com.google.ai.edge.litertlm.Backend.CPU(),
                        maxNumTokens = appSettings.maxTokens
                    ),
                    skillDir = skillDir,
                    allowedSkills = listOf("text-adventure"),
                    compressionThreshold = 0.75f,
                    temperature = appSettings.temperature
                )

                if (isInitSuccess) {
                    // 2. 加载历史消息
                    val messages = withContext(Dispatchers.IO) {
                        conversationRepository.getMessagesBySessionSync(story.id)
                    }

                    // 3. 将历史同步到 AI 引擎
                    val serviceMessages = messages.map { entity ->
                        com.zeerd.textadventure.service.ContextCompressor.Message(
                            role = entity.role,
                            content = entity.text
                        )
                    }
                    liteRtLmService.restoreHistory(serviceMessages)

                    // 4. 更新最后活跃时间以触发主界面的观察者
                    withContext(Dispatchers.IO) {
                        storyHistoryRepository.updateStory(story.copy(lastActive = System.currentTimeMillis()))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Context restoration failed: ${e.message}")
            }
        }
    }
}

/**
 * 新故事界面的 UI 状态。
 */
data class NewStoryUiState(
    val showHistory: Boolean = false,
    val startingStory: Boolean = false, // 是否正在启动故事
    val storyStarted: Boolean = false, // 故事是否已启动（用于导航）
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val storyIntro: String? = null,
    val newStoryId: String? = null,
    val isModelInitialized: Boolean = false,
    val selectedFileName: String? = null,
    val canStartStory: Boolean = false
)
