package com.qingjian.notes.util

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
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val result = visionText.text
                    // P-FIX-003: 不在日志中输出图片 URI, 避免泄露用户图片路径
                    Timber.d("OCR recognized ${result.length} chars")
                    if (scaled != bitmap) {
                        runCatching { scaled.recycle() }
                    }
                    runCatching { bitmap.recycle() }
                    continuation.resume(result)
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "OCR failed")
                    if (scaled != bitmap) {
                        runCatching { scaled.recycle() }
                    }
                    runCatching { bitmap.recycle() }
                    continuation.resumeWithException(e)
                }
            continuation.invokeOnCancellation {
                // ML Kit Task 没有 cancel() — 通过让监听器捕获异常间接终止
                // (recognizer.process 返回的 Task 内部已绑定 InputImage 生命周期)
            }
        }
    }

    /**
     * 从 ContentResolver 加载 Bitmap, 自动按 [maxDim] 采样压缩。
     * P99-FIX: 先用 inJustDecodeBounds 获取尺寸再采样, 避免超大图直接加载导致 OOM。
     */
    private fun loadBitmap(context: Context, uri: Uri, maxDim: Int = 1920): Bitmap {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            // 第一步: 只读取尺寸, 不加载像素
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(stream, null, options)
            
            // 第二步: 计算采样率
            val w = options.outWidth
            val h = options.outHeight
            var sampleSize = 1
            if (w > maxDim || h > maxDim) {
                val wRatio = kotlin.math.ceil(w.toDouble() / maxDim).toInt()
                val hRatio = kotlin.math.ceil(h.toDouble() / maxDim).toInt()
                sampleSize = maxOf(wRatio, hRatio)
            }
            Timber.d("OCR image ${w}x${h}, sampleSize=$sampleSize")
            
            // 第三步: 重新打开流并按采样率加载
            context.contentResolver.openInputStream(uri)?.use { input ->
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565 // 节省内存
                }
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } ?: throw IllegalArgumentException("无法加载图片: $uri")
    }

    /** 如果任一边超过 1920, 等比缩放到 1920 以内。
     *  返回新 bitmap 时回收原图, 避免大图缩放时短时间持有 2x 内存。*/
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
        val scaled = bitmap.scale(newW, newH)
        if (scaled != bitmap) {
            // 新建的缩放 bitmap 不会与原图共享像素, 立刻回收原图释放 native 内存
            runCatching { bitmap.recycle() }
        }
        return scaled
    }
}
