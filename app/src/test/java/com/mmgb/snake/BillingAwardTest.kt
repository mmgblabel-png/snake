package com.mmgb.snake

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BillingAwardTest {
  @Test
  fun `awardForProducts returns empty when zero purchases`() {
    val result = BillingManager.awardForProducts(0,0)
    assertTrue(result.isEmpty())
  }
  @Test
  fun `awardForProducts booster pack yields exactly 3 standard boosters`() {
    val result = BillingManager.awardForProducts(1,0)
    val total = result.values.sum()
    assertEquals(3, total, "One pack should yield 3 boosters")
    // Ensure only standard booster ids present
    assertTrue(result.keys.all { it in listOf(
      BillingManager.BOOSTER_START_LENGTH,
      BillingManager.BOOSTER_SCORE_MULT,
      BillingManager.BOOSTER_EXTRA_TIME
    ) })
  }
  @Test
  fun `awardForProducts super boost yields shield booster`() {
    val result = BillingManager.awardForProducts(0,1)
    assertEquals(1, result[BillingManager.BOOSTER_SUPER_SHIELD], "One super should yield 1 shield")
  }
  @Test
  fun `awardForProducts multiple packs accumulate boosters`() {
    val result = BillingManager.awardForProducts(2,0)
    val total = result.values.sum()
    assertEquals(6, total, "Two packs should yield 6 boosters")
  }
  @Test
  fun `awardForProducts mixed purchases combine counts`() {
    val result = BillingManager.awardForProducts(2,2)
    val totalStandard = result.filterKeys { it != BillingManager.BOOSTER_SUPER_SHIELD }.values.sum()
    val shieldCount = result[BillingManager.BOOSTER_SUPER_SHIELD] ?: 0
    assertEquals(6, totalStandard, "Two packs -> 6 standard boosters")
    assertEquals(2, shieldCount, "Two supers -> 2 shields")
  }
}

