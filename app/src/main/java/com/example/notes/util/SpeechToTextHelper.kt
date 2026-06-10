package com.example.notes.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber

/**
 * F16: 语音转文字 (SpeechRecognizer)。
 *
 * 设计取舍:
 * 1) 使用 Android 系统内置 SpeechRecognizer (免费, 无需网络 API Key),
 *    但需要设备支持 Google 语音服务 (国内设备可能不可用)。
 * 2) 状态通过 Flow 回传, UI 层监听后更新。
 * 3) 一次只识别一段, 不支持"连续听写" (避免电量 / 隐私问题)。
 * 4) 识别结果直接 append 到笔记正文, 不保存录音文件。
 */
class SpeechToTextHelper(private val context: Context) {

    sealed class State {
        object Idle : State()
        object Listening : State()
        data class Partial(val text: String) : State()
        data class Result(val text: String) : State()
        data class Error(val code: Int, val message: String) : State()
    }

    private var recognizer: SpeechRecognizer? = null
    private val _state = Channel<State>(Channel.CONFLATED)
    val state: Flow<State> = _state.receiveAsFlow()

    /** 设备是否支持语音识别 (Google 语音服务) */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(languageTag: String = "zh-CN") {
        stopListening()
        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = rec
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.trySend(State.Listening)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "音频录制失败"
                    SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                    SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别引擎正忙"
                    SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
                    else -> "未知错误 ($error)"
                }
                Timber.e("SpeechToText error: $error -> $msg")
                _state.trySend(State.Error(error, msg))
                destroy()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    _state.trySend(State.Result(text))
                } else {
                    _state.trySend(State.Error(SpeechRecognizer.ERROR_NO_MATCH, "未识别到语音"))
                }
                destroy()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) _state.trySend(State.Partial(text))
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        rec.startListening(intent)
    }

    fun stopListening() {
        try { recognizer?.stopListening() } catch (_: Exception) {}
        destroy()
    }

    private fun destroy() {
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }
}
