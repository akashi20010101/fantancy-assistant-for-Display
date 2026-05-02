package com.fantacy.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fantacy.assistant.ui.common.AvatarComp
import com.fantacy.assistant.ui.common.CodeInput
import com.fantacy.assistant.ui.common.PasswordInput
import com.fantacy.assistant.ui.common.TransparentInput
import com.fantacy.assistant.ui.theme.BrightBg
import com.fantacy.assistant.ui.theme.SkyBlue
import com.fantacy.assistant.ui.theme.TextMain
import com.fantacy.assistant.viewModel.AuthViewModel

@Composable
fun AuthScreen(vm: AuthViewModel, onLoginSuccess: () -> Unit) {
    // 🌟 核心：记录滚动状态，用于解决键盘遮挡问题
    val scrollState = rememberScrollState()

    // 🌟 核心：在最外层 Box 增加 imePadding()，当键盘弹出时会自动留出间距
    Box(modifier = Modifier
        .fillMaxSize()
        .background(BrightBg)
        .imePadding()
    ) {
        Surface(
            modifier = Modifier.align(Alignment.Center).padding(24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            // 🌟 核心：给 Column 增加 verticalScroll 属性
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(scrollState), // 允许垂直滚动
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AvatarComp(isUser = false, size = 80)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = when {
                        vm.isResetMode -> "找回密码"
                        vm.isRegisterMode -> "新用户注册"
                        else -> "欢迎回来"
                    },
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextMain
                )

                // 🌟 状态消息显示区域（包含成功和错误提示）
                if (vm.authErrorMessage.isNotEmpty()) {
                    Text(
                        text = vm.authErrorMessage,
                        // 逻辑：包含"成功"或"发送"字样显示绿色，否则显示红色
                        color = if (vm.authErrorMessage.contains("成功") || vm.authErrorMessage.contains("发送"))
                            Color(0xFF4CAF50) else Color.Red,
                        fontSize = 14.sp, // 稍微调大一点点更清晰
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 邮箱输入
                TransparentInput(vm.username, { vm.username = it }, "邮箱地址")
                Spacer(modifier = Modifier.height(8.dp))

                // 密码输入
                PasswordInput(
                    value = vm.password,
                    onValueChange = { vm.password = it },
                    label = if (vm.isResetMode) "新密码" else "密码",
                    visible = vm.passwordVisible,
                    onToggleVisible = { vm.passwordVisible = !vm.passwordVisible }
                )

                // 记住密码 Checkbox（仅在登录模式显示）
                if(!vm.isRegisterMode && !vm.isResetMode){
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = vm.rememberPassword,
                            onCheckedChange = { vm.rememberPassword = it },
                            colors = CheckboxDefaults.colors(checkedColor = SkyBlue)
                        )
                        Text("记住密码", fontSize = 14.sp, color = Color.Gray)
                    }
                }

                // 注册/重置模式下的额外输入框
                if (vm.isRegisterMode || vm.isResetMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PasswordInput(
                        value = vm.confirmPassword,
                        onValueChange = { vm.confirmPassword = it },
                        label = "确认密码",
                        visible = vm.passwordVisible,
                        onToggleVisible = { vm.passwordVisible = !vm.passwordVisible }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CodeInput(
                        value = vm.verifyCode,
                        onValueChange = { if (it.length <= 6) vm.verifyCode = it },
                        onSend = { vm.sendVerificationCode() },
                        isSending = vm.isCodeSending,
                        enabled = vm.canSendCode,
                        buttonText = if (vm.codeCooldown > 0) "${vm.codeCooldown}s后重发" else "获取验证码"
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 主按钮（登录/注册/重置）
                Button(
                    onClick = { vm.handleAuth(onLoginSuccess) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                    shape = RoundedCornerShape(25.dp),
                    enabled = !vm.isAuthLoading
                ) {
                    if (vm.isAuthLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text(
                            text = if (vm.isResetMode) "确认重置" else if (vm.isRegisterMode) "立即注册" else "登录",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 底部切换模式按钮
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { vm.toggleMode() }) {
                        Text(text = if (vm.isRegisterMode || vm.isResetMode) "返回登录" else "注册账号", color = Color.Gray)
                    }
                    if (!vm.isRegisterMode && !vm.isResetMode) {
                        TextButton(onClick = { vm.toggleToReset() }) {
                            Text("找回密码", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}