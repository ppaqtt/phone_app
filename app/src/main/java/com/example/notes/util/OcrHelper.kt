package com.example.notes.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * F17: OCR 文字识别 (ML Kit Text Recognition v2)。
 *
 * 设计取舍:
 * 1) 使用 Google ML Kit 中文文本识别 (on-device, 无需网络/API Key)。
 *    需要在 build.gradle.kts 加依赖:
 *    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
 * 2) 大图先缩放到 max 1920px 再识别, 平衡精度与速度/内存。
 * 3) 识别结果按行拼接, 保留换行结构。
 * 4) 不保存临时文件, 直接从 URI 解码 Bitmap。
 */
object OcrHelper {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * 从图片 URI 识别文字, 返回识别到的文本字符串。
     * 失败时抛出异常 (调用方用 runCatching 包裹)。
     */
    suspend fun recognizeText(context: Context, uri: Uri): String {
        val bitmap = loadBitmap(context, uri)
        val scaled = scaleIfNeeded(bitmap)
        val image = InputImage.fromBitmap(scaled, 0)

        return suspendCancellableCoroutine { continuation ->
            val task = recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val result = visionText.text
                    Timber.d("OCR recognized ${result.length} chars from $uri")
                    continuation.resume(result)
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "OCR failed for $uri")
                    continuation.resumeWithException(e)
                }
            continuation.invokeOnCancellation {
                task.cancel()
            }
        }
    }

    /** 从 ContentResolver 加载 Bitmap */
    private fun loadBitmap(context: Context, uri: Uri): Bitmap {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw IllegalArgumentException("无法加载图片: $uri")
    }

    /** 如果任一边超过 1920, 等比缩放到 1920 以内 */
    private fun scaleIfNeeded(bitmap: Bitmap, maxDim: Int = 1920): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = w.toFloat() / h.toFloat()
        val newW: Int
        val newH: Int
        if (w > h) {
            newW = maxDim
            newH = (maxDim / ratio).toInt()
        } else {
            newH = maxDim
            newW = (maxDim * ratio).toInt()
        }
        return bitmap.scale(newW, newH)
    }
}
