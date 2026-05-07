package com.zeerd.textadventure.ui.main.components

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

import androidx.compose.ui.res.stringResource
import com.zeerd.textadventure.R

/**
 * 聊天输入栏组件。
 * 包含一个多行文本输入框和一个发送按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    onSend: (String) -> Unit, // 点击发送时的回调
    value: String, // 当前输入框的值
    onValueChange: (String) -> Unit, // 值改变时的回调
    placeholder: String = stringResource(R.string.chat_placeholder), // 占位符文本
    enabled: Boolean = true, // 是否可用
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Box(modifier = modifier) {
        // 文本输入框
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

        // 发送按钮容器
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
                    contentDescription = stringResource(R.string.send),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
