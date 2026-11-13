package com.example.notes.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 真·录音附件: 用 [MediaRecorder] 把麦克风音频落到 App 私有目录。
 *
 * 与旧版"模拟 3 秒"的区别:
 * 1) 真实采集, 生成 .m4a (AAC) 文件, 可被 MediaPlayer 播放;
 * 2) 返回实际录音时长, 供附件卡片显示进度和计算转写时间戳;
 * 3) 文件保存在 `filesDir/audio/`, 不依赖外部存储权限, 卸载 App 时自动清理。
 *
 * 只负责"录", 不负责播放/转写 — 那些交给 UI 与 SpeechToTextHelper。
 */
class AudioRecorderHelper(private val context: Context) {

    data class Result(
        val file: File,
        /** 实际录音时长 (毫秒), 用 [SystemClock.elapsedRealtime] 计算, 不受系统时间修改影响 */
        val durationMs: Long
    )

    private var recorder: MediaRecorder? = null
    private var targetFile: File? = null
    private var startedAt = 0L

    val isRecording: Boolean
        get() = recorder != null

    /**
     * 开始录音。
     * @return true = 启动成功; false = 启动失败 (权限被拒 / 麦克风被占用等)
     */
    fun start(): Boolean {
        if (recorder != null) return false
        val dir = File(context.filesDir, "audio").apply { mkdirs() }
        val name = "rec_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".m4a"
        val file = File(dir, name)

        val mr: MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(128_000)
            mr.setAudioSamplingRate(44_100)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            targetFile = file
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            runCatching { mr.reset() }
            runCatching { mr.release() }
            file.delete()
            recorder = null
            targetFile = null
            false
        }
    }

    /**
     * 停止录音。
     * @return 录音结果; null = 没在录 / 文件为空 / 录音时长过短导致 stop 抛异常
     */
    fun stop(): Result? {
        val mr = recorder ?: return null
        val file = targetFile
        val duration = SystemClock.elapsedRealtime() - startedAt
        var stopFailed = false
        return try {
            try {
                mr.stop()
            } catch (e: RuntimeException) {
                // 录音时间太短 (< 1s 左右) 时 MediaRecorder.stop 会抛
                stopFailed = true
            }
            runCatching { mr.release() }
            if (!stopFailed && file != null && file.exists() && file.length() > 0L) {
                Result(file, duration.coerceAtLeast(0L))
            } else {
                file?.delete()
                null
            }
        } catch (e: Exception) {
            runCatching { mr.release() }
            file?.delete()
            null
        } finally {
            recorder = null
            targetFile = null
            startedAt = 0L
        }
    }

    /** 取消录音并删除半成品文件 (退出编辑页 / 权限中途被收回时调用) */
    fun cancel() {
        val mr = recorder
        if (mr != null) {
            runCatching { mr.stop() }
            runCatching { mr.release() }
        }
        targetFile?.delete()
        recorder = null
        targetFile = null
        startedAt = 0L
    }
}
