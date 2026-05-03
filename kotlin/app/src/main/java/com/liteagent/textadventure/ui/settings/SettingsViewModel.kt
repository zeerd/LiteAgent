package com.liteagent.textadventure.ui.settings

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
import com.liteagent.textadventure.data.local.AppSettings
import com.liteagent.textadventure.data.local.AppSettingsRepository
import com.liteagent.textadventure.data.local.StorySettingsDataSource
import com.liteagent.textadventure.service.LiteRtLmService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val storySettingsDataSource: StorySettingsDataSource,
    private val liteRtLmService: LiteRtLmService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val selectedBackend: StateFlow<String> = _uiState
        .map { it.selectedBackend }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _uiState.value.selectedBackend)

    val temperature: StateFlow<Float> = _uiState
        .map { it.temperature }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _uiState.value.temperature)

    val maxTokens: StateFlow<Int> = _uiState
        .map { it.maxTokens }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _uiState.value.maxTokens)

    val systemPrompt: StateFlow<String> = _uiState
        .map { it.systemPrompt }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _uiState.value.systemPrompt)

    private var tempSettings = SettingsUiState()

    private var lastDownloadId: Long = -1

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
            if (id == lastDownloadId && id != -1L) {
                handleDownloadComplete(id)
            }
        }
    }

    init {
        loadSettings()

        // Register download receiver
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(downloadReceiver, filter)
        }

        // Observe LiteRT-LM status
        viewModelScope.launch {
            liteRtLmService.isInitialized.collect { isInitialized ->
                _uiState.update { it.copy(isModelInitialized = isInitialized) }
            }
        }

        // Initialize if path exists
        val settings = appSettingsRepository.getSettings()
        if (settings.selectedModelPath != null) {
            saveSettings() // Re-initialize with saved settings
        }
    }

    private fun loadSettings() {
        val settings = appSettingsRepository.getSettings()
        tempSettings = SettingsUiState(
            selectedBackend = settings.backend,
            accelerationMode = settings.accelerationMode,
            selectedModelPath = settings.selectedModelPath,
            selectedModelName = settings.selectedModelName,
            temperature = settings.temperature,
            topP = settings.topP,
            topK = settings.topK,
            maxTokens = settings.maxTokens,
            systemPrompt = settings.systemPrompt,
            isModelInitialized = settings.backend != "huggingface"
        )
        _uiState.value = tempSettings
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

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
                    if (!targetDir.exists()) {
                        targetDir.mkdirs()
                    }

                    val targetFile = File(targetDir, fileName)

                    // Try rename, if fails (cross-volume), try copy
                    val success = if (sourceFile.renameTo(targetFile)) {
                        true
                    } else {
                        try {
                            sourceFile.inputStream().use { input ->
                                targetFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
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
                        showMessage("Download complete. Please manually move the file from Downloads to TextAdventure/models")
                    }
                }
            }
        }
        cursor.close()
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

    fun onSystemPromptChanged(prompt: String) {
        _uiState.update { it.copy(systemPrompt = prompt) }
    }

    fun saveSettings() {
        val currentState = _uiState.value
        val settings = AppSettings(
            backend = currentState.selectedBackend,
            accelerationMode = currentState.accelerationMode,
            selectedModelPath = currentState.selectedModelPath,
            selectedModelName = currentState.selectedModelName,
            temperature = currentState.temperature,
            topP = currentState.topP,
            topK = currentState.topK,
            maxTokens = currentState.maxTokens,
            systemPrompt = currentState.systemPrompt
        )
        appSettingsRepository.saveSettings(settings)

        // Update LiteRT-LM service
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
                    liteRtLmService.initialize(config, settings.systemPrompt)
                }
            } catch (e: Exception) {
                showMessage("Failed to initialize engine: ${e.message}")
            }
        }

        _uiState.update { it.copy(showSavedMessage = true) }
        viewSavedMessage()
    }

    fun cancelSettings() {
        _uiState.update { it.copy(showCancel = false) }
    }

    private fun viewSavedMessage() {
        _uiState.update { it.copy(showSavedMessage = false) }
    }

    fun onModelFileSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                // Get the file name from URI
                val fileName = getFileNameFromUri(uri) ?: "model.litertlm"

                // Create directory for models
                val modelDir = liteRtLmService.getOrCreateModelFolder()
                val targetFile = File(modelDir, fileName)

                // Copy file to the model directory
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        val bytesCopied = input.copyTo(output)
                        Log.d("SettingsViewModel", "Copied $bytesCopied bytes to ${targetFile.absolutePath}")
                        if (bytesCopied == 0L) {
                            throw IllegalStateException("Copied 0 bytes. Source file might be empty or inaccessible.")
                        }
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
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex)
                    }
                }
            }
        }
        if (fileName == null) {
            fileName = uri.path?.substringAfterLast('/')
        }
        return fileName
    }

    fun downloadModel() {
        val backend = _uiState.value.selectedBackend
        val url = if (backend == "huggingface") {
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        } else {
            "https://modelscope.cn/models/litert-community/gemma-4-E2B-it-litert-lm/resolve/master/gemma-4-E2B-it.litertlm"
            // "https://modelscope.cn/models/litert-community/gemma-4-E2B-it-litert-lm/resolve/master/README.md"
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
            showMessage("Download started to Downloads directory. It will be auto-moved upon completion.")
        } catch (e: Exception) {
            showMessage("Download failed: ${e.message}")
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

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

// Settings UI State
data class SettingsUiState(
    val selectedBackend: String = "huggingface",
    val accelerationMode: String = "CPU",
    val selectedModelPath: String? = null,
    val selectedModelName: String? = null,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val maxTokens: Int = 32768,
    val systemPrompt: String = "You are a Text Adventure game master. Create engaging, interactive stories where the user makes choices that affect the narrative.",
    val isModelInitialized: Boolean = false,

    // Download status
    val modelDownloading: Boolean = false,
    val modelDownloadSuccess: Boolean = false,
    val modelDownloadError: Boolean = false,

    // Saved message
    val showSavedMessage: Boolean = false,
    val showCancel: Boolean = false,

    // Model initialization status
    val showModelInitialized: Boolean = false
)
