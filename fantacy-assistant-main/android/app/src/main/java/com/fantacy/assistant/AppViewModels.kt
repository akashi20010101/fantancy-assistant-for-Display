package com.fantacy.assistant

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.fantacy.assistant.viewModel.AuthViewModel
import com.fantacy.assistant.viewModel.SetupViewModel
import com.fantacy.assistant.viewModel.ChatViewModel
import android.util.Log

class MainViewModel(private val context: Context, private val baseUrl: String) : ViewModel() {

    // 统一日志前缀
    private val logPrefix = "fantacy-assistant: MainViewModel → "

    // 1. 持有三个子 ViewModel 的实例
    init {
        Log.d(logPrefix, "初始化完成")
        Log.d(logPrefix, "baseUrl = $baseUrl")
    }

    val authVm = AuthViewModel(context, baseUrl).apply {
        Log.d(logPrefix, "AuthViewModel 创建成功")
    }
    val setupVm = SetupViewModel(baseUrl).apply {
        Log.d(logPrefix, "SetupViewModel 创建成功")
    }
    val chatVm = ChatViewModel(baseUrl).apply {
        Log.d(logPrefix, "ChatViewModel 创建成功")
    }

    // 2. 全局基础状态映射
    val isLoggedIn get() = authVm.isLoggedIn
    val needsSetup get() = setupVm.needsSetup
    val uBase get() = setupVm.uBase

    // 3. 核心联动逻辑：App 启动或登录成功后，触发资料拉取
    fun initData() {
        Log.d(logPrefix, "initData() 开始执行")

        val isLogin = authVm.isLoggedIn
        val token = authVm.token
        Log.d(logPrefix, "当前登录状态：isLoggedIn = $isLogin")
        Log.d(logPrefix, "当前Token是否有效：${token.isNotBlank()}")

        if (isLogin && token.isNotBlank()) {
            Log.d(logPrefix, "已登录，开始拉取用户资料")
            setupVm.fetchUserInfo(token)
        } else {
            Log.d(logPrefix, "未登录或Token为空，跳过拉取资料")
        }
    }

    // 4. 包装一些跨模块的组合动作：退出登录
    fun handleLogout() {
        Log.d(logPrefix, "handleLogout() 执行：用户退出登录")

        Log.d(logPrefix, "执行 authVm.logout()")
        authVm.logout()

        Log.d(logPrefix, "执行 chatVm.clearHistory()")
        chatVm.clearHistory()

        Log.d(logPrefix, "重置 Setup 状态：needsSetup = false, uBase = null")
        setupVm.needsSetup = false
        setupVm.uBase = null

        Log.d(logPrefix, "退出登录流程完成")
    }

    // 5. 对话联动：发送消息
    fun performSendMessage() {
        Log.d(logPrefix, "performSendMessage() 执行：发送消息")

        val token = authVm.token
        Log.d(logPrefix, "用户Token状态：${if (token.isNotBlank()) "有效" else "为空"}")

        if (token.isBlank()) {
            Log.w(logPrefix, "发送消息失败：Token 为空，用户未登录")
            return
        }

        Log.d(logPrefix, "Token有效，调用 chatVm.sendMessage()")
        chatVm.sendMessage(token)
    }
}