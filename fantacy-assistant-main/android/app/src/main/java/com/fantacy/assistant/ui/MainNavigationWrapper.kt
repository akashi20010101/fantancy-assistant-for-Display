package com.fantacy.assistant.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fantacy.assistant.MainViewModel
import com.fantacy.assistant.ui.common.AvatarComp
import com.fantacy.assistant.ui.theme.SkyBlue
import kotlinx.coroutines.launch

@Composable
fun MainNavigationWrapper(vm: MainViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("chat") }

    val token = vm.authVm.token

    var showLogoutDialog by remember { mutableStateOf(false) }

    // 1. 登录拦截：如果 Token 为空或未登录，直接显示 AuthScreen
    // 注意：这里使用了你 AuthViewModel 中的 isLoggedIn 状态
    if (!vm.authVm.isLoggedIn || token.isBlank()) {
        AuthScreen(
            vm = vm.authVm,
            onLoginSuccess = {
                // 登录成功后触发背景调查状态获取
                vm.setupVm.fetchUserInfo(vm.authVm.token)
            }
        )
        return
    }

    // 2. 根据背景调查状态进行流转
    when (vm.setupVm.needsSetup) {
        null -> {
            // 状态检查中（App 重启或刚登录时）
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SkyBlue)
            }
            // 自动触发检查
            LaunchedEffect(token) {
                if (token.isNotBlank()) {
                    vm.setupVm.fetchUserInfo(token)
                }
            }
        }
        true -> {
            // 需要进行背景调查设置
            SetupScreen(
                vm = vm.setupVm,
                token = token,
                onLogout = {
                    // 用户在设置页选择退出登录
                    vm.authVm.logout()
                    vm.setupVm.needsSetup = null // 重置状态
                },
                onComplete = {
                    // 设置完成后，ViewModel 会将 needsSetup 改为 false，自动跳转
                }
            )
        }
        false -> {
            // 已完成调查，进入主业务逻辑
            when (currentScreen) {
                "profile" -> ProfileScreen(vm, onBack = { currentScreen = "chat" })
                "chat" -> {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                                Spacer(Modifier.height(48.dp))
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth().clickable {
                                        scope.launch { drawerState.close() }
                                        currentScreen = "profile"
                                    },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AvatarComp(isUser = true, size = 60)
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        // 显示 AuthViewModel 中的用户名
                                        Text(vm.authVm.username, fontWeight = FontWeight.Bold)
                                        Text("查看/编辑资料", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Chat, null) },
                                    label = { Text("智能对话") },
                                    selected = currentScreen == "chat",
                                    onClick = { scope.launch { drawerState.close() } }
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                // 2. 这里的 NavigationDrawerItem 触发弹窗
                                NavigationDrawerItem(
                                    label = { Text("退出登录", color = Color.Red) },
                                    selected = false,
                                    onClick = {
                                        // 点击不再直接退出，而是打开弹窗
                                        showLogoutDialog = true
                                    },
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                // 3. 弹窗组件
                                if (showLogoutDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showLogoutDialog = false },
                                        title = { Text("确认退出") },
                                        confirmButton = { TextButton(onClick = { vm.handleLogout(); showLogoutDialog = false }) { Text("退出", color = Color.Red) } },
                                        dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("取消") } }
                                    )
                                }
                            }
                        }
                    ) {
                        ChatScreen(vm, onOpenDrawer = { scope.launch { drawerState.open() } })
                    }
                }
            }
        }
    }
}