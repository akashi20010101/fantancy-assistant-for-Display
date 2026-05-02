package com.fantacy.assistant.ui.common

import android.net.Uri
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fantacy.assistant.ChatMessage
import com.fantacy.assistant.ui.theme.AssistantBubble
import com.fantacy.assistant.ui.theme.SkyBlue
import com.fantacy.assistant.ui.theme.TextMain
import com.fantacy.assistant.ui.theme.UserBubble
import com.fantacy.assistant.R
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.runtime.remember

@Composable
fun TransparentInput(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        textStyle = TextStyle(color = Color.Black),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SkyBlue)
    )
}

@Composable
fun PasswordInput(value: String, onValueChange: (String) -> Unit, label: String, visible: Boolean, onToggleVisible: () -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = SkyBlue)
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.Black,      // 🌟 加深：聚焦时文字颜色
            unfocusedTextColor = Color.Black,    // 🌟 加深：未聚焦时文字颜色
            focusedBorderColor = SkyBlue,
            unfocusedBorderColor = Color.Gray    // 建议边框也给个基础色，避免在纯白背景下看不清
        )
    )
}

@Composable
fun CodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    enabled: Boolean = true, // 🌟 新增：受 AuthViewModel 中的 codeCooldown 控制
    buttonText: String = "获取验证码" // 🌟 新增：动态显示“60s后重发”
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("验证码") },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedBorderColor = SkyBlue
            )
        )
        // 🌟 修改：按钮的启用逻辑结合了发送状态和冷却状态
        TextButton(
            onClick = onSend,
            enabled = enabled && !isSending
        ) {
            Text(
                text = if (isSending) "发送中..." else buttonText,
                color = if (enabled && !isSending) SkyBlue else Color.Gray
            )
        }
    }
}

@Composable
fun InputField(label: String, value: String, isLong: Boolean = false, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        minLines = if (isLong) 3 else 1, shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.Black,   // 🌟 加深
            unfocusedTextColor = Color.Black, // 🌟 加深
            focusedBorderColor = SkyBlue
        )
    )
}

@Composable
fun AvatarComp(isUser: Boolean, size: Int = 36, uri: Uri? = null) {
    if (isUser) {
        if (uri != null) {
            AsyncImage(model = uri, contentDescription = null, modifier = Modifier.size(size.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Box(modifier = Modifier.size(size.dp).background(Color.LightGray, CircleShape), contentAlignment = Alignment.Center) {
                Text("主", color = Color.White, fontSize = (size/2.5).sp)
            }
        }
    } else {
        // 确保你的 res/drawable 文件夹下有 assistant_avatar 图片
        Image(painter = painterResource(id = R.drawable.assistant_avatar), contentDescription = null, modifier = Modifier.size(size.dp).clip(CircleShape).background(SkyBlue), contentScale = ContentScale.Crop)
    }
}

@Composable
fun ChatBubbleItem(msg: ChatMessage, onCopy: () -> Unit, onDelete: () -> Unit) {
    val isUser = msg.role == "user"

    // 🌟 外层 Row 找回头像布局
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        // 如果是助手，左侧显示头像
        if (!isUser) AvatarComp(isUser = false)

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // 🌟 消息气泡：恢复原来的形状和颜色
            Surface(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .widthIn(max = 260.dp),
                shape = RoundedCornerShape(
                    topStart = if (isUser) 12.dp else 2.dp,
                    topEnd = if (isUser) 2.dp else 12.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 12.dp
                ),
                // 🌟 恢复你原本定义的颜色变量
                color = if (isUser) com.fantacy.assistant.ui.theme.UserBubble else com.fantacy.assistant.ui.theme.AssistantBubble,
                tonalElevation = 1.dp
            ) {
                Text(
                    text = msg.content,
                    modifier = Modifier.padding(12.dp),
                    color = com.fantacy.assistant.ui.theme.TextMain
                )
            }

            // 🌟 按钮部分：放在气泡正下方，保持 12.dp 间隔
            if (!msg.isPending) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.ContentCopy, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // 如果是用户，右侧显示头像
        if (isUser) AvatarComp(isUser = true)
    }
}

// 剪贴板工具函数
fun copyToClipboard(context: Context, text: String) {
    // 🌟 显式写全路径可以彻底避免编译器找错类
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    if (clipboard != null) {
        val clip = android.content.ClipData.newPlainText("chat", text)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
    }
}



// 🌟 时间转换组件
@Composable
fun TimeHeader(timestamp: Long) {
    val sdf = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val timeStr = sdf.format(java.util.Date(timestamp))
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text = timeStr, fontSize = 12.sp, color = Color.Gray)
    }
}