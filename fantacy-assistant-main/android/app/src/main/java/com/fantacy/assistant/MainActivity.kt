package com.fantacy.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.fantacy.assistant.ui.AuthScreen
import com.fantacy.assistant.ui.MainNavigationWrapper
import com.fantacy.assistant.ui.theme.BASE_URL
import com.fantacy.assistant.ui.theme.BrightBg
import com.fantacy.assistant.ui.theme.FantacyAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // 初始化 MainViewModel
            val viewModel = remember { MainViewModel(applicationContext, BASE_URL) }

            FantacyAssistantTheme {
                Surface(modifier = Modifier.fillMaxSize().imePadding(), color = BrightBg) {
                    if (!viewModel.isLoggedIn) {
                        // 1. 登录页
                        AuthScreen(viewModel.authVm, onLoginSuccess = { viewModel.initData() })
                    } else {
                        // 2. 导航包装器（内含 Setup 和 Chat）
                        MainNavigationWrapper(viewModel)
                    }
                }
            }
        }
    }
}