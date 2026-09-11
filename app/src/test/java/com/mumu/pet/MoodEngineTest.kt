package com.mumu.pet

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * MoodEngine 的行为契约。纯函数，不依赖 Android，在 JVM 上直接跑。
 */
class MoodEngineTest {

    private val noon = Instant.parse("2026-05-20T04:00:00Z")

    @Test
    fun deriveMood_energyExhausted_isSleepy() {
        assertEquals(MoodEngine.SLEEPY, MoodEngine.deriveMood(70, 10))
    }

    @Test
    fun deriveMood_lowMood_isSad() {
        assertEquals(MoodEngine.SAD, MoodEngine.deriveMood(20, 80))
    }

    @Test
    fun deriveMood_lowEnergy_isTired() {
        assertEquals(MoodEngine.TIRED, MoodEngine.deriveMood(50, 30))
    }

    @Test
    fun deriveMood_goodMood_isHappy() {
        assertEquals(MoodEngine.HAPPY, MoodEngine.deriveMood(70, 80))
    }

    @Test
    fun settle_neglectedFor8Hours_growsSad() {
        val from = noon.minus(8, ChronoUnit.HOURS)
        val state = PetState(moodValue = 32, energy = 80, lastSeenAt = from)

        val out = MoodEngine.settle(state, noon)

        // 无论白天还是夜里，被冷落 8 小时都会扣心情：32 - 2*(8-6) = 28
        assertEquals(28, out.moodValue)
        assertEquals(MoodEngine.SAD, out.mood)
        assertEquals(noon, out.lastSeenAt)
    }

    @Test
    fun settle_neverSeen_bootstrapsWithoutFailing() {
        val state = PetState(lastSeenAt = null)
        val out = MoodEngine.settle(state, noon)
        assertEquals(noon, out.lastSeenAt)
        assertEquals(state.energy, out.energy)
        assertEquals(state.moodValue, out.moodValue)
    }

    @Test
    fun settle_capsAt24Hours() {
        val from = noon.minus(240, ChronoUnit.HOURS)
        val state = PetState(moodValue = 100, energy = 100, lastSeenAt = from)
        val out = MoodEngine.settle(state, noon)
        // 最长只按 24 小时算：100 - 2*(24-6) = 64
        assertEquals(64, out.moodValue)
    }

    @Test
    fun onTap_threeTimes_makesHappy() {
        var s = PetState(moodValue = 62, energy = 80, intimacy = 10)
        repeat(3) { s = MoodEngine.onTap(s, noon) }

        assertEquals(71, s.moodValue)
        assertEquals(13, s.intimacy)
        assertEquals(77, s.energy)
        assertEquals(MoodEngine.HAPPY, s.mood)
    }

    @Test
    fun onPet_gainsFasterThanOnTap() {
        val base = PetState(moodValue = 50, energy = 80, intimacy = 10)
        val tapped = MoodEngine.onTap(base, noon)
        val petted = MoodEngine.onPet(base, noon)
        assertEquals(53, tapped.moodValue)
        assertEquals(56, petted.moodValue)
        assertEquals(13, petted.intimacy)
    }
}
