package com.example.notes

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
                    if (showSplash) {
                        SplashScreen(onAnimationComplete = { showSplash = false })
                    } else {
                        NotesNavGraph(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
