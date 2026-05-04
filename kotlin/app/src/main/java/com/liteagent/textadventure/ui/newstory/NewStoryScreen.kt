package com.liteagent.textadventure.ui.newstory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 新故事创建界面。
 * 用户可以在此选择背景文件、启动新游戏或查看历史记录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewStoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: NewStoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 进入界面时，重置一些临时的 UI 状态
    LaunchedEffect(Unit) {
        viewModel.onNewStoryDismiss()
    }

    // 当故事成功启动后，自动返回主界面（主界面会观察到最新故事的变化）
    LaunchedEffect(uiState.storyStarted) {
        if (uiState.storyStarted) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            // 如果不在历史模式，显示标准的 TopAppBar
            if (!uiState.showHistory) {
                TopAppBar(
                    title = { Text("New Story") },
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(if (uiState.showHistory) 0.dp else 24.dp)
        ) {
            // 定义文件选择器
            val filePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let { viewModel.onFileSelected(it) }
            }

            // 根据状态决定显示“历史记录”还是“新故事创建”
            if (uiState.showHistory) {
                HistoryScreen(
                    onNavigateBack = { viewModel.hideHistory() },
                    viewModel = viewModel
                )
            } else {
                // 新故事创建界面布局
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Create a New Adventure",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    Text(
                        text = "Upload a background setting file to start your personalized story.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 选择背景文件按钮
                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Select Background Setting",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 查看历史记录按钮
                    Button(
                        onClick = { viewModel.onViewHistory() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Story History")
                    }

                    // 展示当前选中的文件名
                    if (uiState.selectedFileName != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Selected Setting:",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${uiState.selectedFileName}.md",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = androidx.compose.ui.graphics.Color.Green
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 错误提示
                    if (uiState.hasError) {
                        Text(
                            text = uiState.errorMessage ?: "An unknown error occurred",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    // 取消和开始按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onNavigateBack() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = viewModel::onStartStory,
                            modifier = Modifier.weight(1f).height(56.dp),
                            enabled = uiState.canStartStory && !uiState.startingStory,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (uiState.startingStory) "Starting..." else "Start Story")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 故事历史记录列表界面。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: NewStoryViewModel
) {
    // 观察历史故事列表
    val stories by viewModel.storyHistoryRepository.getAllStories().collectAsState(initial = emptyList())
    // 选中的条目 ID 集合
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    // 是否处于批量选择/删除模式
    var isSelectionMode by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (isSelectionMode) "${selectedIds.size} Selected" else "Story History") },
            navigationIcon = {
                IconButton(onClick = {
                    if (isSelectionMode) {
                        isSelectionMode = false
                        selectedIds = emptySet()
                    } else {
                        onNavigateBack()
                    }
                }) {
                    Icon(
                        imageVector = if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                // 如果是选择模式，显示删除按钮
                if (isSelectionMode) {
                    IconButton(onClick = {
                        viewModel.deleteSelectedStories(selectedIds.toList())
                        isSelectionMode = false
                        selectedIds = emptySet()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                    }
                } else if (stories.isNotEmpty()) {
                    // 非选择模式，显示编辑按钮进入选择模式
                    IconButton(onClick = { isSelectionMode = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Selection Mode")
                    }
                }
            }
        )

        if (stories.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp))
                Text("No stories yet", style = MaterialTheme.typography.titleLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stories) { story ->
                    val isSelected = selectedIds.contains(story.id)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) selectedIds - story.id else selectedIds + story.id
                                        if (selectedIds.isEmpty()) isSelectionMode = false
                                    } else {
                                        // 加载选中的故事
                                        viewModel.onLoadStory(story)
                                    }
                                },
                                onLongClick = {
                                    // 长按进入选择模式
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedIds = setOf(story.id)
                                    }
                                }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { isChecked ->
                                        selectedIds = if (isChecked) selectedIds + story.id else selectedIds - story.id
                                        if (selectedIds.isEmpty()) isSelectionMode = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(story.settingTitle, style = MaterialTheme.typography.titleMedium)

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Created: ${dateFormatter.format(Date(story.createdAt))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Active: ${dateFormatter.format(Date(story.lastActive))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // 显示故事开头的预览
                                Text(
                                    story.storyBeginning.take(100) + "...",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2
                                )
                            }
                            if (!isSelectionMode) {
                                IconButton(onClick = { viewModel.deleteStory(story.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
