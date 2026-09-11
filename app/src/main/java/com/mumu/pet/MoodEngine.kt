package com.mumu.pet

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * MuMu 的情绪引擎。
 *
 * 纯函数，不碰网络不碰界面，输入一个状态和一个时间点，输出新状态。
 * 三条线：
 * - 精力 energy：醒着就掉，夜里睡觉回
 * - 心情 moodValue：精力太低会难受，被冷落太久会委屈，被摸会高兴
 * - 亲密度 intimacy：只在真实互动里长，不自然掉
 */
object MoodEngine {

    const val HAPPY = "happy"
    const val IDLE = "idle"
    const val TIRED = "tired"
    const val SAD = "sad"
    const val SLEEPY = "sleepy"

    /** 醒着每小时掉多少精力 */
    private const val ENERGY_LOSS_PER_HOUR = 4.0

    /** 夜里睡觉每小时回多少精力 */
    private const val ENERGY_REGEN_PER_HOUR = 6.0

    /** 冷落超过这么久，心情开始往下走 */
    private const val LONELY_AFTER_HOURS = 6.0

    private const val LONELY_MOOD_LOSS_PER_HOUR = 2.0
    private const val LOW_ENERGY_MOOD_LOSS_PER_HOUR = 3.0

    /** 单次结算最多按 24 小时算，免得出差一周回来发现它掉到底 */
    private const val MAX_SETTLE_HOURS = 24.0

    /** 夜间：23:00 - 06:00 */
    fun isNight(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val hour = instant.atZone(zone).hour
        return hour >= 23 || hour < 6
    }

    /** 把从上次见面到现在这段时间的账算清楚。 */
    fun settle(state: PetState, now: Instant): PetState {
        val from = state.lastSeenAt ?: return state.copy(lastSeenAt = now)

        val rawHours = Duration.between(from, now).toMillis() / 3_600_000.0
        if (rawHours <= 0.0) return state.copy(lastSeenAt = now)
        val hours = rawHours.coerceAtMost(MAX_SETTLE_HOURS)

        var energy = state.energy.toDouble()
        var moodValue = state.moodValue.toDouble()

        if (isNight(now, now.atZone(ZoneId.systemDefault()).zone)) {
            energy += ENERGY_REGEN_PER_HOUR * hours
        } else {
            energy -= ENERGY_LOSS_PER_HOUR * hours
        }
        energy = energy.coerceIn(0.0, 100.0)

        if (energy < 30.0) {
            moodValue -= LOW_ENERGY_MOOD_LOSS_PER_HOUR * hours
        }
        if (hours > LONELY_AFTER_HOURS) {
            moodValue -= LONELY_MOOD_LOSS_PER_HOUR * (hours - LONELY_AFTER_HOURS)
        }
        moodValue = moodValue.coerceIn(0.0, 100.0)

        val e = energy.toInt()
        val m = moodValue.toInt()
        return state.copy(
            moodValue = m,
            energy = e,
            mood = deriveMood(m, e),
            lastSeenAt = now
        )
    }

    /** 轻轻点一下：高兴一点，亲近一点，耗一点精力。 */
    fun onTap(state: PetState, now: Instant): PetState =
        react(state, now, moodGain = 3, intimacyGain = 1, energyCost = 1)

    /** 按住摸摸：高兴得多，亲近得快。 */
    fun onPet(state: PetState, now: Instant): PetState =
        react(state, now, moodGain = 6, intimacyGain = 3, energyCost = 2)

    private fun react(
        state: PetState,
        now: Instant,
        moodGain: Int,
        intimacyGain: Int,
        energyCost: Int
    ): PetState {
        val m = (state.moodValue + moodGain).coerceIn(0, 100)
        val e = (state.energy - energyCost).coerceIn(0, 100)
        return state.copy(
            moodValue = m,
            energy = e,
            intimacy = (state.intimacy + intimacyGain).coerceIn(0, 100),
            mood = deriveMood(m, e),
            lastSeenAt = now
        )
    }

    /** 由心情值和精力推导出表情。 */
    fun deriveMood(moodValue: Int, energy: Int): String = when {
        energy <= 12 -> SLEEPY
        moodValue < 30 -> SAD
        energy <= 35 -> TIRED
        moodValue >= 70 -> HAPPY
        else -> IDLE
    }
}
