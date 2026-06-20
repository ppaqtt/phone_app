package com.qingjian.notes.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingjian.notes.R
import kotlinx.coroutines.delay

/**
 * 启动画面: 居中显示 ic_launcher_source.png (清笺卷轴图标),
 * 紧贴下方显示 app_name (清笺)。
 *
 * 整体淡入, 固定延迟 600ms 后回调 onAnimationComplete。
 * 600ms 是经验值: Room 首次冷启 + 简单 SELECT 远小于此, 慢机也基本够用;
 * 真正慢的极端场景 (上千条笔记) 也只会"闪一下"不会崩溃。
 *
 * P84: 旧版有 `ready: Boolean` 参数但 MainActivity 始终用默认值 true,
 * 该参数变成死代码且误导后人。删除并把淡入 / 淡出 / 回调合并为一个 LaunchedEffect。
 */
@Composable
fun SplashScreen(onAnimationComplete: () -> Unit) {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400)
        )
        delay(600L)
        onAnimationComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(alpha.value)
        ) {
            // 居中图标: 加载 ic_launcher_source.png
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_source),
                contentDescription = stringResource(id = R.string.app_name),
                modifier = Modifier.size(160.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 紧贴图标下方的软件名
            Text(
                text = stringResource(id = R.string.app_name),
                color = Color(0xFF2E5D5A),
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}
