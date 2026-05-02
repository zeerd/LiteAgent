package com.liteagent.textadventure.ui.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    onSend: (String) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Message...",
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp, max = 120.dp)
                .focusRequester(focusRequester),
            placeholder = { Text(placeholder) },
            maxLines = if (expanded) 5 else 1,
            singleLine = false,
            shape = RoundedCornerShape(28.dp),
            enabled = enabled,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )
        
        Box(
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 8.dp, y = (-24).dp),
            contentAlignment = Alignment.Center
        ) {
            FilledIconButton(
                onClick = {
                    if (value.isNotBlank()) {
                        onSend(value)
                        onValueChange("")
                    }
                    expanded = false
                },
                enabled = enabled && value.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
