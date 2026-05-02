package com.fantacy.assistant.viewModel

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantacy.assistant.ChatMessage
import com.fantacy.assistant.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ChatViewModel(private val baseUrl: String) : ViewModel() {
    private val logPrefix = "fantacy-assistant: ChatViewModel → "

    val chatMessages = mutableStateListOf<ChatMessage>()
    var inputText by mutableStateOf("")

    // 🌟 模型选择状态：默认 glm，可选 gemini
    var currentProvider by mutableStateOf("glm")

    // 分页与加载状态
    var isHistoryLoading by mutableStateOf(false)
    var isReachEnd by mutableStateOf(false)

    // 发送状态与中止引用
    var isSending by mutableStateOf(false)
    private var currentCall: okhttp3.Call? = null

    // 初始加载接口
    fun initData(token: String) {
        Log.d(logPrefix, "initData 调用")
        Log.d(logPrefix, "initData → token 是否为空：${token.isBlank()}")
        Log.d(logPrefix, "initData → 消息列表是否为空：${chatMessages.isEmpty()}")

        if (token.isBlank() || chatMessages.isNotEmpty()) {
            Log.d(logPrefix, "initData → 条件不满足，直接返回")
            return
        }

        Log.d(logPrefix, "initData → 开始加载历史记录")
        isReachEnd = false
        loadHistory(token, isLoadMore = false)
    }

    // 核心：加载历史记录（支持分页）
    fun loadHistory(token: String, isLoadMore: Boolean) {
        Log.d(logPrefix, "==================== loadHistory 开始 ====================")
        Log.d(logPrefix, "loadHistory → 是否加载更多：$isLoadMore")
        Log.d(logPrefix, "loadHistory → 是否正在加载：$isHistoryLoading")
        Log.d(logPrefix, "loadHistory → 是否已经到底：$isReachEnd")

        if (token.isBlank() || isHistoryLoading || (isLoadMore && isReachEnd)) {
            Log.d(logPrefix, "loadHistory → 条件不满足，直接返回")
            return
        }

        val beforeCursor = if (isLoadMore) {
            chatMessages.firstOrNull()?.createdAt ?: System.currentTimeMillis()
        } else {
            System.currentTimeMillis()
        }
        Log.d(logPrefix, "loadHistory → 分页游标时间：$beforeCursor")

        isHistoryLoading = true
        Log.d(logPrefix, "loadHistory → 设置 isHistoryLoading = true")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "$baseUrl/chat/history?before=$beforeCursor"
                Log.d(logPrefix, "loadHistory → 请求URL：$url")

                val request = NetworkHelper.makeGetRequest(url, token)
                Log.d(logPrefix, "loadHistory → 发起网络请求")

                NetworkHelper.client.newCall(request).execute().use { response ->
                    Log.d(logPrefix, "loadHistory → 响应码：${response.code}")

                    if (response.isSuccessful) {
                        Log.d(logPrefix, "loadHistory → 请求成功")
                        val resText = response.body?.string() ?: ""
                        Log.d(logPrefix, "loadHistory → 响应数据：$resText")

                        val data = JSONObject(resText).getJSONArray("data")
                        Log.d(logPrefix, "loadHistory → 解析到 ${data.length()} 条记录")

                        val newMsgs = mutableListOf<ChatMessage>()
                        for (i in 0 until data.length()) {
                            val obj = data.getJSONObject(i)
                            newMsgs.add(ChatMessage(
                                role = obj.getString("role"),
                                content = obj.getString("content"),
                                createdAt = obj.getLong("created_at")
                            ))
                        }

                        withContext(Dispatchers.Main) {
                            Log.d(logPrefix, "loadHistory → 加载到 ${newMsgs.size} 条消息")
                            if (newMsgs.size < 20) {
                                isReachEnd = true
                                Log.d(logPrefix, "loadHistory → 不足20条，标记已到底部")
                            } else {
                                isReachEnd = false
                            }

                            if (isLoadMore) {
                                chatMessages.addAll(0, newMsgs)
                                Log.d(logPrefix, "loadHistory → 加载更多，插入顶部")
                            } else {
                                chatMessages.clear()
                                chatMessages.addAll(newMsgs)
                                Log.d(logPrefix, "loadHistory → 首次加载，清空并刷新")
                            }
                        }
                    } else {
                        Log.e(logPrefix, "loadHistory → 请求失败，码：${response.code}")
                        withContext(Dispatchers.Main) { isReachEnd = true }
                    }
                }
            } catch (e: Exception) {
                Log.e(logPrefix, "loadHistory → 异常：${e.message}", e)
                withContext(Dispatchers.Main) { isReachEnd = true }
            } finally {
                withContext(Dispatchers.Main) {
                    isHistoryLoading = false
                    Log.d(logPrefix, "loadHistory → 设置 isHistoryLoading = false")
                    Log.d(logPrefix, "==================== loadHistory 结束 ====================")
                }
            }
        }
    }

    fun stopGeneration() {
        Log.d(logPrefix, "stopGeneration → 中止当前对话请求")
        currentCall?.cancel()
        isSending = false
        currentCall = null
        Log.d(logPrefix, "stopGeneration → 请求已中止，isSending = false")
    }

    fun sendMessage(token: String) {
        Log.d(logPrefix, "==================== sendMessage 开始 ====================")
        val userContent = inputText.trim()
        Log.d(logPrefix, "sendMessage → 用户输入：$userContent")
        Log.d(logPrefix, "sendMessage → 当前模型：$currentProvider")
        Log.d(logPrefix, "sendMessage → 是否正在发送：$isSending")

        if (userContent.isBlank() || token.isBlank() || isSending) {
            Log.d(logPrefix, "sendMessage → 条件不满足，直接返回")
            return
        }

        val now = System.currentTimeMillis()
        val pendingAiId: String = java.util.UUID.randomUUID().toString()
        Log.d(logPrefix, "sendMessage → 生成临时消息ID：$pendingAiId")

        inputText = ""
        isSending = true
        Log.d(logPrefix, "sendMessage → 清空输入框，设置 isSending = true")

        // 插入本地消息
        chatMessages.add(ChatMessage(role = "user", content = userContent, createdAt = now))
        chatMessages.add(ChatMessage(
            role = "assistant",
            content = "...",
            id = pendingAiId,
            createdAt = now + 1,
            isPending = true
        ))
        Log.d(logPrefix, "sendMessage → 本地插入用户消息 + 等待中消息")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(logPrefix, "sendMessage → 构建请求体")
                // 🌟 发送到后端的 provider 标识符
                val json = JSONObject()
                    .put("message", userContent)
                    .put("provider", currentProvider)

                val timezoneId = java.util.TimeZone.getDefault().id
                Log.d(logPrefix, "sendMessage → 用户时区：$timezoneId")

                val request = NetworkHelper.makePostRequest("$baseUrl/chat", json, token)
                    .newBuilder()
                    .addHeader("X-Timezone", timezoneId)
                    .build()

                Log.d(logPrefix, "sendMessage → 发起聊天请求")
                val call = NetworkHelper.client.newCall(request)
                currentCall = call

                call.execute().use { response ->
                    Log.d(logPrefix, "sendMessage → 响应码：${response.code}")
                    if (!response.isSuccessful) throw Exception("HTTP Error: ${response.code}")

                    Log.d(logPrefix, "sendMessage → 开始接收流式数据")
                    val reader = response.body?.byteStream()?.bufferedReader(Charsets.UTF_8) ?: return@use
                    var fullReply = ""

                    reader.forEachLine { line ->
                        if (line.startsWith("data:")) {
                            val data = line.substring(5).trim()

                            // 🌟 1. 拦截结束标识
                            if (data == "[DONE]") return@forEachLine

                            try {
                                val jsonResp = JSONObject(data)

                                // 🌟 2. 统一使用 choices 路径解析 (因为后端已经包装过了)
                                val deltaText = jsonResp.optJSONArray("choices")
                                    ?.optJSONObject(0)
                                    ?.optJSONObject("delta")
                                    ?.optString("content") ?: ""

                                if (deltaText.isNotEmpty()) {
                                    fullReply += deltaText
                                    viewModelScope.launch(Dispatchers.Main) {
                                        val index = chatMessages.indexOfFirst { it.id == pendingAiId }
                                        if (index != -1) {
                                            chatMessages[index] = chatMessages[index].copy(
                                                content = fullReply,
                                                isPending = false
                                            )
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // 忽略非 JSON 数据或解析异常
                                Log.e(logPrefix, "解析行失败: $data", e)
                            }
                        }
                    }
                    Log.d(logPrefix, "sendMessage → 流式接收完成，最终回复：$fullReply")
                }
            } catch (e: Exception) {
                Log.e(logPrefix, "sendMessage → 异常：${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (!(e is java.io.IOException && e.message?.contains("Canceled") == true)) {
                        Log.e(logPrefix, "sendMessage → 显示错误提示")
                        val index = chatMessages.indexOfFirst { it.id == pendingAiId }
                        if (index != -1) {
                            chatMessages[index] = chatMessages[index].copy(
                                content = "系统繁忙，请稍后再试",
                                isPending = false
                            )
                        }
                    } else {
                        Log.d(logPrefix, "sendMessage → 请求被主动取消，不显示错误")
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSending = false
                    currentCall = null
                    Log.d(logPrefix, "sendMessage → 请求结束，isSending = false")
                    Log.d(logPrefix, "==================== sendMessage 结束 ====================")
                }
            }
        }
    }

    fun deleteChatRound(token: String, createdAt: Long) {
        Log.d(logPrefix, "deleteChatRound → 调用删除，时间：$createdAt")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().put("createdAt", createdAt)
                val request = NetworkHelper.makePostRequest("$baseUrl/chat/delete", json, token)
                Log.d(logPrefix, "deleteChatRound → 发起删除请求")

                NetworkHelper.client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(logPrefix, "deleteChatRound → 删除成功")
                        withContext(Dispatchers.Main) {
                            chatMessages.removeAll {
                                it.createdAt == createdAt || it.createdAt == createdAt + 1
                            }
                        }
                    } else {
                        Log.e(logPrefix, "deleteChatRound → 删除失败，码：${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(logPrefix, "deleteChatRound → 异常：${e.message}", e)
            }
        }
    }

    fun clearHistory() {
        Log.d(logPrefix, "clearHistory → 清空所有聊天记录")
        chatMessages.clear()
    }
}