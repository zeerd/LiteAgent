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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onModelFileSelected(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
            // Model Selection Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Model Backend",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // HuggingFace Option
                    RadioButtonRow(
                        selected = uiState.selectedBackend == "huggingface",
                        title = "HuggingFace",
                        subtitle = "Open community models",
                        onClick = { viewModel.onBackendSelected("huggingface") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ModelScope Option
                    RadioButtonRow(
                        selected = uiState.selectedBackend == "modelscope",
                        title = "ModelScope",
                        subtitle = "Alibaba's model platform",
                        onClick = { viewModel.onBackendSelected("modelscope") }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Model Info
                    Text(
                        text = "Current Model: ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.selectedModelName ?: "None selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.selectedModelName == null)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Download Button
                        Button(
                            onClick = { viewModel.downloadModel() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            enabled = !uiState.modelDownloading,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download", style = MaterialTheme.typography.labelMedium)
                        }

                        // Select Button
                        Button(
                            onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select", style = MaterialTheme.typography.labelMedium)
                        }

                        // Browser Button
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
                            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Web", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // LLM Configuration Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LLM Configuration",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Temperature Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Temperature")
                            Text(
                                text = "%.2f".format(uiState.temperature),
                                style = MaterialTheme.typography.bodySmall
                            )
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

                    // Top-P Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Top-P")
                            Text(
                                text = "%.2f".format(uiState.topP),
                                style = MaterialTheme.typography.bodySmall
                            )
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

                    // Top-K Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Top-K")
                            Text(
                                text = "${uiState.topK}",
                                style = MaterialTheme.typography.bodySmall
                            )
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

                    Text(
                        text = "Hardware Acceleration",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.accelerationMode == "CPU",
                            onClick = { viewModel.onAccelerationModeChanged("CPU") },
                            label = { Text("CPU") },
                            modifier = Modifier.weight(1f),
                            leadingIcon = if (uiState.accelerationMode == "CPU") {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                        FilterChip(
                            selected = uiState.accelerationMode == "GPU",
                            onClick = { viewModel.onAccelerationModeChanged("GPU") },
                            label = { Text("GPU") },
                            modifier = Modifier.weight(1f),
                            leadingIcon = if (uiState.accelerationMode == "GPU") {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Max Tokens Input
                    OutlinedTextField(
                        value = uiState.maxTokens.toString(),
                        onValueChange = {
                            try {
                                val value = it.toInt()
                                viewModel.onMaxTokensChanged(value)
                            } catch (e: NumberFormatException) {
                                // Invalid number
                            }
                        },
                        label = { Text("Max Tokens") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { /* Dismiss keyboard if needed */ }
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // System Prompt
                    OutlinedTextField(
                        value = uiState.systemPrompt,
                        onValueChange = { viewModel.onSystemPromptChanged(it) },
                        label = { Text("System Prompt") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp),
                        maxLines = 6,
                        minLines = 4
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.saveSettings()
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .weight(0.6f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Settings")
                }

                OutlinedButton(
                    onClick = { onNavigateBack() },
                    modifier = Modifier
                        .weight(0.4f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

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
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
