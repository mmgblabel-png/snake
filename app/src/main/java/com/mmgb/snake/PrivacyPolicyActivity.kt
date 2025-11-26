@file:Suppress("MaxLineLength")
package com.mmgb.snake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opt-in edge-to-edge for compatibility back to older versions
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Ensure system bar icon contrast on dark background
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        setContent { PrivacyPolicyScreen() }
    }
}

@Composable
private fun PrivacyPolicyScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Respect system bars (status + nav) when not in immersive or when bars are transient
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        BasicText("Privacy Policy")
        BasicText(
            "We respect your privacy. The app uses Google Mobile Ads (AdMob) which may collect the Advertising ID to serve personalized or non-personalized ads depending on your consent. We do not collect personal information such as name, email, or phone number. Gameplay data (like high scores, settings) is stored locally on your device. In-app purchases are processed by Google Play and are associated with your Google account.",
            modifier = Modifier.padding(top = 12.dp)
        )
        BasicText("Data Collected:", modifier = Modifier.padding(top = 16.dp))
        BasicText("- Advertising ID (by Google Mobile Ads)\n- Crash logs (last crash message locally only)\n- Non-identifiable analytics from ad networks")
        BasicText("Data Sharing:", modifier = Modifier.padding(top = 16.dp))
        BasicText("- Shared with AdMob and Google Play services as needed to deliver ads, purchases, and Play Games features.")
        BasicText("User Choices:", modifier = Modifier.padding(top = 16.dp))
        BasicText("- You can control personalized ads via the consent form shown when required by law.\n- You may change ad consent in device settings or reinstall the app.")
        BasicText("Contact:", modifier = Modifier.padding(top = 16.dp))
        BasicText("For any questions, contact the developer via the email listed on the Play Store listing.")
    }
}
