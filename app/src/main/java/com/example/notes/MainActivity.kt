package com.example.notes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.notes.nav.NotesNavGraph
import com.example.notes.ui.screens.AboutLegalScreen
import com.example.notes.ui.screens.AppLockScreen
import com.example.notes.ui.screens.PermissionIntroScreen
import com.example.notes.ui.screens.SplashScreen
import com.example.notes.ui.theme.NotesAppTheme
import com.example.notes.ui.viewmodel.NotesViewModel
import com.example.notes.ui.viewmodel.ViewModelFactory
import com.example.notes.util.NotificationPermission
import com.example.notes.util.PermissionIntroPrefs
import com.example.notes.util.SecurityChecker
import com.example.notes.util.rememberNotificationPermissionRequest
import com.example.notes.widget.NotesAppWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // F20: 运行时安全检测 (后台线程, 不阻塞 UI)
        lifecycleScope.launch(Dispatchers.IO) {
            val report = SecurityChecker.check(this@MainActivity)
            if (report.hasThreats) {
                Timber.tag("Security").w(
                    "安全威胁检测: %s", report.warnings.joinToString("; ")
                )
            }
        }

        // 透明状态栏 / 导航栏由主题的 windowTranslucentStatus 配置,
        // 不调用 enableEdgeToEdge (它在 activity-ktx 1.8+ 才有)。
        val app = application as NotesApplication
        val factory = ViewModelFactory(app.repository)
        // P80: 去掉 by lazy, 直接在 onCreate 中初始化, 生命周期更清晰
        val viewModel: NotesViewModel = ViewModelProvider(this, factory)[NotesViewModel::class.java]

        setContent {
            NotesAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showSplash by remember { mutableStateOf(true) }
                    var showPermissionIntro by remember { mutableStateOf(false) }
                    // 从 intent.data 解析深链接, host == "privacy" 时跳隐私政策页
                    val pendingLegalUri = remember { parseLegalUri(intent) }
                    var showLegal by remember { mutableStateOf(pendingLegalUri != null) }

                    // F3: 解析桌面小部件的 "新建笔记" / "打开笔记" intent
                    val widgetIntent = remember { parseWidgetIntent(intent) }

                    // Splash 结束后判断是否需要展示权限引导
                    LaunchedEffect(showSplash) {
                        if (!showSplash) {
                            showPermissionIntro = !PermissionIntroPrefs.isShown(this@MainActivity)
                        }
                    }

                    // P95: 启动页结束且权限引导完成后, 若未授通知权限 (Android 13+) 自动弹申请
                    val permRequest = rememberNotificationPermissionRequest()
                    LaunchedEffect(showSplash, showPermissionIntro) {
                        if (!showSplash && !showPermissionIntro && !NotificationPermission.hasPermission(this@MainActivity)) {
                            permRequest.value = true
                        }
                    }

                    if (showLegal && pendingLegalUri != null) {
                        AboutLegalScreen(
                            title = "隐私政策",
                            rawResId = com.example.notes.R.raw.privacy_policy,
                            onBack = { showLegal = false }
                        )
                    } else if (showSplash) {
                        SplashScreen(onAnimationComplete = { showSplash = false })
                    } else if (showPermissionIntro) {
                        PermissionIntroScreen(
                            onComplete = {
                                showPermissionIntro = false
                            }
                        )
                    } else {
                        // F9: 应用锁 gate — 已锁时显示 AppLockScreen, 否则直接进 NavGraph
                        AppLockGate(
                            appLockStore = app.appLockStore,
                            content = {
                                NotesNavGraph(
                                    viewModel = viewModel,
                                    widgetIntent = widgetIntent
                                )
                            }
                        )
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
        // P21: 严格只匹配 /privacy 路径, 不允许 /privacy/xxx
        val isHttpsPrivacy = data.scheme == "https" &&
            data.host == "qing-jian.ppaqtt.com" &&
            data.path == "/privacy"
        return if (isAppPrivacy || isHttpsPrivacy) data else null
    }

    /**
     * F3 + F4: 解析桌面小部件 / 快捷方式发来的 intent。
     * @return WidgetIntent 描述初始目标; null 表示"无特殊要求, 走默认列表"。
     */
    private fun parseWidgetIntent(intent: Intent?): WidgetIntent? {
        if (intent == null) return null
        return when (intent.action) {
            ACTION_NEW_NOTE -> WidgetIntent.NewNote
            ACTION_OPEN_NOTE -> {
                val id = intent.getLongExtra(
                    NotesAppWidget.EXTRA_NOTE_ID, 0L)
                if (id > 0L) WidgetIntent.OpenNote(id) else null
            }
            // F4: 快捷方式入口
            ACTION_OPEN_SEARCH -> WidgetIntent.OpenSearch
            ACTION_OPEN_TRASH -> WidgetIntent.OpenTrash
            else -> {
                // P98-FIX: 未知 action 打日志, 便于排查第三方应用 / 旧版快捷方式
                // 唤起失败的问题 (例如小部件点击没反应), 不打日志的话难以定位。
                if (intent.action != null) {
                    Timber.tag("MainActivity").w("unknown widget intent action: %s", intent.action)
                }
                null
            }
        }
    }

    companion object {
        // F3: 桌面小部件启动 Activity 的两个 action
        const val ACTION_NEW_NOTE = "com.example.notes.action.NEW_NOTE"
        const val ACTION_OPEN_NOTE = "com.example.notes.action.OPEN_NOTE"
        // F4: App 快捷方式启动 Activity 的 action
        const val ACTION_OPEN_SEARCH = "com.example.notes.action.OPEN_SEARCH"
        const val ACTION_OPEN_TRASH = "com.example.notes.action.OPEN_TRASH"
    }
}

/** F3 + F4: 桌面入口对 MainActivity 启动意图的封装, NotesNavGraph 据此决定初始路由
 *
 * P112-FIX: Kotlin 1.8.22 不支持 `data object` (Kotlin 1.9+ 才有), 改用 `object`。
 * 影响: 失去自动生成的 toString/equals/hashCode, 但 sealed interface 的子类型
 * 用单例 object 引用 (WidgetIntent.NewNote 等) 不依赖这些, 业务无副作用。 */
sealed interface WidgetIntent {
    object NewNote : WidgetIntent
    data class OpenNote(val noteId: Long) : WidgetIntent
    object OpenSearch : WidgetIntent
    object OpenTrash : WidgetIntent
}

@androidx.compose.runtime.Composable
fun AppLockGate(
    appLockStore: com.example.notes.util.AppLockStore,
    content: @androidx.compose.runtime.Composable () -> Unit
) {
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var locked by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(appLockStore.shouldShowLock())
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                locked = appLockStore.shouldShowLock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (locked) {
        com.example.notes.ui.screens.AppLockScreen(
            store = appLockStore,
            mode = com.example.notes.ui.screens.Mode.Unlock,
            onSuccess = { locked = false }
        )
    } else {
        content()
    }
}
