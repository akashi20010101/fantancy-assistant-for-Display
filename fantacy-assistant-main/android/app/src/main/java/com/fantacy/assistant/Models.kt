package com.fantacy.assistant

data class ChatMessage(
    val role: String,
    val content: String,
    // 🌟 核心修改：由 String 改为 Long，用于存储毫秒时间戳
    val createdAt: Long = System.currentTimeMillis(),
    val isPending: Boolean = false,
    val id: String = java.util.UUID.randomUUID().toString(),
)

data class AuthResponse(
    val token: String? = null,
    val error: String? = null
)

data class SetupState(
    // ========== 用户信息（带默认值） ==========
    val user_nickname: String = "",
    val user_gender: String = "",
    val user_birthday: String = "",
    val user_occupation: String = "",
    val user_mbti: String = "",
    val user_hobbies: String = "",
    val user_communication_style: String = "",
    val user_goals: String = "",
    val user_taboos: String = "",
    val user_bio: String = "",

    // ========== 助手信息（带默认值） ==========
    val assistant_name: String = "",
    val assistant_gender: String = "",
    val assistant_role_identity: String = "",
    val assistant_personality_tags: String = "",
    val assistant_speaking_style: String = "",
    val assistant_expertise: String = "",
    val assistant_values_system: String = "",
    val assistant_background_story: String = ""
)

data class UserProfile(
    val id: Int? = null,
    val userId: Int? = null,
    val nickname: String = "",
    val gender: String = "未知",
    val birthday: String = "未知",
    val age: Int? = null,
    val occupation: String = "自由职业",
    val mbti: String = "未测",
    val hobbies: String = "未知",
    val communicationStyle: String = "自然",
    val goals: String = "暂无",
    val taboos: String = "无",
    val bio: String = "无",
    val avatarUrl: String? = null,
    val updatedAt: String? = null
)