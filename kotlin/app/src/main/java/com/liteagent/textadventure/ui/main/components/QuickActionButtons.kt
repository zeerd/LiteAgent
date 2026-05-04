package com.liteagent.textadventure.ui.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liteagent.textadventure.model.QuickAction

/**
 * 快捷动作按钮组组件。
 * 在输入栏上方显示一排常用的指令按钮。
 */
@Composable
fun QuickActionButtons(
    actions: List<QuickAction>, // 动作列表
    onActionSelected: (QuickAction) -> Unit, // 点击回调
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { action ->
            QuickActionButton(
                action = action,
                onSelected = onActionSelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 单个快捷动作按钮。
 */
@Composable
fun QuickActionButton(
    action: QuickAction,
    onSelected: (QuickAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { onSelected(action) },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getIconForAction(action),
                contentDescription = action.label,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = action.label)
        }
    }
}

/**
 * 基础款快捷动作按钮。
 */
@Composable
fun QuickActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(text = text)
        }
    }
}

/**
 * 根据动作 ID 获取对应的图标。
 */
fun getIconForAction(action: QuickAction): androidx.compose.ui.graphics.vector.ImageVector {
    return when (action.id) {
        "continue" -> Icons.Default.FastForward // 继续
        "examine" -> Icons.Default.Search // 检查
        "help" -> Icons.Default.Help // 帮助
        "restart" -> Icons.Default.Replay // 重启
        else -> Icons.Default.Menu // 其他
    }
}
