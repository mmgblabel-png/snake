@file:Suppress("TooManyFunctions", "NestedBlockDepth", "TooGenericExceptionCaught", "MaxLineLength")
package com.mmgb.snake

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.GamesSignInClient
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class MainActivity : ComponentActivity() {
    private var immersiveEnabled: Boolean = true
    private var gamesSignInClient: GamesSignInClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set a global uncaught exception handler early
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("GlobalCrash", "Uncaught exception in thread ${thread.name}", throwable)
                val prefs = getSharedPreferences("snake", MODE_PRIVATE)
                prefs.edit { putString(PrefKeys.LAST_CRASH, throwable::class.java.name + ": " + (throwable.message ?: "(no message)")) }
            } catch (_: Throwable) {}
            // Delegate to system default after logging
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(thread, throwable)
        }
        super.onCreate(savedInstanceState)
        // Initialize Mobile Ads SDK (safe to call multiple times; no-op after first)
        try {
            MobileAds.initialize(this) {}
        } catch (t: Throwable) { Log.w("MainActivity", "MobileAds init failed", t) }

        // Request user consent for ads where required (GDPR/EEA)
        try {
            requestConsent()
        } catch (t: Throwable) { Log.w("MainActivity", "UMP consent flow failed", t) }

        // Initialize Play Games sign-in client and try silent sign-in
        try {
            gamesSignInClient = PlayGames.getGamesSignInClient(this)
            gamesSignInClient?.isAuthenticated?.addOnCompleteListener { task ->
                val authenticated = task.isSuccessful && task.result.isAuthenticated
                Log.d("PlayGames", "Authenticated = $authenticated")
            }
        } catch (t: Throwable) {
            Log.w("MainActivity", "Play Games sign-in init failed", t)
        }
        // Enable edge-to-edge with transparent system bars using new SystemBarStyle API
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Set display cutout mode for edge-to-edge. Android 15 deprecates SHORT_EDGES; use ALWAYS for full-screen games.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                try {
                    // Use ALWAYS to avoid relying on the deprecated SHORT_EDGES parameter on newer Android versions.
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } catch (_: Throwable) {}
            }
        }
        applySystemBarAppearance()
        // Hide bars immediately for a seamless fullscreen launch
        if (immersiveEnabled) hideSystemBars() else showSystemBars()

        setContent { SnakeApp() }
    }

    private fun requestConsent() {
        // Configure consent params; by default not under age of consent.
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            // Optionally enable debug geography for testing; keep disabled for production
            //.setConsentDebugSettings(
            //    ConsentDebugSettings.Builder(this)
            //        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            //        .addTestDeviceHashedId("TEST-DEVICE-ID")
            //        .build()
            //)
            .build()
        val consentInformation = UserMessagingPlatform.getConsentInformation(this)
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                // Consent info updated. If a form is available, load and show it.
                loadAndShowConsentFormIfRequired()
            },
            { formError ->
                Log.w("MainActivity", "Consent request failed: ${formError.errorCode}:${formError.message}")
            }
        )
    }

    private fun loadAndShowConsentFormIfRequired() {
        val consentInformation = UserMessagingPlatform.getConsentInformation(this)
        UserMessagingPlatform.loadConsentForm(
            this,
            { consentForm ->
                if (consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                    try {
                        consentForm.show(this) { loadAndShowConsentFormIfRequired() }
                    } catch (t: Throwable) {
                        Log.w("MainActivity", "Consent form show failed", t)
                    }
                }
            },
            { formError -> Log.w("MainActivity", "Load consent form failed: ${formError.errorCode}:${formError.message}") }
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && immersiveEnabled) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        if (immersiveEnabled) hideSystemBars() else showSystemBars()
    }

    fun updateImmersiveMode(enabled: Boolean) {
        immersiveEnabled = enabled
        applySystemBarAppearance()
        if (enabled) hideSystemBars() else showSystemBars()
    }

    fun updateKeepScreenOn(enabled: Boolean) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun applySystemBarAppearance() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    fun getLastCrashMessage(): String? = try { getSharedPreferences("snake", MODE_PRIVATE).getString(PrefKeys.LAST_CRASH, null) } catch (_: Throwable){ null }
}
