package com.liteagent.textadventure.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

import androidx.compose.ui.res.stringResource

import com.liteagent.textadventure.R

/**
 * 应用程序设置界面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 定义模型文件选择器
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onModelFileSelected(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- 语言选择区块 ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.language_settings_title),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    RadioButtonRow(
                        selected = uiState.language == "zh",
                        title = stringResource(R.string.language_zh),
                        subtitle = "",
                        onClick = { viewModel.onLanguageSelected("zh") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    RadioButtonRow(
                        selected = uiState.language == "en",
                        title = stringResource(R.string.language_en),
                        subtitle = "",
                        onClick = { viewModel.onLanguageSelected("en") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 模型与后端选择区块 ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.model_backend_title),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // HuggingFace 后端
                    RadioButtonRow(
                        selected = uiState.selectedBackend == "huggingface",
                        title = stringResource(R.string.model_backend_huggingface),
                        subtitle = "Open community models",
                        onClick = { viewModel.onBackendSelected("huggingface") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ModelScope 后端
                    RadioButtonRow(
                        selected = uiState.selectedBackend == "modelscope",
                        title = stringResource(R.string.model_backend_modelscope),
                        subtitle = "Alibaba's model platform",
                        onClick = { viewModel.onBackendSelected("modelscope") }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 当前已加载的模型信息
                    Text(
                        text = stringResource(R.string.current_model),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.selectedModelName ?: stringResource(R.string.none_selected),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.selectedModelName == null)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 模型操作按钮组：下载、选择本地文件、打开网页浏览器
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.downloadModel() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            enabled = !uiState.modelDownloading,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.download_models), style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.select_model), style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = { viewModel.onOpenFolderClick() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Public, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.web_browser), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- LLM 参数配置区块 ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.llm_config_title),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Temperature (生成温度) 滑动条
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.temperature))
                            Text(text = "%.2f".format(uiState.temperature), style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = uiState.temperature,
                            onValueChange = { viewModel.onTemperatureChanged(it) },
                            valueRange = 0.0f..2.0f,
                            steps = 20,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Top-P 采样滑动条
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Top-P")
                            Text(text = "%.2f".format(uiState.topP), style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = uiState.topP,
                            onValueChange = { viewModel.onTopPChanged(it) },
                            valueRange = 0.0f..1.0f,
                            steps = 20,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Top-K 采样滑动条
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Top-K")
                            Text(text = "${uiState.topK}", style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = uiState.topK.toFloat(),
                            onValueChange = { viewModel.onTopKChanged(it.toInt()) },
                            valueRange = 1.0f..100.0f,
                            steps = 99,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 硬件加速配置 (CPU vs GPU)
                    Text(
                        text = stringResource(R.string.hardware_acceleration),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.accelerationMode == "CPU",
                            onClick = { viewModel.onAccelerationModeChanged("CPU") },
                            label = { Text("CPU") },
                            modifier = Modifier.weight(1f),
                            leadingIcon = if (uiState.accelerationMode == "CPU") {
                                { Icon(Icons.Default.Check, null) }
                            } else null
                        )
                        FilterChip(
                            selected = uiState.accelerationMode == "GPU",
                            onClick = { viewModel.onAccelerationModeChanged("GPU") },
                            label = { Text("GPU") },
                            modifier = Modifier.weight(1f),
                            leadingIcon = if (uiState.accelerationMode == "GPU") {
                                { Icon(Icons.Default.Check, null) }
                            } else null
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 最大生成 Token 数输入
                    OutlinedTextField(
                        value = uiState.maxTokens.toString(),
                        onValueChange = {
                            try {
                                val value = it.toInt()
                                viewModel.onMaxTokensChanged(value)
                            } catch (e: NumberFormatException) {}
                        },
                        label = { Text(stringResource(R.string.max_tokens)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 系统提示词编辑器
                    OutlinedTextField(
                        value = uiState.systemPrompt,
                        onValueChange = { viewModel.onSystemPromptChanged(it) },
                        label = { Text(stringResource(R.string.system_prompt)) },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                        maxLines = 6,
                        minLines = 4
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 保存与取消按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.saveSettings()
                        onNavigateBack()
                    },
                    modifier = Modifier.weight(0.6f).height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.save_settings))
                }

                OutlinedButton(
                    onClick = { onNavigateBack() },
                    modifier = Modifier.weight(0.4f).height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.cancel_settings))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 带有单选按钮的行组件。
 */
@Composable
fun RadioButtonRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
