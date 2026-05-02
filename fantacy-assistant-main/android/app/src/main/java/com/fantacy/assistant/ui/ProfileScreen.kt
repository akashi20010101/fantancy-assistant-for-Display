package com.fantacy.assistant.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: MainViewModel, onBack: () -> Unit) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

//    if (showLogoutDialog) {
//        AlertDialog(
//            onDismissRequest = { showLogoutDialog = false },
//            title = { Text("确认退出") },
//            confirmButton = { TextButton(onClick = { vm.handleLogout(); showLogoutDialog = false }) { Text("退出", color = Color.Red) } },
//            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("取消") } }
//        )
//    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认注销") },
            confirmButton = { TextButton(onClick = { vm.authVm.deleteAccount(); showDeleteDialog = false }) { Text("确认注销", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("账号设置") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(40.dp))
            AvatarComp(isUser = true, size = 100)
            Spacer(Modifier.height(20.dp))
            Text(vm.authVm.username, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(40.dp))
            Card(modifier = Modifier.padding(16.dp)) {
//                ListItem(headlineContent = { Text("退出登录") }, leadingContent = { Icon(Icons.Default.ExitToApp, null) }, modifier = Modifier.clickable { showLogoutDialog = true })
                HorizontalDivider()
                ListItem(headlineContent = { Text("注销账号", color = Color.Red) }, leadingContent = { Icon(Icons.Default.Delete, null, tint = Color.Red) }, modifier = Modifier.clickable { showDeleteDialog = true })
            }
        }
    }
}