package com.example.notes.util

import com.example.notes.data.FlashcardEntity

/**
 * SM-2 间隔重复调度。
 *
 * 评分沿用 SM-2 的 0-5 分制, UI 只暴露 4 档:
 * - Again(1): 遗忘, 重置连续答对次数, 10 分钟后重来;
 * - Hard(3): 勉强想起, 间隔按 0.8 收缩;
 * - Good(4): 正常, 标准 SM-2 间隔;
 * - Easy(5): 轻松, 间隔再放大 1.3 倍, 难度因子略升。
 */
object Sm2Scheduler {

    const val QUALITY_AGAIN = 1
    const val QUALITY_HARD = 3
    const val QUALITY_GOOD = 4
    const val QUALITY_EASY = 5

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val AGAIN_DELAY_MS = 10L * 60 * 1000
    private const val MAX_INTERVAL_DAYS = 365.0

    fun review(card: FlashcardEntity, quality: Int, now: Long = System.currentTimeMillis()): FlashcardEntity {
        val q = quality.coerceIn(0, 5)
        // SM-2 难度因子公式, 下限 1.3
        val newEase = (card.easeFactor + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)))
            .coerceAtLeast(1.3)

        if (q < 3) {
            return card.copy(
                intervalDays = 1.0,
                repetitions = 0,
                lapses = card.lapses + 1,
                easeFactor = newEase,
                dueAt = now + AGAIN_DELAY_MS,
                lastReviewedAt = now
            )
        }

        val repetitions = card.repetitions + 1
        val base = when (repetitions) {
            1 -> 1.0
            2 -> 6.0
            else -> card.intervalDays * newEase
        }
        val interval = when (q) {
            QUALITY_HARD -> (base * 0.8).coerceAtLeast(1.0)
            QUALITY_EASY -> (base * 1.3).coerceAtLeast(1.0)
            else -> base.coerceAtLeast(1.0)
        }.coerceAtMost(MAX_INTERVAL_DAYS)

        val ease = if (q == QUALITY_EASY) (newEase + 0.05).coerceAtMost(3.0) else newEase

        return card.copy(
            intervalDays = interval,
            repetitions = repetitions,
            easeFactor = ease,
            dueAt = now + (interval * DAY_MS).toLong(),
            lastReviewedAt = now
        )
    }
}
