package com.example.notes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.notes.nav.NotesNavGraph
import com.example.notes.ui.screens.AboutLegalScreen
import com.example.notes.ui.screens.SplashScreen
import com.example.notes.ui.theme.NotesAppTheme
import com.example.notes.ui.viewmodel.NotesViewModel
import com.example.notes.ui.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 透明状态栏 / 导航栏由主题的 windowTranslucentStatus 配置,
        // 不调用 enableEdgeToEdge (它在 activity-ktx 1.8+ 才有)。
        val app = application as NotesApplication
        val factory = ViewModelFactory(app.repository)
        val viewModel: NotesViewModel by lazy {
            ViewModelProvider(this, factory)[NotesViewModel::class.java]
        }

        setContent {
            NotesAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showSplash by remember { mutableStateOf(true) }
                    // 从 intent.data 解析深链接, host == "privacy" 时跳隐私政策页
                    val pendingLegalUri = remember { parseLegalUri(intent) }
                    var showLegal by remember { mutableStateOf(pendingLegalUri != null) }

                    if (showLegal && pendingLegalUri != null) {
                        AboutLegalScreen(
                            title = "隐私政策",
                            rawResId = com.example.notes.R.raw.privacy_policy,
                            onBack = { showLegal = false }
                        )
                    } else if (showSplash) {
                        SplashScreen(onAnimationComplete = { showSplash = false })
                    } else {
                        NotesNavGraph(viewModel = viewModel)
                    }
                }
            }
        }
    }

    /** 解析进入 Activity 的 Intent, 仅识别指向隐私政策的深链接 */
    private fun parseLegalUri(intent: Intent?): Uri? {
        val data: Uri = intent?.data ?: return null
        // app://privacy  或  https://qing-jian.ppaqtt.com/privacy
        val isAppPrivacy = data.scheme == "app" && data.host == "privacy"
        val isHttpsPrivacy = data.scheme == "https" &&
            data.host == "qing-jian.ppaqtt.com" &&
            data.path?.startsWith("/privacy") == true
        return if (isAppPrivacy || isHttpsPrivacy) data else null
    }
}
