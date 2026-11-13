package com.example.notes.util

import java.util.Locale

/** 转写文本的一行 (带相对音频开头的时间戳) */
data class TranscriptLine(
    val timeMs: Long,
    val text: String
)

/**
 * 语音附件: 录音文件 + 时长 + 可点击跳转的转写行。
 *
 * 存储格式 (直接写在笔记正文里, 随笔记一起保存/导出):
 *
 * ```
 * 🎙️ [语音](file:///data/user/0/.../audio/rec_20261113_120000.m4a|12345)
 * > 时长 00:12
 * > 转写:
 * > [00:00] 第一句
 * > [00:05] 第二句
 * ```
 *
 * 选择内嵌到正文而不是新增数据库表:
 * 1) 老数据零迁移, 备份 JSON 也是纯文本, 换设备不会丢结构;
 * 2) 用户可以直接看到/编辑转写内容, 符合"笔记"的直觉;
 * 3) 删除附件 = 删除这段文本, 撤销/重做天然支持。
 */
data class VoiceAttachment(
    val uri: String,
    val durationMs: Long,
    val transcript: List<TranscriptLine>,
    /** 匹配到的原始文本块 (含 `>` 开头的转写行), 用于精确替换/删除 */
    val marker: String
)

object VoiceAttachmentParser {

    /** 头部: 🎙️ [语音](uri|durationMs)。uri 里不会出现 `|` / `)` */
    private val HEADER = Regex("🎙️ \\[语音\\]\\(([^|()]+)\\|(\\d+)\\)")

    /** 转写行: > [mm:ss] 文本 */
    private val TRANSCRIPT_LINE = Regex("^>\\s*\\[(\\d{1,3}):(\\d{2})\\]\\s?(.*)$")

    private val LEGACY_AUDIO = Regex("\\[音频\\]\\(([^)]+)\\)")

    /** 解析正文里所有语音附件 (按出现顺序) */
    fun parseAll(content: String): List<VoiceAttachment> {
        if (!content.contains("🎙️ [语音]")) return emptyList()
        val matches = HEADER.findAll(content).toList()
        if (matches.isEmpty()) return emptyList()
        val result = ArrayList<VoiceAttachment>(matches.size)
        matches.forEach { match ->
            val start = match.range.first
            // 只吃紧随其后的 `>` 引用行, 遇到普通正文立即停止, 避免删除时误删后续内容
            var cursor = match.range.last + 1
            if (cursor < content.length && content[cursor] == '\n') cursor++
            while (cursor < content.length) {
                val lineEnd = content.indexOf('\n', cursor).let { if (it < 0) content.length else it }
                val line = content.substring(cursor, lineEnd)
                if (!line.trimStart().startsWith(">")) break
                cursor = if (lineEnd < content.length) lineEnd + 1 else content.length
            }
            val block = content.substring(start, cursor).trimEnd()
            val uri = match.groupValues[1]
            val duration = match.groupValues[2].toLongOrNull() ?: 0L
            val lines = ArrayList<TranscriptLine>()
            block.lineSequence().drop(1).forEach { raw ->
                val m = TRANSCRIPT_LINE.matchEntire(raw.trim())
                if (m != null) {
                    val minutes = m.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = m.groupValues[2].toLongOrNull() ?: 0L
                    val text = m.groupValues[3].trim()
                    if (text.isNotEmpty()) {
                        lines.add(TranscriptLine((minutes * 60L + seconds) * 1000L, text))
                    }
                }
            }
            result.add(VoiceAttachment(uri, duration, lines, block))
        }
        return result
    }

    /** 解析旧版文件选择器插入的 `[音频](uri)` 标记 */
    fun parseLegacyAudio(content: String): List<String> =
        LEGACY_AUDIO.findAll(content).map { it.groupValues[1] }.toList()

    /** 生成可写入正文的语音附件块 */
    fun buildMarker(uri: String, durationMs: Long, transcript: List<TranscriptLine>): String {
        val sb = StringBuilder()
        sb.append("🎙️ [语音](").append(uri).append('|').append(durationMs).append(")\n")
        sb.append("> 时长 ").append(formatTime(durationMs))
        if (transcript.isNotEmpty()) {
            sb.append("\n> 转写:")
            transcript.forEach { line ->
                sb.append("\n> [").append(formatTime(line.timeMs)).append("] ").append(line.text)
            }
        }
        return sb.toString()
    }

    /**
     * 把纯文本转写切成带时间戳的行。
     *
     * 系统语音识别不提供词级时间对齐, 这里按"字符数占比"把整段时长分配到各句,
     * 既能实现点击跳转, 又不会假装比实际更精确。
     */
    fun withTimestamps(text: String, durationMs: Long): List<TranscriptLine> {
        val sentences = text.split(Regex("(?<=[。！？!?；;])|\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (sentences.isEmpty()) return emptyList()
        val totalChars = sentences.sumOf { it.length }.coerceAtLeast(1)
        var consumed = 0
        return sentences.map { sentence ->
            val time = if (durationMs <= 0L) 0L else durationMs * consumed / totalChars
            consumed += sentence.length
            TranscriptLine(time, sentence)
        }
    }

    /** 把毫秒格式化为 mm:ss / h:mm:ss */
    fun formatTime(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
