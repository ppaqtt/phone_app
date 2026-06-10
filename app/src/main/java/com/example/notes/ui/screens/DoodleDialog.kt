package com.example.notes.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.notes.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 涂鸦白板 Dialog。
 * 用户用手指在白色画布上画线, 点击保存后导出 PNG 到 app 私有目录,
 * 通过 [onDone] 回调把 file:// URI 传回给编辑器 (调用方负责写入 imageUris)。
 */
@Composable
fun DoodleDialog(
    onDismiss: () -> Unit,
    onDone: (Uri) -> Unit
) {
    val paths: SnapshotStateList<Path> = remember { mutableStateListOf() }
    val redoStack: SnapshotStateList<Path> = remember { mutableStateListOf() }
    var currentPath by remember { mutableStateOf(Path()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var brushColor by remember { mutableStateOf(Color.Black) }
    var brushWidth by remember { mutableStateOf(8f) }
    val context = LocalContext.current
    // P4: 协程作用域, 用于把导出操作切到 IO 线程
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("涂鸦", modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    if (paths.isNotEmpty()) {
                        redoStack.add(paths.last())
                        paths.removeAt(paths.lastIndex)
                    }
                }) { Icon(Icons.Filled.Undo, contentDescription = "撤销") }
                IconButton(onClick = {
                    if (redoStack.isNotEmpty()) {
                        paths.add(redoStack.last())
                        redoStack.removeAt(redoStack.lastIndex)
                    }
                }) { Icon(Icons.Filled.Redo, contentDescription = "重做") }
                IconButton(onClick = {
                    paths.clear(); redoStack.clear(); currentPath = Path()
                }) { Icon(Icons.Filled.Clear, contentDescription = "清空") }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                // 画布
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPath = Path().apply { moveTo(offset.x, offset.y) }
                                    },
                                    onDrag = { change, _ ->
                                        currentPath.lineTo(change.position.x, change.position.y)
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        paths.add(currentPath)
                                        currentPath = Path()
                                    }
                                )
                            }
                    ) {
                        canvasSize = IntSize(size.width.toInt(), size.height.toInt())
                        paths.forEach { path ->
                            drawPath(
                                path = path,
                                color = brushColor,
                                style = Stroke(width = brushWidth, cap = StrokeCap.Round)
                            )
                        }
                        drawPath(
                            path = currentPath,
                            color = brushColor,
                            style = Stroke(width = brushWidth, cap = StrokeCap.Round)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 画笔颜色选择 (用 clickable 而非 awaitPointerEventScope 避免编译问题)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        Color.Black,
                        Color.Red,
                        Color(0xFFE6B800),
                        Color(0xFF4CAF50),
                        Color(0xFF2196F3)
                    ).forEach { c ->
                        val selected = c == brushColor
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) Color(0xFF6750A4) else Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable { brushColor = c }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(4f, 8f, 14f, 22f).forEach { w ->
                        Icon(
                            Icons.Filled.Brush,
                            contentDescription = "粗细 $w",
                            tint = if (w == brushWidth) Color(0xFF6750A4) else Color.Gray,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { brushWidth = w }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = canvasSize.width
                val h = canvasSize.height
                val allPaths = paths.toList() + listOf(currentPath)
                val colorArgb = brushColor.toArgb()
                val strokeWidth = brushWidth
                // P4: 切到 Dispatchers.IO 异步生成 PNG, 避免主线程 OOM/ANR
                scope.launch {
                    val resultUri = withContext(Dispatchers.IO) {
                        if (w <= 0 || h <= 0 || paths.isEmpty()) {
                            null
                        } else {
                            runCatching {
                                // 限制最大尺寸 2048x2048, 防止 OOM
                                val safeW = w.coerceAtMost(2048)
                                val safeH = h.coerceAtMost(2048)
                                val bmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
                                val androidCanvas = Canvas(bmp)
                                androidCanvas.drawColor(android.graphics.Color.WHITE)
                                val paint = Paint().apply {
                                    isAntiAlias = true
                                    strokeCap = Paint.Cap.ROUND
                                    strokeWidth = strokeWidth
                                    color = colorArgb
                                    style = Paint.Style.STROKE
                                }
                                allPaths.forEach { p ->
                                    val androidPath = android.graphics.Path()
                                    androidPath.set(p.asAndroidPath())
                                    androidCanvas.drawPath(androidPath, paint)
                                }
                                val file = ImageUtils.createImageFile(context)
                                FileOutputStream(file).use {
                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                                }
                                bmp.recycle()
                                ImageUtils.getUriForFile(context, file)
                            }.getOrNull()
                        }
                    }
                    if (resultUri != null) onDone(resultUri)
                    onDismiss()
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
