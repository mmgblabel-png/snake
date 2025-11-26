package com.mmgb.snake

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class BillingIdempotentTest {
    @Test fun testAwardEmptyWhenNoPacksOrSupers() {
        val awards = BillingManager.awardForProducts(0,0, Random(0))
        assertTrue(awards.isEmpty())
    }
    @Test fun testShieldOnlyFromSupers() {
        val awardsFromPacks = BillingManager.awardForProducts(2,0, Random(1))
        assertFalse(awardsFromPacks.containsKey(BillingManager.BOOSTER_SUPER_SHIELD))
        val awardsFromSupers = BillingManager.awardForProducts(0,3, Random(2))
        assertEquals(3, awardsFromSupers[BillingManager.BOOSTER_SUPER_SHIELD])
        assertTrue(awardsFromSupers.keys.all { it == BillingManager.BOOSTER_SUPER_SHIELD })
    }
    @Test fun testCombinedAwardsCounts() {
        val awards = BillingManager.awardForProducts(3,2, Random(3))
        val total = awards.values.sum()
        assertEquals(3*3 + 2, total) // 3 packs -> 9 boosters + 2 shields
        assertEquals(2, awards[BillingManager.BOOSTER_SUPER_SHIELD])
    }
    @Test fun testDistributionVariety() {
        val awards = BillingManager.awardForProducts(packs = 10, supers = 0, rnd = Random(42))
        val distinctStandard = awards.keys.filter { it != BillingManager.BOOSTER_SUPER_SHIELD }.toSet()
        assertTrue("Expected at least two different standard boosters", distinctStandard.size >= 2)
    }
}
