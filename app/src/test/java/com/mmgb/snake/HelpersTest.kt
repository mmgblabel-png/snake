package com.mmgb.snake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpersTest {
    @Test fun testRandCellExclusion() {
        val exclude = setOf(Cell(0,0), Cell(1,1))
        repeat(50) {
            val c = randCell(exclude)
            assertFalse("Excluded cell returned", exclude.any { it.x==c.x && it.y==c.y })
            assertTrue(c.x in 0 until 22 && c.y in 0 until 22)
        }
    }
    @Test fun testCurrentIntervalBounds() {
        val effects = mapOf<PowerUp, Long>()
        val base = currentInterval(2, effects) // speedIdx 2
        assertTrue(base in 40..420)
    }
    @Test fun testCurrentIntervalFastSlow() {
        val now = System.currentTimeMillis()+5000
        val effFast = mapOf(PowerUp.FAST to now)
        val effSlow = mapOf(PowerUp.SLOW to now)
        val fast = currentInterval(2, effFast)
        val slow = currentInterval(2, effSlow)
        assertTrue(fast < slow)
    }
    @Test fun testSuperBoosterExclusivity() {
        val catalog = listOf(
            BoosterDef("super_shield","Schild","", 0, true, null, SuperBoosterKind.SHIELD),
            BoosterDef("booster_score_mult","Score","", 0, false, BoosterKind.SCORE_MULT, null)
        )
        val selected = setOf("super_shield")
        val superDef = catalog.first()
        val otherSuperSelectable = canSelectBooster(selected, superDef, catalog)
        assertTrue(otherSuperSelectable) // same booster can remain selected
        // Simulate second super booster (copy) not allowed when different id
        val anotherSuper = BoosterDef("super_alt","Alt","", 0, true, null, SuperBoosterKind.SHIELD)
        assertFalse(canSelectBooster(selected, anotherSuper, catalog + anotherSuper))
    }
}
