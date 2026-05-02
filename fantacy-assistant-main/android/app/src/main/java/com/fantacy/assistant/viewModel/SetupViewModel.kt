package com.fantacy.assistant.viewModel

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.fantacy.assistant.NetworkHelper
import com.fantacy.assistant.UserProfile
import com.fantacy.assistant.SetupState

class SetupViewModel(private val baseUrl: String) : ViewModel() {

    private val logPrefix = "fantacy-assistant: SetupViewModel → "

    var setupState by mutableStateOf(SetupState())
        private set

    fun updateState(transformer: (SetupState) -> SetupState) {
        Log.d(logPrefix, "updateState() → 全局更新状态")
        setupState = transformer(setupState)
    }

    fun updateField(onUpdate: (SetupState) -> SetupState) {
        Log.d(logPrefix, "updateField() → 局部更新字段")
        setupState = onUpdate(setupState)
    }

    var uBase by mutableStateOf<UserProfile?>(null)

    // null=检查中, true=需设置, false=已完成
    var needsSetup by mutableStateOf<Boolean?>(null)

    init {
        Log.d(logPrefix, "初始化完成")
    }

    fun fetchUserInfo(token: String) {
        Log.d(logPrefix, "fetchUserInfo() → 开始获取用户资料")
        if (token.isBlank()) {
            needsSetup = false
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = NetworkHelper.makePostRequest("$baseUrl/setup/fetchStatus", JSONObject(), token)
                NetworkHelper.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) { needsSetup = false }
                        return@use
                    }

                    val resText = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(resText)

                    if (jsonResponse.optBoolean("success")) {
                        val dataJson = jsonResponse.optJSONObject("data")
                        withContext(Dispatchers.Main) {
                            if (dataJson != null && !dataJson.optString("nickname").isNullOrBlank()) {
                                uBase = UserProfile(
                                    nickname = dataJson.optString("nickname"),
                                    gender = dataJson.optString("gender", "未知"),
                                    occupation = dataJson.optString("occupation"),
                                    mbti = dataJson.optString("mbti"),
                                    bio = dataJson.optString("bio")
                                )
                                needsSetup = false
                            } else {
                                needsSetup = true
                                uBase = UserProfile()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) { needsSetup = false }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { needsSetup = false }
            }
        }
    }

    fun submitFullProfiles(token: String, onComplete: () -> Unit) {
        val state = setupState
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("user_nickname", state.user_nickname)
                    put("user_gender", state.user_gender)
                    put("user_birthday", state.user_birthday)
                    put("user_occupation", state.user_occupation)
                    put("user_mbti", state.user_mbti)
                    put("user_hobbies", state.user_hobbies)
                    put("user_communication_style", state.user_communication_style)
                    put("user_goals", state.user_goals)
                    put("user_taboos", state.user_taboos)
                    put("user_bio", state.user_bio)
                    put("assistant_name", state.assistant_name)
                    put("assistant_gender", state.assistant_gender)
                    put("assistant_role_identity", state.assistant_role_identity)
                    put("assistant_personality_tags", state.assistant_personality_tags)
                    put("assistant_speaking_style", state.assistant_speaking_style)
                    put("assistant_expertise", state.assistant_expertise)
                    put("assistant_values_system", state.assistant_values_system)
                    put("assistant_background_story", state.assistant_background_story)
                }

                val request = NetworkHelper.makePostRequest("$baseUrl/setup/upsertProfiles", json, token)
                NetworkHelper.client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            needsSetup = false
                            uBase = UserProfile(nickname = state.user_nickname, bio = state.user_bio)
                            onComplete()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(logPrefix, "提交异常：${e.message}")
            }
        }
    }
}