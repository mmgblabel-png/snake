package com.mmgb.snake

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoosterSelectionInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private fun seedBoosters(context: Context) {
        context.getSharedPreferences("snake", Context.MODE_PRIVATE).edit().putString(PrefKeys.BOOSTERS,
            "booster_start_length=2;booster_score_mult=2;booster_extra_time=2;super_shield=2"
        ).apply()
    }

    @Test fun selectThreeStandardAndOneSuperBooster() {
        val ctx = composeRule.activity
        seedBoosters(ctx)
        // Relaunch composition to reflect seeded prefs (activity already shows SnakeApp in setContent)
        composeRule.waitForIdle()
        // Open start overlay already visible (running false by default) and select boosters
        composeRule.onNodeWithText("Start+3").performClick()
        composeRule.onNodeWithText("+25% Score").performClick()
        composeRule.onNodeWithText("+20s Tijd").performClick()
        composeRule.onNodeWithText("Schild").performClick()
        composeRule.waitForIdle()
        // Expect four checkmarks (one per selected booster)
        composeRule.onAllNodesWithText("\u2713").assertCountEquals(4)
        // Attempt clicking super again to deselect (should reduce checkmarks to 3)
        composeRule.onNodeWithText("Schild").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("\u2713").assertCountEquals(3)
    }
}
