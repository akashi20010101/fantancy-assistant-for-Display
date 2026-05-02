package com.fantacy.assistant.viewModel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.fantacy.assistant.NetworkHelper

class AuthViewModel(private val context: Context, private val baseUrl: String) : ViewModel() {

    private val logPrefix = "fantacy-assistant: AuthViewModel → "
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-z]+$".toRegex()

    // --- 认证状态 ---
    var isLoggedIn by mutableStateOf(!prefs.getString("jwt_token", "").isNullOrBlank())
    var username by mutableStateOf(prefs.getString("saved_username", "") ?: "")

    // 🌟 记住密码相关
    var rememberPassword by mutableStateOf(prefs.getBoolean("remember_password", false))
    var password by mutableStateOf(if (rememberPassword) prefs.getString("saved_password", "") ?: "" else "")

    var confirmPassword by mutableStateOf("")
    var verifyCode by mutableStateOf("")

    // 🌟 验证码冷却相关
    var codeCooldown by mutableIntStateOf(0) // 倒计时秒数
    val canSendCode: Boolean get() = codeCooldown <= 0 && !isCodeSending

    var isRegisterMode by mutableStateOf(false)
    var isResetMode by mutableStateOf(false)
    var isAuthLoading by mutableStateOf(false)
    var isCodeSending by mutableStateOf(false)
    var passwordVisible by mutableStateOf(false)
    var authErrorMessage by mutableStateOf("")

    // 只读 Token 供其他 ViewModel 使用
    val token: String get() = prefs.getString("jwt_token", "") ?: ""

    init {
        Log.d(logPrefix, "初始化完成")
        Log.d(logPrefix, "baseUrl = $baseUrl")
        Log.d(logPrefix, "初始登录状态 = $isLoggedIn")
        Log.d(logPrefix, "初始化：记住密码状态 = $rememberPassword")
    }

    // --- 内部逻辑 ---
    /**
     * 🌟 修改：增加了 forceClear 选项，用于切换模式时彻底清空
     */
    private fun resetInputs(forceClear: Boolean = false) {
        Log.d(logPrefix, "resetInputs() → 清空输入框，forceClear = $forceClear")

        if (forceClear || !rememberPassword) {
            password = ""
        }

        confirmPassword = ""
        verifyCode = ""
        authErrorMessage = ""
    }

    fun toggleMode() {
        Log.d(logPrefix, "toggleMode() → 切换/返回模式")

        // 🌟 核心修改：切换模式时首先强制清空输入框
        resetInputs(forceClear = true)

        if (isResetMode || isRegisterMode) {
            // 如果是从注册或重置模式返回登录模式
            isRegisterMode = false
            isResetMode = false

            // 🌟 返回登录界面后，如果之前勾选了记住密码，则恢复显示
            if (rememberPassword) {
                password = prefs.getString("saved_password", "") ?: ""
            }
        } else {
            // 登录模式切到注册模式
            isRegisterMode = true
        }
    }

    fun toggleToReset() {
        Log.d(logPrefix, "toggleToReset() → 进入重置密码模式")
        // 🌟 切换时强制清空
        resetInputs(forceClear = true)
        isResetMode = true
        isRegisterMode = false
    }

    fun sendVerificationCode() {
        if (!canSendCode) return
        Log.d(logPrefix, "sendVerificationCode() → 开始发送验证码")

        if (!username.matches(emailRegex)) {
            authErrorMessage = "请先输入有效的邮箱地址"
            Log.w(logPrefix, "邮箱格式错误")
            return
        }

        isCodeSending = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().put("email", username)
                val request = NetworkHelper.makePostRequest("$baseUrl/auth/send-code", json)
                NetworkHelper.client.newCall(request).execute().use { response ->
                    val resText = response.body?.string() ?: ""
                    val jsonRes = try { JSONObject(resText) } catch (e: Exception) { JSONObject() }
                    withContext(Dispatchers.Main) {
                        isCodeSending = false
                        if (response.isSuccessful && jsonRes.optBoolean("success")) {
                            startCodeTimer()
                            Log.d(logPrefix, "验证码发送成功")
                            authErrorMessage = "验证码已发送"
                        } else {
                            authErrorMessage = jsonRes.optString("error", "发送失败")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isCodeSending = false
                    authErrorMessage = "网络异常"
                    Log.e(logPrefix, "发送验证码异常", e)
                }
            }
        }
    }

    private fun startCodeTimer() {
        codeCooldown = 60
        viewModelScope.launch {
            while (codeCooldown > 0) {
                kotlinx.coroutines.delay(1000)
                codeCooldown--
            }
        }
    }

    fun handleAuth(onLoginSuccess: () -> Unit) {
        Log.d(logPrefix, "handleAuth() → 开始认证流程")

        val cleanUser = username.trim()
        val cleanPass = password.trim()

        // 即时更新已记录的用户名
        prefs.edit().putString("saved_username", cleanUser).apply()

        if (isRegisterMode || isResetMode) {
            if (!cleanUser.matches(emailRegex)) {
                authErrorMessage = "请输入有效邮箱"
                return
            }
            if (cleanPass.length < 8) {
                authErrorMessage = "密码长度需至少8位"
                return
            }
            if (cleanPass != confirmPassword) {
                authErrorMessage = "两次输入的密码不一致"
                return
            }
            if (verifyCode.isBlank()) {
                authErrorMessage = "请输入验证码"
                return
            }
        }

        authErrorMessage = ""
        isAuthLoading = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = when {
                    isResetMode -> "/auth/reset-password"
                    isRegisterMode -> "/auth/register"
                    else -> "/auth/login"
                }

                val json = JSONObject().put("username", cleanUser).put("password", cleanPass)
                if (isRegisterMode || isResetMode) json.put("code", verifyCode)

                val request = NetworkHelper.makePostRequest(baseUrl + path, json)
                NetworkHelper.client.newCall(request).execute().use { response ->
                    val resText = response.body?.string() ?: ""
                    val jsonRes = try { JSONObject(resText) } catch (e: Exception) { JSONObject() }
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            if (!isRegisterMode && !isResetMode) {
                                // 登录成功
                                val receivedToken = jsonRes.optString("token")
                                if (receivedToken.isNotEmpty()) {
                                    val edit = prefs.edit()
                                        .putString("jwt_token", receivedToken)
                                        .putString("saved_username", cleanUser)
                                        .putBoolean("remember_password", rememberPassword)

                                    if (rememberPassword) {
                                        edit.putString("saved_password", cleanPass)
                                    } else {
                                        edit.remove("saved_password")
                                    }
                                    edit.apply()

                                    isLoggedIn = true
                                    onLoginSuccess()
                                }
                            } else {
                                // 1. 先切换模式
                                isRegisterMode = false
                                isResetMode = false

                                // 2. 先执行重置（这会清空之前的错误信息和输入框）
                                resetInputs(forceClear = true)

                                // 3. 【关键】在重置之后再设置成功消息，这样它就不会被 resetInputs 清空
                                authErrorMessage = "操作成功，请登录"

                                // 4. 如果是记住密码，恢复密码显示
                                if (rememberPassword) {
                                    password = prefs.getString("saved_password", "") ?: ""
                                }
                                Log.d(logPrefix, "状态已重置，显示成功消息")
                            }
                        } else {
                            authErrorMessage = jsonRes.optString("error", "认证失败")
                        }
                        isAuthLoading = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isAuthLoading = false
                    authErrorMessage = "网络异常"
                    Log.e(logPrefix, "认证网络异常", e)
                }
            }
        }
    }

    fun logout() {
        Log.d(logPrefix, "logout() → 用户退出登录")

        // 1. 清空 Token 状态
        prefs.edit().remove("jwt_token").apply()
        isLoggedIn = false

        // 2. 重置输入框，但如果是记住密码，则保留/重新读取密码
        resetInputs(forceClear = false)
        if (rememberPassword) {
            password = prefs.getString("saved_password", "") ?: ""
        }
    }

    fun deleteAccount() {
        Log.d(logPrefix, "deleteAccount() → 开始注销账号")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().put("username", username)
                val request = NetworkHelper.makePostRequest("$baseUrl/auth/delete-account", json, token)
                NetworkHelper.client.newCall(request).execute().use { response ->
                    withContext(Dispatchers.Main) { logout() }
                }
            } catch (e: Exception) {
                Log.e(logPrefix, "注销账号异常", e)
                withContext(Dispatchers.Main) { logout() }
            }
        }
    }
}