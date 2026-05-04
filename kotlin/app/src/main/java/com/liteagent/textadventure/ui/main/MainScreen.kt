package com.liteagent.textadventure.ui.main

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liteagent.textadventure.model.ChatMessage
import dev.jeziellago.compose.markdowntext.MarkdownText
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.delay

/**
 * 游戏主界面，包含聊天消息列表和输入框。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit, // 跳转设置回调
    onNavigateToNewStory: () -> Unit, // 跳转新故事/历史回调
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberLazyListState()

    // 当消息数量增加或 AI 正在输入时，自动滚动到底部
    LaunchedEffect(uiState.messages.size, uiState.currentMessage) {
        if (uiState.messages.isNotEmpty() || !uiState.currentMessage.isNullOrEmpty()) {
            scrollState.animateScrollToItem(
                if (!uiState.currentMessage.isNullOrEmpty()) uiState.messages.size else uiState.messages.size - 1
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Text Adventure") },
                actions = {
                    // 设置按钮
                    IconButton(onClick = { onNavigateToSettings() }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                    // 菜单按钮（跳转到新故事/历史记录）
                    IconButton(onClick = { onNavigateToNewStory() }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "New Story Menu"
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
        ) {
            // 聊天消息展示区域
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 如果是空会话，显示欢迎卡片
                    if (uiState.showWelcome) {
                        item {
                            WelcomeCard()
                        }
                    }

                    // 过滤并处理要显示的消息
                    val displayMessages = uiState.messages.filter { message ->
                        // 不显示系统角色（SYSTEM）的消息，这些通常是内部上下文
                        if (message.role == ChatMessage.Role.SYSTEM) return@filter false

                        // 过滤掉包含指令注入等内部逻辑的消息
                        val isInstrumentedPrompt = message.text.contains("find the most relevant skill") ||
                                                 message.text.contains("1. First, find the most relevant skill") ||
                                                 message.text.contains("You are a text adventure game master")

                        !isInstrumentedPrompt
                    }

                    items(displayMessages) { message ->
                        MessageBubble(
                            text = message.text,
                            isUser = message.role == ChatMessage.Role.USER
                        )
                    }

                    // 显示流式生成的实时 AI 响应
                    val currentMessage = uiState.currentMessage
                    if (uiState.isProcessing && !currentMessage.isNullOrEmpty()) {
                        item {
                            MessageBubble(
                                text = currentMessage,
                                isUser = false
                            )
                        }
                    }
                }

                // AI 正在思考时的加载遮罩
                if (uiState.isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.large),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp).padding(8.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "AI is thinking...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 底部输入区域
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.userInput ?: "",
                    onValueChange = { viewModel.onInputChange(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message...") },
                    maxLines = 4,
                    enabled = !uiState.isProcessing
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 发送按钮
                Button(
                    onClick = { viewModel.sendChatMessage() },
                    enabled = ((uiState.userInput?.isNotEmpty() == true) && !uiState.isProcessing)
                ) {
                    Text("Send")
                }
            }
        }
    }
}

/**
 * 欢迎卡片组件。
 */
@Composable
fun WelcomeCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "⚡ Text Adventure",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome to Text Adventure - an AI-powered interactive storytelling experience.",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Select a story setting to begin your adventure.",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

/**
 * 聊天消息气泡组件，支持长按复制。
 */
@Composable
fun MessageBubble(
    text: String,
    isUser: Boolean
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // 控制复制菜单显示的状态
    var showMenu by remember { mutableStateOf(false) }
    var longPressing by remember { mutableStateOf(false) }

    // 菜单超时自动关闭
    LaunchedEffect(showMenu) {
        if (showMenu && !longPressing) {
            delay(500)
            showMenu = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            longPressing = true
                            showMenu = true
                        },
                        onTap = {
                            longPressing = false
                        }
                    )
                }
                .semantics {
                    contentDescription = if (!isUser) "Message, long press to copy" else "Message"
                }
        ) {
            if (isUser) {
                // 用户消息显示为纯文本
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                )
            } else {
                // AI 响应支持 Markdown 渲染
                MarkdownText(
                    markdown = text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                )
            }
        }

        // 复制内容的下拉菜单
        if (showMenu) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false; longPressing = false },
                modifier = Modifier.padding(4.dp)
            ) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color.Unspecified
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Copy text")
                        }
                    },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(
                            context,
                            "Copied to clipboard",
                            Toast.LENGTH_SHORT
                        ).show()
                        showMenu = false
                        longPressing = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Close") },
                    onClick = { showMenu = false; longPressing = false }
                )
            }
        }
    }
}
