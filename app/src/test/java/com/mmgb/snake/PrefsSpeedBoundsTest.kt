package com.mmgb.snake

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PrefsSpeedBoundsTest {
    @Test fun testCurrentIntervalClamped() {
        val effects = mapOf<PowerUp, Long>()
        // Test out-of-range negative and large indices
        val fast = currentInterval(-5, effects)
        val slow = currentInterval(99, effects)
        assertTrue(fast in 40..420)
        assertTrue(slow in 40..420)
    }
    @Test fun testFastSlowEffectsApplied() {
        val future = System.currentTimeMillis() + 5000
        val fastMap = mapOf(PowerUp.FAST to future)
        val slowMap = mapOf(PowerUp.SLOW to future)
        val base = currentInterval(2, mapOf())
        val fast = currentInterval(2, fastMap)
        val slow = currentInterval(2, slowMap)
        assertTrue(fast < base)
        assertTrue(slow > base)
    }
}
