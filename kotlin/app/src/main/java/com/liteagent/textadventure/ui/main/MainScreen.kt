package com.liteagent.textadventure.ui.main

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liteagent.textadventure.model.ChatMessage
import dev.jeziellago.compose.markdowntext.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToNewStory: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberLazyListState()

    // Auto-scroll to bottom when messages change or AI is typing
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
                    IconButton(onClick = { onNavigateToSettings() }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
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
            // Chat messages
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.showWelcome) {
                        item {
                            WelcomeCard()
                        }
                    }

                    // Filter out internal system messages and first user prompt
                    val displayMessages = uiState.messages.filterIndexed { index, message ->
                        val text = message.text
                        // Hide first user message (setting) if it's the very first message
                        val isFirstUserMessage = index == 0 && message.role == ChatMessage.Role.USER

                        // Hide system-like instrumented prompts
                        val isInstrumentedPrompt = text.contains("find the most relevant skill") ||
                                                 text.contains("1. First, find the most relevant skill") ||
                                                 text.contains("You are a text adventure game master")

                        !isFirstUserMessage && !isInstrumentedPrompt
                    }

                    items(displayMessages) { message ->
                        MessageBubble(
                            text = message.text,
                            isUser = message.role == ChatMessage.Role.USER
                        )
                    }

                    // Show streaming AI response
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

                // Loading indicator overlay
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

            // Input area
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

@Composable
fun MessageBubble(
    text: String,
    isUser: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            if (isUser) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                )
            } else {
                MarkdownText(
                    markdown = text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                )
            }
        }
    }
}
