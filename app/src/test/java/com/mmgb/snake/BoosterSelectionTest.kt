package com.mmgb.snake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoosterSelectionTest {
    private val catalog = boosterCatalog()

    @Test fun testSelectUpToThreeStandard() {
        var selected = emptySet<String>()
        val std = catalog.filter { !it.isSuper }.take(3)
        std.forEach { def ->
            assertTrue(canSelectBooster(selected, def, catalog))
            selected = selected + def.id
        }
        // Fourth standard should be blocked
        val fourth = std.first() // reuse first (already selected allowed)
        assertTrue(canSelectBooster(selected, fourth, catalog)) // deselect allowed
        val extra = BoosterDef("extra_std", "Extra", "", 0, false, BoosterKind.START_LENGTH)
        assertFalse(canSelectBooster(selected, extra, catalog))
    }

    @Test fun testSuperBoosterLimit() {
        var selected = emptySet<String>()
        val superDef = catalog.first { it.isSuper }
        assertTrue(canSelectBooster(selected, superDef, catalog))
        selected = selected + superDef.id
        // Second super (hypothetical) disallowed
        val anotherSuper = BoosterDef("another_super", "Another", "", 0, true, superKind = SuperBoosterKind.SHIELD)
        assertTrue(canSelectBooster(selected, superDef, catalog)) // deselect allowed
        assertFalse(canSelectBooster(selected, anotherSuper, catalog))
    }
}
