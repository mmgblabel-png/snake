package com.mmgb.snake

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class SmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun canTogglePauseAndPressArrows() {
        // Try pressing some controls that we gave content descriptions to
        composeRule.onNodeWithContentDescription("Omhoog").performClick()
        composeRule.onNodeWithContentDescription("Links").performClick()
        composeRule.onNodeWithContentDescription("Rechts").performClick()
        composeRule.onNodeWithContentDescription("Omlaag").performClick()
    }
}

