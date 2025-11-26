package com.mmgb.snake

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingAwardsTest {
    @Test fun testBoosterPackAwardsThreeStandardBoosters() {
        val awards = BillingManager.awardForProducts(packs = 1, supers = 0, rnd = Random(1))
        val total = awards.values.sum()
        assertEquals(3, total)
        // All keys must be standard boosters (no super_shield)
        assertTrue(awards.keys.all { it != BillingManager.BOOSTER_SUPER_SHIELD })
    }

    @Test fun testSuperBoostAwardsShield() {
        val awards = BillingManager.awardForProducts(packs = 0, supers = 1, rnd = Random(2))
        assertEquals(1, awards[BillingManager.BOOSTER_SUPER_SHIELD])
    }

    @Test fun testMultipleAwardsAggregate() {
        val awards = BillingManager.awardForProducts(packs = 2, supers = 1, rnd = Random(3))
        val total = awards.values.sum()
        // 2 packs -> 6 boosters + 1 shield = 7 total boosters
        assertEquals(7, total)
        assertEquals(1, awards[BillingManager.BOOSTER_SUPER_SHIELD])
    }
}
