package com.zeerd.textadventure.ui.newstory

import android.content.Context
import android.util.Log
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeerd.textadventure.data.local.StorySettingsDataSource
import com.zeerd.textadventure.data.repository.StoryHistoryRepository
import com.zeerd.textadventure.data.db.StoryHistoryEntity
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
     * 点击“开始故事”后的逻辑流程。
     */
    fun onStartStory() {
        val fileName = selectedSettingFileName ?: return
        val settingDirPath = selectedSettingDirPath ?: return

        val storyId = UUID.randomUUID().toString()
        _uiState.update { it.copy(startingStory = true, storyStarted = true, newStoryId = storyId, hasError = false) }

        viewModelScope.launch {
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

                    // 不再直接存入对话数据库，而是通过信号交给 MainViewModel 统一处理（带上前缀指令）
                    // 发送明确的新故事启动信号
                    liteRtLmService.triggerNewStory(storyId, settingContent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting new story: ${e.message}", e)
                _uiState.update { it.copy(startingStory = false, storyStarted = false, hasError = true, errorMessage = e.message) }
            }
        }
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

        // 在后台更新最后活跃时间以触发 MainViewModel 的观察者
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    storyHistoryRepository.updateStory(story.copy(lastActive = System.currentTimeMillis()))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update last active: ${e.message}")
            }
        }
    }
}

/**
 * 新故事界面 (NewStoryScreen) 的 UI 状态。
 * 该状态通过 _uiState 流驱动 NewStoryScreen 的组合，控制故事选择和启动的 UI 流程。
 */
data class NewStoryUiState(
    /**
     * 控制历史故事列表面板的显示/隐藏。
     * - true: 显示 HistoryScreen，展示已有的故事列表供用户选择或删除
     * - false: 隐藏历史记录面板，显示新故事选择界面
     */
    val showHistory: Boolean = false,

    /**
     * 指示是否正在启动新故事（AI 引擎初始化中）。
     * - true: 显示加载指示器和禁用其他交互，防止用户重复点击
     * - false: AI 引擎尚未开始初始化
     */
    val startingStory: Boolean = false,

    /**
     * 指示故事是否已启动成功（用于触发导航）。
     * - true: 表示新故事已成功启动，MainViewModel 需要恢复 AI 上下文
     *         用于通知应用程序导航到主聊天界面
     * - false: 故事尚未启动
     */
    val storyStarted: Boolean = false,

    /**
     * 指示是否发生错误。
     * - true: 表示启动了错误处理流程，可能显示错误提示
     * - false: 当前没有错误状态
     */
    val hasError: Boolean = false,

    /**
     * 显示详细的错误信息（如"模型未找到"、"引擎初始化失败"等）。
     * - 非 null 时，通过 Snackbar 或错误提示组件显示给用户
     * - 用于告知用户具体问题以便修复
     */
    val errorMessage: String? = null,

    /**
     * 显示故事的开场介绍文本（AI 生成的引言）。
     * - 非 null 时，显示故事开篇，作为对话的起始点
     * - 用于向用户展示故事的背景和开始情境
     */
    val storyIntro: String? = null,

    /**
     * 新创建故事的唯一标识符 (ID)。
     * - 非 null 时，用于后续操作（如加载对话历史、更新 AI 上下文）
     * - 传递给 MainViewModel.loadSession() 以恢复 AI 上下文
     */
    val newStoryId: String? = null,

    /**
     * 指示 AI 模型是否已完成初始化。
     * - false: 显示"模型加载中"状态，阻止用户启动故事
     * - true: 模型已就绪，用户可以开始新故事
     */
    val isModelInitialized: Boolean = false,

    /**
     * 显示用户选中的背景文件的文件名（不含扩展名）。
     * - 非 null 时，显示已选文件名称，提供用户确认
     * - 为空表示尚未选择任何背景文件
     */
    val selectedFileName: String? = null,

    /**
     * 指示是否已满足启动故事的所有条件。
     * - true: 表示用户已选择有效背景文件且模型就绪，"开始故事"按钮启用
     * - false: "开始故事"按钮被禁用（尚未选择文件或模型未就绪）
     */
    val canStartStory: Boolean = false
)
