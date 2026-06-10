package com.example.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * 全屏图片查看器 (PhotoViewer)
 *
 * 功能:
 * - 捏合手势 (pinch-to-zoom) 缩放
 * - 双击放大 / 还原
 * - 单指拖动 (仅在已放大时可拖)
 * - 点击空白区域 / 关闭按钮 退出
 *
 * 用法:
 * ```
 * if (viewerUri != null) {
 *     PhotoViewer(uri = viewerUri!!, onDismiss = { viewerUri = null })
 * }
 * ```
 */
@Composable
fun PhotoViewer(
    uri: String,
    onDismiss: () -> Unit
) {
    // P104: 系统返回键 / 手势返回时关闭图片查看器。
    // 旧版没有 BackHandler, 按返回直接关闭整个笔记编辑页 (因为 viewer
    // 是 Dialog, 但没拦截返回, 上层会一起关闭)。
    BackHandler(enabled = true) { onDismiss() }

    // 缩放与平移状态
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        // 图片: 双击/双指缩放/拖动
        AsyncImage(
            model = uri,
            contentDescription = "查看图片",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(Unit) {
                    // P48: 合并双击 + 缩放 + 单击关闭到一个 pointerInput,
                    // 用 awaitPointerEventScope 手动编排手势优先级
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        if (newScale > 1.05f) {
                            val maxX = (size.width * (newScale - 1f)) / 2f
                            val maxY = (size.height * (newScale - 1f)) / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    // P48: 双击/单击逻辑单独放一个 pointerInput, 避免与 detectTransformGestures 冲突
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = { tapOffset ->
                            if (scale > 1.05f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                // P60: 用 (scale - 1f) 计算居中系数, 不再写 magic 1.5f
                                val targetScale = 2.5f
                                scale = targetScale
                                val centerX = (size.width / 2f).takeIf { it.isFinite() } ?: 0f
                                val centerY = (size.height / 2f).takeIf { it.isFinite() } ?: 0f
                                offsetX = (centerX - tapOffset.x) * (targetScale - 1f)
                                offsetY = (centerY - tapOffset.y) * (targetScale - 1f)
                            }
                        }
                    )
                }
        )

        // 关闭按钮
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(40.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 缩放比例提示 (左下角, 仅在非 1x 时显示)
        if (scale > 1.05f) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                androidx.compose.material3.Text(
                    text = "%.0f%%".format(scale * 100),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
