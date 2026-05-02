package com.fantacy.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantacy.assistant.SetupState
import com.fantacy.assistant.viewModel.SetupViewModel
import com.fantacy.assistant.ui.common.InputField
import com.fantacy.assistant.ui.theme.SkyBlue

@Composable
fun SetupScreen(vm: SetupViewModel, token: String, onLogout: () -> Unit, onComplete: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(1) }
    val state = vm.setupState
    val scrollState = rememberScrollState()
    val totalSteps = 6

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // 🌟 顶部栏：增加退出登录按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("设定进度: $currentStep / $totalSteps", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onLogout) {
                Text("退出登录", color = Color.Gray)
            }
        }

        LinearProgressIndicator(
            progress = { currentStep / totalSteps.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            color = SkyBlue
        )

        Column(modifier = Modifier.weight(1f).verticalScroll(scrollState).padding(vertical = 16.dp)) {
            when (currentStep) {
                1 -> StepSection("用户基础信息") {
                    InputField("您的昵称 (必填)*", state.user_nickname) { input -> vm.updateState { it.copy(user_nickname = input) } }
                    InputField("您的性别", state.user_gender) { input -> vm.updateState { it.copy(user_gender = input) } }
                }
                2 -> StepSection("用户补充信息") {
                    InputField("您的职业", state.user_occupation) { input -> vm.updateState { it.copy(user_occupation = input) } }
                    InputField("您的生日 (如: 2000-05-20)", state.user_birthday) { input -> vm.updateState { it.copy(user_birthday = input) } }
                    InputField("您的MBTI", state.user_mbti) { input -> vm.updateState { it.copy(user_mbti = input) } }
                }
                3 -> StepSection("用户性格与喜好") {
                    InputField("爱好(如：篮球)", state.user_hobbies) { input -> vm.updateState { it.copy(user_hobbies = input) } }
                    InputField("聊天风格(如：自然)", state.user_communication_style) { input -> vm.updateState { it.copy(user_communication_style = input) } }
                    InputField("个人目标", state.user_goals) { input -> vm.updateState { it.copy(user_goals = input) } }
                }
                4 -> StepSection("用户禁忌与简介") {
                    InputField("聊天禁忌", state.user_taboos) { input -> vm.updateState { it.copy(user_taboos = input) } }
                    InputField("个人简介", state.user_bio, isLong = true) { input -> vm.updateState { it.copy(user_bio = input) } }
                }
                5 -> StepSection("助手基础设定") {
                    InputField("助手名字 (必填)*", state.assistant_name) { input -> vm.updateState { it.copy(assistant_name = input) } }
                    InputField("助手性别", state.assistant_gender) { input -> vm.updateState { it.copy(assistant_gender = input) } }
                    InputField("助手身份(如：伙伴)", state.assistant_role_identity) { input -> vm.updateState { it.copy(assistant_role_identity = input) } }
                }
                6 -> StepSection("助手性格与背景") {
                    InputField("性格标签(如：傲娇)(必填)*", state.assistant_personality_tags) { input -> vm.updateState { it.copy(assistant_personality_tags = input) } }
                    InputField("说话语气(如：撒娇)", state.assistant_speaking_style) { input -> vm.updateState { it.copy(assistant_speaking_style = input) } }
                    InputField("擅长领域", state.assistant_expertise) { input -> vm.updateState { it.copy(assistant_expertise = input) } }
                    InputField("价值观(如：积极)", state.assistant_values_system) { input -> vm.updateState { it.copy(assistant_values_system = input) } }
                    InputField("背景故事", state.assistant_background_story, isLong = true) { input -> vm.updateState { it.copy(assistant_background_story = input) } }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { currentStep-- }, enabled = currentStep > 1) { Text("上一步") }
            Button(
                onClick = {
                    if (currentStep < totalSteps) currentStep++
                    else vm.submitFullProfiles(token, onComplete)
                },
                enabled = when (currentStep) {
                    1 -> state.user_nickname.isNotBlank()
                    5 -> state.assistant_name.isNotBlank()
                    6 -> state.assistant_personality_tags.isNotBlank()
                    else -> true
                }
            ) {
                Text(if (currentStep == totalSteps) "完成设置" else "下一步")
            }
        }
    }
}

@Composable
fun StepSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        content()
    }
}