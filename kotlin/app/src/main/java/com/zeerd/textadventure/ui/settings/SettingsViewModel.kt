package com.zeerd.textadventure.ui.settings

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeerd.textadventure.data.local.AppSettings
import com.zeerd.textadventure.R
import com.zeerd.textadventure.data.local.AppSettingsRepository
import com.zeerd.textadventure.data.local.StorySettingsDataSource
import com.zeerd.textadventure.service.LiteRtLmService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 设置界面的视图模型。
 * 处理应用程序配置、模型下载、模型选择以及本地 AI 引擎的参数调整。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val storySettingsDataSource: StorySettingsDataSource,
    private val liteRtLmService: LiteRtLmService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // 导出的各个状态流，方便 UI 组件细粒度观察
    val selectedBackend: StateFlow<String> = _uiState
        .map { it.selectedBackend }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _uiState.value.selectedBackend)

    val temperature: StateFlow<Float> = _uiState
        .map { it.temperature }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _uiState.value.temperature)

    val maxTokens: StateFlow<Int> = _uiState
        .map { it.maxTokens }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _uiState.value.maxTokens)

    private var tempSettings = SettingsUiState()

    private var lastDownloadId: Long = -1

    // 监听下载完成的广播接收器
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
            if (id == lastDownloadId && id != -1L) {
                handleDownloadComplete(id)
            }
        }
    }

    init {
        // 加载当前已保存的设置
        loadSettings()

        // 注册下载完成广播
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(downloadReceiver, filter)
        }

        // 观察 AI 引擎的初始化状态
        viewModelScope.launch {
            liteRtLmService.isInitialized.collect { isInitialized ->
                _uiState.update { it.copy(isModelInitialized = isInitialized) }
            }
        }

        // 如果已经配置了模型路径，尝试初始化引擎
        val settings = appSettingsRepository.getSettings()
        if (settings.selectedModelPath != null) {
            saveSettings()
        }
    }

    /**
     * 从存储库加载设置到 UI 状态中。
     */
    private fun loadSettings() {
        val settings = appSettingsRepository.getSettings()
        tempSettings = SettingsUiState(
            language = settings.language,
            selectedBackend = settings.backend,
            accelerationMode = settings.accelerationMode,
            selectedModelPath = settings.selectedModelPath,
            selectedModelName = settings.selectedModelName,
            temperature = settings.temperature,
            topP = settings.topP,
            topK = settings.topK,
            maxTokens = settings.maxTokens,
            isModelInitialized = settings.backend != "huggingface"
        )
        _uiState.value = tempSettings
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {}
    }

    /**
     * 处理模型文件下载完成后的逻辑，自动移动文件到专用目录。
     */
    private fun handleDownloadComplete(downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusIndex)) {
                val fileName = "gemma-4-E2B-it.litertlm"
                val sourceFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)

                if (sourceFile.exists()) {
                    val targetDir = File(Environment.getExternalStorageDirectory(), "TextAdventure/models")
                    if (!targetDir.exists()) targetDir.mkdirs()

                    val targetFile = File(targetDir, fileName)

                    // 移动或拷贝文件到应用专用的模型目录
                    val success = if (sourceFile.renameTo(targetFile)) {
                        true
                    } else {
                        try {
                            sourceFile.inputStream().use { input ->
                                targetFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            sourceFile.delete()
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }

                    if (success) {
                        _uiState.update { it.copy(
                            selectedModelPath = targetFile.absolutePath,
                            selectedModelName = fileName
                        )}
                        saveSettings()
                        showMessage("Model moved to TextAdventure/models and auto-selected.")
                    } else {
                        showMessage("Download complete. Please manually move the file to TextAdventure/models")
                    }
                }
            }
        }
        cursor.close()
    }

    fun onLanguageSelected(language: String) {
        _uiState.update { it.copy(language = language) }
    }

    fun onBackendSelected(backend: String) {
        _uiState.update { it.copy(selectedBackend = backend) }
    }

    fun onAccelerationModeChanged(mode: String) {
        _uiState.update { it.copy(accelerationMode = mode) }
    }

    fun onTemperatureChanged(temperature: Float) {
        _uiState.update { it.copy(temperature = temperature) }
    }

    fun onTopPChanged(topP: Float) {
        _uiState.update { it.copy(topP = topP) }
    }

    fun onTopKChanged(topK: Int) {
        _uiState.update { it.copy(topK = topK) }
    }

    fun onMaxTokensChanged(maxTokens: Int) {
        _uiState.update { it.copy(maxTokens = maxTokens) }
    }

    /**
     * 保存当前 UI 状态中的所有设置到持久化存储。
     */
    fun saveSettings() {
        val currentState = _uiState.value
        val settings = AppSettings(
            language = currentState.language,
            backend = currentState.selectedBackend,
            accelerationMode = currentState.accelerationMode,
            selectedModelPath = currentState.selectedModelPath,
            selectedModelName = currentState.selectedModelName,
            temperature = currentState.temperature,
            topP = currentState.topP,
            topK = currentState.topK,
            maxTokens = currentState.maxTokens
        )
        appSettingsRepository.saveSettings(settings)

        // 应用语言设置变更
        updateLocale(currentState.language)

        // 重新初始化 AI 服务
        viewModelScope.launch {
            try {
                if (settings.selectedModelPath != null) {
                    val config = liteRtLmService.getEngineConfig().copy(
                        modelPath = settings.selectedModelPath,
                        backend = if (settings.accelerationMode == "GPU")
                            com.google.ai.edge.litertlm.Backend.GPU()
                        else
                            com.google.ai.edge.litertlm.Backend.CPU(),
                        maxNumTokens = settings.maxTokens
                    )
                    liteRtLmService.initialize(
                        engineConfig = config
                    )
                }
            } catch (e: Exception) {
                showMessage("Failed to initialize engine: ${e.message}")
            }
        }

        _uiState.update { it.copy(showSavedMessage = true) }
        viewSavedMessage()
    }

    /**
     * 更新应用程序的本地化语言。
     */
    private fun updateLocale(language: String) {
        val locale = if (language == "zh") java.util.Locale.CHINESE else java.util.Locale.ENGLISH
        java.util.Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        context.createConfigurationContext(config)

        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun cancelSettings() {
        _uiState.update { it.copy(showCancel = false) }
    }

    private fun viewSavedMessage() {
        _uiState.update { it.copy(showSavedMessage = false) }
    }

    /**
     * 当用户从本地文件系统选择一个模型文件时。
     */
    fun onModelFileSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val fileName = getFileNameFromUri(uri) ?: "model.litertlm"
                val modelDir = liteRtLmService.getOrCreateModelFolder()
                val targetFile = File(modelDir, fileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        val bytesCopied = input.copyTo(output)
                        if (bytesCopied == 0L) throw IllegalStateException("Source file inaccessible.")
                    }
                }

                _uiState.update { it.copy(
                    selectedModelPath = targetFile.absolutePath,
                    selectedModelName = fileName
                )}

                showMessage("Selected model: $fileName")
            } catch (e: Exception) {
                showMessage("Failed to select model: ${e.message}")
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) fileName = it.getString(nameIndex)
                }
            }
        }
        if (fileName == null) fileName = uri.path?.substringAfterLast('/')
        return fileName
    }

    /**
     * 开始从指定的后端（HuggingFace/ModelScope）下载预定义模型。
     */
    fun downloadModel() {
        val backend = _uiState.value.selectedBackend
        val url = if (backend == "huggingface") {
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        } else {
            "https://modelscope.cn/models/litert-community/gemma-4-E2B-it-litert-lm/resolve/master/gemma-4-E2B-it.litertlm"
        }

        val fileName = "gemma-4-E2B-it.litertlm"

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading Model")
            .setDescription("Downloading $fileName from $backend")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            lastDownloadId = downloadManager.enqueue(request)
            showMessage("Download started. It will be auto-moved upon completion.")
        } catch (e: Exception) {
            showMessage("Download failed: ${e.message}")
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * 跳转到模型所在的组织主页。
     */
    fun onOpenFolderClick() {
        val backend = _uiState.value.selectedBackend
        val url = if (backend == "huggingface") {
            "https://huggingface.co/litert-community/models"
        } else {
            "https://modelscope.cn/organization/litert-community"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun onModelDownloadDismiss() {
        _uiState.update {
            it.copy(modelDownloading = false, modelDownloadSuccess = false, modelDownloadError = false)
        }
    }

    fun onModelInitializedDismiss() {
        _uiState.update { it.copy(showModelInitialized = false) }
    }
}

/**
 * 设置界面的 UI 状态。
 */
data class SettingsUiState(
    val language: String = "zh",
    val selectedBackend: String = "huggingface",
    val accelerationMode: String = "CPU",
    val selectedModelPath: String? = null,
    val selectedModelName: String? = null,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val maxTokens: Int = 32768,
    val isModelInitialized: Boolean = false,

    val modelDownloading: Boolean = false,
    val modelDownloadSuccess: Boolean = false,
    val modelDownloadError: Boolean = false,

    val showSavedMessage: Boolean = false,
    val showCancel: Boolean = false,

    val showModelInitialized: Boolean = false
)
