package com.fantacy.assistant.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fantacy.assistant.MainViewModel
import com.fantacy.assistant.ui.common.AvatarComp
import com.fantacy.assistant.ui.common.copyToClipboard
import com.fantacy.assistant.ui.theme.BrightBg
import com.fantacy.assistant.ui.theme.SkyBlue
import com.fantacy.assistant.viewModel.ChatViewModel
import com.fantacy.assistant.ui.common.ChatBubbleItem
import com.fantacy.assistant.ui.common.TimeHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: MainViewModel, onOpenDrawer: () -> Unit) {
    val chatVm = vm.chatVm
    val token = vm.authVm.token
    var showModelMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        chatVm.initData(token)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { AvatarComp(isUser = false, size = 80) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null, tint = SkyBlue) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 🌟 模型切换下拉框
                    Box {
                        IconButton(onClick = { showModelMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = SkyBlue
                            )
                        }
                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("智谱 GLM-4", fontSize = 14.sp) }, // 🌟 已修改
                                leadingIcon = { Icon(Icons.Default.Bolt, null, tint = Color(0xFFFF9800)) }, // 🌟 更换了图标和颜色
                                onClick = {
                                    chatVm.currentProvider = "glm" // 🌟 已修改为 glm
                                    showModelMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Google Gemini", fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Cloud, null, tint = SkyBlue) },
                                onClick = {
                                    chatVm.currentProvider = "gemini"
                                    showModelMenu = false
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = chatVm.inputText,
                        onValueChange = { chatVm.inputText = it },
                        modifier = Modifier.weight(1f),
                        enabled = true,
                        shape = RoundedCornerShape(24.dp),
                        placeholder = {
                            Text(
                                text = if (chatVm.isSending) "对方正在思考..."
                                else "使用 ${if(chatVm.currentProvider=="gemini") "Gemini" else "GLM-4"} 提问...",
                                fontSize = 14.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedBorderColor = SkyBlue,
                            unfocusedBorderColor = Color.LightGray,
                            cursorColor = SkyBlue
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (chatVm.isSending) chatVm.stopGeneration() else chatVm.sendMessage(token)
                        },
                        modifier = Modifier.size(40.dp).background(
                            if (chatVm.isSending) Color(0xFFFF4D4F) else SkyBlue,
                            CircleShape
                        )
                    ) {
                        Icon(
                            imageVector = if (chatVm.isSending) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        containerColor = BrightBg
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            ChatList(chatVm = chatVm, token = token)
        }
    }
}

@Composable
fun ChatList(chatVm: ChatViewModel, token: String) {
    val messages = chatVm.chatMessages
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var messageToDelete by remember { mutableStateOf<Long?>(null) }
    var isUserScrollingUpToLoad by remember { mutableStateOf(false) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) false
            else visibleItems.first().index == 0
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && messages.isNotEmpty() && !chatVm.isHistoryLoading && !chatVm.isReachEnd && listState.isScrollInProgress) {
            isUserScrollingUpToLoad = true
            chatVm.loadHistory(token, isLoadMore = true)
        }
    }

    LaunchedEffect(messages.size) {
        if (isUserScrollingUpToLoad) {
            isUserScrollingUpToLoad = false
            return@LaunchedEffect
        }
        if (messages.isNotEmpty()) {
            if (chatVm.isSending) listState.scrollToItem(messages.size - 1)
            else listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (messageToDelete != null) {
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除这一轮对话记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    messageToDelete?.let { chatVm.deleteChatRound(token, it) }
                    messageToDelete = null
                }) { Text("删除", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { messageToDelete = null }) { Text("取消") } }
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (chatVm.isHistoryLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SkyBlue)
                }
            }
        }
        itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
            val showTime = index == 0 || (msg.createdAt - messages[index - 1].createdAt > 120000)
            if (showTime) TimeHeader(msg.createdAt)
            ChatBubbleItem(
                msg = msg,
                onCopy = { copyToClipboard(context, msg.content) },
                onDelete = {
                    val baseTs = if (msg.role == "user") msg.createdAt else msg.createdAt - 1
                    messageToDelete = baseTs
                }
            )
        }
    }
}