@file:Suppress("TooManyFunctions", "LongMethod", "CyclomaticComplexMethod", "MagicNumber", "WildcardImport", "FunctionNaming", "LongParameterList", "ReturnCount", "ComplexCondition", "MaxLineLength")
package com.mmgb.snake

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.navigationBarsPadding
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import androidx.core.content.edit
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import com.google.android.gms.games.PlayGames

import android.os.Vibrator
import android.os.VibratorManager
import java.io.File
import java.io.FileInputStream
import kotlin.math.*

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.google.android.ump.UserMessagingPlatform
import com.android.billingclient.api.ProductDetails

// Inline constants (restored from modular attempt)
private const val POWERUP_SPAWN_CHANCE = 0.035f
private const val COMBO_TIME_WINDOW_MS = 3000
private const val POWERUP_DURATION_MS = 7000
private const val SCORE_COMBO_STEP = 0.2f
private const val MUSIC_INTERVAL_MS = 380
private const val MUSIC_MIN_TONE_DURATION_MS = 80
private const val BOOSTER_START_LENGTH_SEGMENTS = 3
private const val BOOSTER_EXTRA_TIME_SECONDS = 20
private const val INTERSTITIAL_WAIT_MS = 3500
private const val INTERSTITIAL_RETRY_WAIT_MS = 4000
private val BG = Color(0xFF0B1220)
private val GRID = Color(0xFFB3C5EF).copy(alpha = 0.25f)

private data class Particle(var x: Float, var y: Float, var dx: Float, var dy: Float, var life: Float, val color: Color)

// Booster system models are defined in BoosterModels.kt

private data class RunBoosters(
  val startLengthBonus: Int = 0,
  val scoreMultiplier: Float = 1f,
  val extraTimeSeconds: Int = 0,
  val shields: Int = 0
)

// Top-level Achievements data class (was previously nested)
@Serializable
private data class Achievements(
  val longest: Int = 3,
  val highestCombo: Int = 1,
  val games: Int = 0,
  val food: Int = 0,
  val scores: List<Int> = emptyList()
)

private val AchJson = Json { ignoreUnknownKeys = true }

@Composable
fun SnakeApp() {
  MaterialTheme(colorScheme = darkColorScheme()) {
    Surface(Modifier.fillMaxSize()) { GameScreen() }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameScreen() {
  val ctx = LocalContext.current
  val activity = (ctx as? MainActivity)
  val snackbarHostState = remember { SnackbarHostState() }

  // Currency + booster inventory state
  var coins by remember { mutableStateOf(loadCoins(ctx)) }
  var ownedBoosters by remember { mutableStateOf(loadBoosterInventory(ctx)) }
  var selectedBoosters by remember { mutableStateOf<Set<String>>(emptySet()) }
  var runBoosters by remember { mutableStateOf(RunBoosters()) }

  var showShop by remember { mutableStateOf(false) }

  // Persistent preference-backed states (initial defaults, then load)
  var running by remember { mutableStateOf<Boolean>(false) }
  var gameOver by remember { mutableStateOf<Boolean>(false) }
  var speedIdx by remember { mutableStateOf<Int>(1) }
  var mode by remember { mutableStateOf("classic") }
  var score by remember { mutableStateOf<Int>(0) }
  var best by remember { mutableStateOf<Int>(loadBest(ctx)) }
  var timeLeft by remember { mutableStateOf<Int>(60) }
  var musicVol by remember { mutableStateOf<Float>(0.04f) }
  var soundOn by remember { mutableStateOf<Boolean>(true) }
  var musicOn by remember { mutableStateOf<Boolean>(true) }
  var hapticsOn by remember { mutableStateOf<Boolean>(true) }
  var wrapWalls by remember { mutableStateOf<Boolean>(false) }
  var showDiag by remember { mutableStateOf<Boolean>(false) } // production default false; enable manually for diagnostics
  var showSettings by remember { mutableStateOf<Boolean>(false) }
  var keepScreenOn by remember { mutableStateOf<Boolean>(true) }
  // Debug build flag for gating diagnostics in production
  val isDebugBuild = (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
  // Remove Ads owned flag (persisted)
  var removeAds by remember { mutableStateOf<Boolean>(loadRemoveAds(ctx)) }
  // Booster credits state (was missing after edit)
  var boosterCredits by remember { mutableStateOf(loadBoosterCredits(ctx)) }
  // Billing product state for Remove Ads
  var removeAdsDetails by remember { mutableStateOf<ProductDetails?>(null) }
  var billingLoading by remember { mutableStateOf(false) }
  var billingError by remember { mutableStateOf<String?>(null) }

  // UMP consent state (moved up before ad helpers to avoid forward reference)
  var consentResolved by remember { mutableStateOf(false) }
  var consentStatusText by remember { mutableStateOf("pending") }
  LaunchedEffect(activity) {
    val act = activity ?: return@LaunchedEffect
    try {
      val consentInfo = UserMessagingPlatform.getConsentInformation(act)
      val params = com.google.android.ump.ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build()
      consentInfo.requestConsentInfoUpdate(act, params, {
        consentStatusText = consentInfo.consentStatus.toString()
        if (consentInfo.isConsentFormAvailable && consentInfo.consentStatus == com.google.android.ump.ConsentInformation.ConsentStatus.REQUIRED) {
          UserMessagingPlatform.loadAndShowConsentFormIfRequired(act) { err ->
            err?.let { Log.w("Consent", "Form error: ${it.errorCode} ${it.message}") }
            consentResolved = true
          }
        } else consentResolved = true
      }, { err ->
        Log.w("Consent", "Info update failed: ${err.errorCode} ${err.message}")
        consentResolved = true
      })
    } catch (t: Throwable) { Log.w("Consent", "Init exception", t); consentResolved = true }
  }
  if (showDiag && isDebugBuild) {
    Column(Modifier.padding(4.dp)) { Text("Consent=$consentResolved ($consentStatusText)", color = Color(0xFF64748B), fontSize = 10.sp) }
  }

  val interstitialState = remember { mutableStateOf<InterstitialAd?>(null) }
  // Track if current game's game-over ad was shown
  var adShownForGame by remember { mutableStateOf(false) }
  var adFailedToLoad by remember { mutableStateOf(false) }
  var gamesSinceLastAd by remember { mutableStateOf(0) }
  // Ad diagnostics
  var lastInterstitialErrorCode by remember { mutableStateOf<Int?>(null) }
  var lastInterstitialErrorMsg by remember { mutableStateOf<String?>(null) }
  var lastShowErrorCode by remember { mutableStateOf<Int?>(null) }
  var lastShowErrorMsg by remember { mutableStateOf<String?>(null) }

  // Sign-in state
  var signedIn by remember { mutableStateOf(false) }

  // Native ad state (for Settings modal)
  var nativeAd by remember { mutableStateOf<com.google.android.gms.ads.nativead.NativeAd?>(null) }
  // Helper to load a native ad lazily
  fun loadNativeAd() {
    if (removeAds) return
    val unitId = ctx.getString(R.string.admob_native_ad_unit_id)
    try {
      val loader = AdLoader.Builder(ctx, unitId)
        .forNativeAd { ad ->
          // Clean up previous ad
          nativeAd?.destroy()
          nativeAd = ad
        }
        .withAdListener(object : AdListener() {
          override fun onAdFailedToLoad(error: LoadAdError) {
            Log.w("SnakeApp", "Native ad failed: ${error.message}")
          }
        })
        .build()
      loader.loadAd(AdRequest.Builder().build())
    } catch (t: Throwable) { Log.w("SnakeApp", "Native ad load exception", t) }
  }
  LaunchedEffect(showSettings, removeAds) {
    if (showSettings && !removeAds && nativeAd == null) loadNativeAd()
  }
  DisposableEffect(Unit) { onDispose { nativeAd?.destroy() } }

  // Only show an interstitial every N games to limit frequency (production = every 2 games)
  val adFrequency = 2

  // Interstitial helpers (single definition)
  fun refreshInterstitial() {
    if (removeAds) {
      interstitialState.value = null; adFailedToLoad = false; Log.d("Ads", "Skip interstitial load: removeAds=true"); return
    }
    adFailedToLoad = false
    val consentStatus = try { UserMessagingPlatform.getConsentInformation(ctx).consentStatus } catch (_: Throwable) { null }
    if (!consentResolved && consentStatus == com.google.android.ump.ConsentInformation.ConsentStatus.REQUIRED) {
      Log.d("Ads", "Skip interstitial load: consent required & not resolved")
      return
    }
    val adUnit = ctx.getString(R.string.admob_interstitial_id) // production unit
    Log.d("Ads", "Loading interstitial: unit=$adUnit consent=$consentStatus gamesSinceLastAd=$gamesSinceLastAd adShownForGame=$adShownForGame")
    try {
      InterstitialAd.load(ctx, adUnit, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(ad: InterstitialAd) { interstitialState.value = ad; adFailedToLoad = false; Log.d("Ads", "Interstitial loaded") }
        override fun onAdFailedToLoad(error: LoadAdError) { Log.w("Ads", "Interstitial failed: code=${error.code} message=${error.message}"); interstitialState.value = null; adFailedToLoad = true; lastInterstitialErrorCode = error.code; lastInterstitialErrorMsg = error.message }
      })
    } catch (t: Throwable) { Log.w("Ads", "Interstitial load threw", t); interstitialState.value = null; adFailedToLoad = true }
  }
  fun showInterstitialIfReady(onDone: () -> Unit) {
    val ad = interstitialState.value
    if (ad != null && !removeAds) {
      Log.d("Ads", "Attempting to show interstitial. adAvailable=true")
      ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() { Log.d("Ads", "Interstitial shown") }
        override fun onAdImpression() { Log.d("Ads", "Interstitial impression recorded") }
        override fun onAdDismissedFullScreenContent() { Log.d("Ads", "Interstitial dismissed; refreshing"); refreshInterstitial(); onDone() }
        override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
          Log.w("Ads", "Failed to show interstitial: code=${adError.code} message=${adError.message}")
          adFailedToLoad = true
          lastShowErrorCode = adError.code
          lastShowErrorMsg = adError.message
          refreshInterstitial()
          onDone()
        }
      }
      activity?.let { ad.show(it) } ?: run {
        Log.w("Ads", "Activity null; cannot show interstitial")
        onDone()
      }
      interstitialState.value = null
    } else {
      Log.d("Ads", "Interstitial not ready (ad=${ad!=null} removeAds=$removeAds); proceeding without ad")
      onDone()
    }
  }
  // Cold-start prefetch only after consent resolution
  LaunchedEffect(consentResolved, removeAds) { if (consentResolved && !removeAds && interstitialState.value == null) refreshInterstitial() }
  // Remove previous unconditional Unit LaunchedEffect to avoid loading before consent

  // When gameOver flips to true, try to show an interstitial with a short wait window
  LaunchedEffect(gameOver, removeAds) {
    if (gameOver && !removeAds && !adShownForGame && gamesSinceLastAd >= adFrequency - 1) {
      var readyAd = interstitialState.value
      if (readyAd == null) {
        refreshInterstitial()
        // Wait asynchronously for ad load
        val start = System.currentTimeMillis()
        while (readyAd == null && System.currentTimeMillis() - start < INTERSTITIAL_WAIT_MS) {
          delay(150)
          readyAd = interstitialState.value
        }
      }
      if (readyAd != null) {
        showInterstitialIfReady {
          adShownForGame = true
          gamesSinceLastAd = 0
        }
      } else {
        adFailedToLoad = true
        adShownForGame = true
      }
    }
  }

  val billingManager = remember {
    runCatching { BillingManager(ctx.applicationContext) }
      .onFailure { Log.e("SnakeApp", "Billing manager init failed", it); appendCrash(ctx, "BillingInit", it) }
      .getOrNull()
  }
  val billingScope = rememberCoroutineScope()
  DisposableEffect(billingManager) {
    val job = billingScope.launch { billingManager?.ownedRemoveAds?.collectLatest { owned -> removeAds = owned; saveRemoveAds(ctx, owned) } }
    val job2 = billingScope.launch { billingManager?.boosterCredits?.collectLatest { credits -> boosterCredits = credits } }
    val job3 = billingScope.launch { billingManager?.coins?.collectLatest { latest -> coins = latest } }
    onDispose { job.cancel(); job2.cancel(); job3.cancel(); billingManager?.endConnection() }
  }

  // Observe Play Games authentication state once
  LaunchedEffect(activity) {
    try {
      activity?.let { act ->
        PlayGames.getGamesSignInClient(act).isAuthenticated.addOnCompleteListener { task ->
          signedIn = task.isSuccessful && task.result.isAuthenticated
          Log.d("PlayGames", "isAuthenticated = $signedIn")
        }
      }
    } catch (t: Throwable) {
      Log.w("SnakeApp", "Auth check failed", t)
      signedIn = false
    }
  }

  fun ensureSignedIn(onDone: ()->Unit = {}) {
    val act = activity ?: return onDone()
    try {
      PlayGames.getGamesSignInClient(act).isAuthenticated.addOnCompleteListener { task ->
        val ok = task.isSuccessful && task.result.isAuthenticated
        if (ok) { signedIn = true; onDone(); return@addOnCompleteListener }
        PlayGames.getGamesSignInClient(act).signIn().addOnCompleteListener { t2 ->
          signedIn = t2.isSuccessful && t2.result.isAuthenticated
          onDone()
        }
      }
    } catch (t: Throwable) { appendCrash(ctx, "EnsureSignIn", t); Log.w("SnakeApp", "ensureSignedIn failed", t); onDone() }
  }

  // Achievements / progression
  var ach by remember { mutableStateOf<Achievements>(loadAchievements(ctx)) }
  // Persisted real-time unlocked achievements set (immutable for state safety)
  var unlockedAchievements by remember { mutableStateOf(loadUnlockedAchievementSet(ctx)) }

  // Play Games clients (lazy)
  val leaderboardsClient = remember(activity) { activity?.let { PlayGames.getLeaderboardsClient(it) } }
  val achievementsClient = remember(activity) { activity?.let { PlayGames.getAchievementsClient(it) } }

  // Play Games IDs from Play Console (now loaded from resources for easier updates)
  val leaderboardId = ctx.getString(R.string.play_games_leaderboard_high_score_id)
  val achievementFirstGame = ctx.getString(R.string.achievement_first_game_id)
  val achievement100Score = ctx.getString(R.string.achievement_100_score_id)
  val achievementLongSnake = ctx.getString(R.string.achievement_long_snake_id)
  val achievement500Score = ctx.getString(R.string.achievement_500_score_id)
  val achievement50Food = ctx.getString(R.string.achievement_50_food_id)
  val achievement10Combo = ctx.getString(R.string.achievement_10_combo_id)

  fun tryUnlock(key: String, id: String, condition: Boolean) {
    if (!condition || unlockedAchievements.contains(key)) return
    val client = achievementsClient ?: return
    if (!id.startsWith("Cgk")) return
    runCatching { client.unlock(id) }.onFailure { Log.w("SnakeApp", "Unlock $key failed", it); appendCrash(ctx, "AchievementUnlock", it) }
    unlockedAchievements = unlockedAchievements + key
    saveUnlockedAchievementSet(ctx, unlockedAchievements)
    // Schedule a lightweight sync (e.g. reveal already unlocked) to ensure Play Games mirrors local state
    runCatching { client.reveal(id) }.onFailure { /* ignore; reveal may be redundant */ }
  }

  // Snake state
  val snake = remember { mutableStateListOf<Cell>(Cell(5,10), Cell(4,10), Cell(3,10)) }
  var dir by remember { mutableStateOf<Cell>(Cell(1,0)) }
  var nextDir by remember { mutableStateOf<Cell>(Cell(1,0)) }
  var food by remember { mutableStateOf<Cell>(randCell(exclude = snake.toSet())) }
  var powerUp by remember { mutableStateOf<Pair<Cell, PowerUp>?>(null) }
  val effectUntil = remember { mutableStateMapOf(
    PowerUp.GHOST to 0L, PowerUp.FAST to 0L, PowerUp.SLOW to 0L, PowerUp.DOUBLE to 0L, PowerUp.PHASE to 0L
  ) }
  var comboCount by remember { mutableStateOf(0) }
  var lastEat by remember { mutableStateOf(0L) }
  val particles = remember { mutableStateListOf<Particle>() }
  // Evaluate achievements centrally (after snake state so snake/combo accessible)
  fun evaluateUnlocks(trigger: String) {
    tryUnlock("first", achievementFirstGame, ach.games > 0 || (score > 0 && ach.games == 0))
    tryUnlock("century", achievement100Score, score >= 100 || ach.scores.any { it >= 100 })
    tryUnlock("python", achievementLongSnake, snake.size >= 20 || ach.longest >= 20 )
    tryUnlock("highRoller", achievement500Score, score >= 500 || ach.scores.any { it >= 500 })
    tryUnlock("hungry", achievement50Food, ach.food >= 50)
    tryUnlock("combo", achievement10Combo, comboCount >= 10 || ach.highestCombo >= 10)
    if (showDiag) Log.d("AchEval", "trigger=$trigger score=$score len=${snake.size} combo=$comboCount food=${ach.food}")
  }

  // FX Sound engine (SoundPool) (re-added)
  val soundEngine: SfxEngine? = remember {
    runCatching { SfxEngine(ctx) }
      .onFailure { Log.e("SnakeApp", "Sound engine init failed", it); appendCrash(ctx, "SfxInit", it) }
      .getOrNull()
  }
  DisposableEffect(soundEngine) { onDispose { soundEngine?.release() } }

  // AUDIO (ToneGenerator for music loop only)
  val tg: ToneGenerator? = remember {
    runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }
      .onFailure { Log.e("SnakeApp", "ToneGenerator init failed", it) }
      .getOrNull()
  }
  DisposableEffect(tg) { onDispose { try { tg?.release() } catch (_: Throwable) {} } }

  fun playEat() { if (soundOn) soundEngine?.play("eat") }
  fun playEat2() { if (soundOn) soundEngine?.play("eat2") }
  fun playPow() { if (soundOn) soundEngine?.play("pow") }
  fun playFail() { if (soundOn) soundEngine?.play("fail") }

  // HAPTICS
  val vibrator = remember {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
      } else {
        ctx.getSystemService(Vibrator::class.java)
      }
    } catch (_: Throwable) { null }
  }
  fun vibe(ms: Long, amp: Int = 80) {
    if (!hapticsOn || vibrator == null) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try { vibrator.vibrate(VibrationEffect.createOneShot(ms, amp)) } catch (_: Throwable) {}
    } else {
      @Suppress("DEPRECATION")
      try { vibrator.vibrate(ms) } catch (_: Throwable) {}
    }
  }
  fun hEat() = vibe(18, 120)
  fun hPow() = vibe(40, 160)
  fun hFail() = vibe(140, 200)

  // Music loop (simple arpeggio)
  LaunchedEffect(musicOn, musicVol, tg) {
    if (!musicOn || tg == null) return@LaunchedEffect
     val tones = listOf(
       ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_2, ToneGenerator.TONE_DTMF_3, ToneGenerator.TONE_DTMF_6, ToneGenerator.TONE_DTMF_9
     )
     var i=0
     while (isActive && musicOn) {
       val dur = (220 * musicVol.coerceIn(0f,0.2f) / 0.2f).toInt().coerceAtLeast(MUSIC_MIN_TONE_DURATION_MS)
       try { tg.startTone(tones[i % tones.size], dur) } catch (t: Throwable) { Log.w("SnakeApp", "ToneGenerator play failed", t); appendCrash(ctx, "TonePlay", t); break }
       i++; delay(MUSIC_INTERVAL_MS.toLong())
     }
   }

  // Diagnostics counters
  var fps by remember { mutableStateOf(0) }
  var tps by remember { mutableStateOf(0) }
  var frameCount by remember { mutableStateOf(0) }
  var tickCount by remember { mutableStateOf(0) }
  LaunchedEffect(showDiag) {
    var last = System.currentTimeMillis()
    while (isActive) {
      delay(16); frameCount++
      val now = System.currentTimeMillis()
      if (now - last >= 1000) {
        fps = frameCount; tps = tickCount; frameCount = 0; tickCount = 0; last = now
      }
    }
  }

  // GAME LOOP
  LaunchedEffect(running, gameOver, speedIdx, mode, wrapWalls) {
    var lastTick = 0L; var lastSec = System.currentTimeMillis()
    while (isActive) {
      delay(16); val now = System.currentTimeMillis(); if (lastTick==0L) lastTick = now
      if (mode=="time" && running && !gameOver && now - lastSec >= 1000) {
        lastSec = now; timeLeft = max(0, timeLeft - 1); if (timeLeft==0) gameOver = true
      }
      val interval = run {
        val base = currentInterval(speedIdx.coerceIn(0,4), effectUntil)
        // Dynamic difficulty: up to 30% faster as score rises (smoothly)
        val factor = (1f - (score / 800f)).coerceIn(0.7f, 1f)
        (base * factor).roundToLong().coerceAtLeast(40L)
      }
      if (running && !gameOver && now - lastTick >= interval) {
        tickCount++
        lastTick = now
        try {
          if (powerUp == null && kotlin.random.Random.nextFloat() < POWERUP_SPAWN_CHANCE) {
            val occSet = HashSet<Cell>(snake.size + 1); snake.forEach { occSet.add(it) }; occSet.add(food)
            val pos = randCell(occSet)
            powerUp = pos to PowerUp.entries.random()
          }
          dir = nextDir
          var head = Cell(snake.first().x + dir.x, snake.first().y + dir.y)
          val ghost = isEffectActive(effectUntil, PowerUp.GHOST)
          val phase = isEffectActive(effectUntil, PowerUp.PHASE)
          val wrapping = wrapWalls || ghost
          if (wrapping) {
            head = Cell((head.x + COLS) % COLS, (head.y + ROWS) % ROWS)
          } else if (head.x !in 0 until COLS || head.y !in 0 until ROWS) {
            // Wall collision: check shield
            if (runBoosters.shields > 0) {
              runBoosters = runBoosters.copy(shields = runBoosters.shields - 1)
              head = Cell(head.x.coerceIn(0, COLS-1), head.y.coerceIn(0, ROWS-1))
              playPow(); hPow()
            } else {
              gameOver = true; playFail(); hFail()
            }
          }
          if (!gameOver) {
              val hitSelf = snake.any { it.x==head.x && it.y==head.y }
              if (hitSelf && !phase) {
                // Self-collision: check shield
                if (runBoosters.shields > 0) {
                  runBoosters = runBoosters.copy(shields = runBoosters.shields - 1)
                  playPow(); hPow()
                } else {
                  gameOver = true; playFail(); hFail()
                }
              }
              if (!gameOver) {
                snake.add(0, head); var grew=false
                if (head.x==food.x && head.y==food.y) {
                  grew = true
                  val occ = snake.toSet(); food = randCell(occ)
                  val within = (now - lastEat) <= COMBO_TIME_WINDOW_MS; comboCount = if (within) comboCount + 1 else 1; lastEat = now
                  val comboMult = 1f + (comboCount - 1)*SCORE_COMBO_STEP; val base = if (isEffectActive(effectUntil, PowerUp.DOUBLE)) 2 else 1
                  val raw = base * comboMult * runBoosters.scoreMultiplier
                  val add = raw.roundToInt().coerceAtLeast(1); score += add; if (score>best) { best = score; saveBest(ctx,best) }
                  if (snake.size > ach.longest) ach = ach.copy(longest = snake.size)
                  if (comboCount > ach.highestCombo) ach = ach.copy(highestCombo = comboCount)
                  ach = ach.copy(food = ach.food + 1)
                  emitParticles(particles, head.x.toFloat(), head.y.toFloat(), Color(0xFFFFEDD5), 18)
                  playEat(); playEat2(); hEat(); if (mode=="time") timeLeft = min(180, timeLeft + 3)
                  evaluateUnlocks("eat")
                }
                // Power-up pickup
                powerUp?.let { (pos, typ) -> if (pos.x==head.x && pos.y==head.y) { powerUp=null; effectUntil[typ]=now+POWERUP_DURATION_MS; emitParticles(particles, head.x.toFloat(), head.y.toFloat(), colorFor(typ), 24); playPow(); hPow() } }
                if (!grew) snake.removeAt(snake.lastIndex)
                // Real-time achievements for snake length & combo after movement
                evaluateUnlocks("move")
              }
          }
          if (gameOver) {
            // Update achievements & high scores
            val newScores = (ach.scores + score).sortedDescending().take(10).toMutableList()
            ach = ach.copy(games = ach.games + 1, scores = newScores)
            saveAchievements(ctx, ach)
            evaluateUnlocks("gameOver")
            // Award coins based on score
            val earnedCoins = max(1, score / 10)
            coins += earnedCoins
            saveCoins(ctx, coins)
            // Submit score to leaderboard (if signed in and ID set)
            if (leaderboardsClient != null && leaderboardId.startsWith("Cgk")) {
              try { leaderboardsClient.submitScore(leaderboardId, score.toLong()) } catch (t: Throwable) { appendCrash(ctx, "SubmitScore", t); Log.w("SnakeApp", "Submit score failed", t) }
            }
          }
        } catch (t: Throwable) {
          // Prevent hard crash; log and pause game
          Log.e("GameLoop", "Tick failure", t)
          try {
            ctx.getSharedPreferences("snake", Context.MODE_PRIVATE).edit {
              putString(PrefKeys.LAST_CRASH, "GameLoop:" + t::class.java.name + ":" + (t.message ?: ""))
            }
          } catch (_: Throwable) {}
          gameOver = true
          running = false
        }
      }
    }
  }

  fun beginNewRun() {
    // Consume selected boosters into a runBoosters snapshot
    val catalog = boosterCatalog().associateBy { it.id }
    var startLenBonus = 0
    var scoreMult = 1f
    var extraTime = 0
    var shields = 0
    val newInventory = ownedBoosters.toMutableMap()

    selectedBoosters.forEach { id ->
      val def = catalog[id] ?: return@forEach
      val have = newInventory[id] ?: 0
      if (have <= 0) return@forEach
      newInventory[id] = have - 1
      when {
        def.kind == BoosterKind.START_LENGTH -> startLenBonus += BOOSTER_START_LENGTH_SEGMENTS
        def.kind == BoosterKind.SCORE_MULT -> scoreMult *= 1.25f
        def.kind == BoosterKind.EXTRA_TIME -> extraTime += BOOSTER_EXTRA_TIME_SECONDS
        def.superKind == SuperBoosterKind.SHIELD -> shields += 1
      }
    }
    ownedBoosters = newInventory.filterValues { it > 0 }
    saveBoosterInventory(ctx, ownedBoosters)
    selectedBoosters = emptySet()
    runBoosters = RunBoosters(startLenBonus, scoreMult, extraTime, shields)
  }

  fun restart() {
    beginNewRun()
    // Prefetch a fresh interstitial at the start of every run if none is ready
    if (!removeAds && interstitialState.value == null) refreshInterstitial()
    snake.clear(); snake.addAll(buildInitialSnake(runBoosters.startLengthBonus))
    dir = Cell(1,0); nextDir = Cell(1,0); food = randCell(exclude = snake.toSet()); powerUp = null
    PowerUp.entries.forEach { effectUntil[it] = 0L }; comboCount = 0; lastEat = 0L; particles.clear(); score = 0; gameOver = false; running = true
    adShownForGame = false
    adFailedToLoad = false
    gamesSinceLastAd++
    if (mode=="time") timeLeft = 60 + runBoosters.extraTimeSeconds
  }

  // Load preferences once
  LaunchedEffect(Unit) {
    loadPrefs(ctx)?.let { p ->
      mode = p.mode; speedIdx = p.speed.coerceIn(0,4); soundOn = p.sound; musicOn = p.music; hapticsOn = p.haptics; musicVol = p.musicVol; wrapWalls = p.wrap; showDiag = p.diag
      if (p is PrefsV2) keepScreenOn = p.keepScreenOn else keepScreenOn = true
      activity?.updateImmersiveMode(true)
      activity?.updateKeepScreenOn(keepScreenOn)
    }
  }
  // Save preferences when they change
  LaunchedEffect(mode, speedIdx, soundOn, musicOn, hapticsOn, musicVol, wrapWalls, showDiag, keepScreenOn) {
    // Ensure we never persist an out-of-range speed index
    speedIdx = speedIdx.coerceIn(0,4)
    savePrefs(ctx, PrefsV2(mode, speedIdx, soundOn, musicOn, hapticsOn, musicVol, wrapWalls, showDiag, keepScreenOn))
  }

  // Reusable board composable with overlay pause + diagnostics
  @Composable fun BoardWithOverlay(modifier: Modifier = Modifier, isFullscreen: Boolean = false) {
    @Suppress("UNUSED_VARIABLE", "UNUSED_CHANGED_VALUE")
    Box(
      modifier = modifier
        .then(if (!isFullscreen) Modifier.fillMaxWidth().aspectRatio(COLS / ROWS.toFloat()).clip(RoundedCornerShape(18.dp)) else Modifier.fillMaxSize())
        .background(Color(0xFF0A0F1A))
        .pointerInput(Unit) {
          var totalDx = 0f; var totalDy = 0f
          detectDragGestures(
            onDragStart = { totalDx = 0f; totalDy = 0f },
            onDragEnd = {
              val absX = abs(totalDx); val absY = abs(totalDy)
              if (absX < 30f && absY < 30f) return@detectDragGestures
              val newDir = if (absX > absY) { if (totalDx>0) Cell(1,0) else Cell(-1,0) } else { if (totalDy>0) Cell(0,1) else Cell(0,-1) }
              nextDir = nudge(nextDir, newDir, dir)
            }
          ) { change, dragAmount -> change.consume(); totalDx += dragAmount.x; totalDy += dragAmount.y }
        }
    ) {
      BoardCanvas(
        snake = snake, food = food, powerUp = powerUp, particles = particles,
        effectUntil = effectUntil, mode = mode, timeLeft = timeLeft, comboCount = comboCount,
        gameOver = gameOver, paused = !running && !gameOver
      )
      Box(Modifier.fillMaxSize().systemBarsPadding()) {
        if (isFullscreen) {
          Column(
            Modifier.align(Alignment.TopStart)
              .padding(8.dp)
              .background(Color(0x661E293B), RoundedCornerShape(12.dp))
              .padding(horizontal = 10.dp, vertical = 8.dp)
          ) {
            Text("Score: $score", color = Color(0xFFE2E8F0), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Beste: $best", color = Color(0xFFCBD5E1), fontSize = 12.sp)
            if (mode == "time") Text("Tijd: ${timeLeft}s", color = Color(0xFF93C5FD), fontSize = 12.sp)
            // Show active boosters
            if (runBoosters.shields > 0) {
              Text("🛡️ Schild: ${runBoosters.shields}", color = Color(0xFFFDE68A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            if (runBoosters.scoreMultiplier > 1f) {
              Text("⚡ Score: ${(runBoosters.scoreMultiplier * 100).toInt()}%", color = Color(0xFF34D399), fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Button(onClick = { if (!gameOver) running = !running else restart() }, colors = ButtonDefaults.buttonColors(containerColor = if (gameOver) Color(0xFFEF4444) else Color(0xFF10B981), contentColor = Color.Black), shape = RoundedCornerShape(50)) {
                Text( when { gameOver -> "Opnieuw"; running -> "Pauze"; else -> "Verder" }, fontSize = 12.sp)
              }
              Spacer(Modifier.width(8.dp))
              Button(onClick = { showSettings = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)), shape = RoundedCornerShape(50)) {
                Text("⚙", fontSize = 14.sp)
              }
            }
            // Show retry ad button if game over, ads not removed, and ad failed to load
            if (gameOver && !removeAds && adFailedToLoad && adShownForGame) {
              Spacer(Modifier.height(8.dp))
              val scope = rememberCoroutineScope()
              Button(
                onClick = {
                  adShownForGame = false
                  adFailedToLoad = false
                  refreshInterstitial()
                  // Use existing scope instead of calling composable inside onClick
                  scope.launch {
                    var loadedAd = interstitialState.value
                    val start = System.currentTimeMillis()
                    while (loadedAd == null && System.currentTimeMillis() - start < INTERSTITIAL_RETRY_WAIT_MS) {
                      delay(150)
                      loadedAd = interstitialState.value
                    }
                    if (loadedAd != null) {
                      Log.d("Ads", "Retry button: interstitial loaded, showing now")
                      showInterstitialIfReady { adShownForGame = true }
                    } else {
                      Log.w("Ads", "Retry button: interstitial still not loaded after timeout")
                      adFailedToLoad = true
                      adShownForGame = true
                    }
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24), contentColor = Color.Black),
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("\ud83d\udd04 Probeer advertentie opnieuw", fontSize = 12.sp)
              }
            }
            if (showDiag) {
              Spacer(Modifier.height(6.dp))
              Text("FPS $fps / TPS $tps", color = Color(0xFF94A3B8), fontSize = 11.sp)
              Text("Len ${snake.size} Combo $comboCount", color = Color(0xFF94A3B8), fontSize = 11.sp)
              // Ad diagnostics panel
              val consentStatus = try { UserMessagingPlatform.getConsentInformation(ctx).consentStatus } catch (_: Throwable) { null }
              Spacer(Modifier.height(4.dp))
              Column(Modifier.background(Color(0x330F172A), RoundedCornerShape(8.dp)).padding(6.dp)) {
                Text("AdDiag", color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("loaded=${interstitialState.value!=null} failed=$adFailedToLoad freq=$adFrequency gamesSince=$gamesSinceLastAd", color = Color(0xFF94A3B8), fontSize = 10.sp)
                Text("consent=$consentStatus removeAds=$removeAds", color = Color(0xFF94A3B8), fontSize = 10.sp)
                lastInterstitialErrorCode?.let { Text("loadErr=$it ${lastInterstitialErrorMsg}", color = Color(0xFFF87171), fontSize = 10.sp) }
                lastShowErrorCode?.let { Text("showErr=$it ${lastShowErrorMsg}", color = Color(0xFFF87171), fontSize = 10.sp) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Button(onClick = { refreshInterstitial() }, enabled = !removeAds, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6), contentColor = Color.White), modifier = Modifier.weight(1f), shape = RoundedCornerShape(50)) { Text("Load", fontSize = 10.sp) }
                  Button(onClick = { showInterstitialIfReady { adShownForGame = true } }, enabled = interstitialState.value!=null && !removeAds, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black), modifier = Modifier.weight(1f), shape = RoundedCornerShape(50)) { Text("Show", fontSize = 10.sp) }
                }
              }
            }
          }
        } else {
          Column(
            Modifier.align(Alignment.Center)
              .background(Color(0xCC0F172A), RoundedCornerShape(18.dp))
              .padding(16.dp)
              .widthIn(max = 400.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Text(
              if (gameOver) "Game over" else "Klaar om te spelen?",
              color = Color(0xFFF8FAFC),
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              if (gameOver) "Tik op Opnieuw of Start" else "Tik op ▶ of de knop hieronder",
              color = Color(0xFFCBD5F5),
              fontSize = 14.sp,
              modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )

            // Show booster selection when not running (before start or after game over)
            if (!running && ownedBoosters.isNotEmpty()) {
              Spacer(Modifier.height(8.dp))
              Text("Kies boosters voor deze run:", color = Color(0xFFE2E8F0), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
              Spacer(Modifier.height(8.dp))
              Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val catalogRef = BOOSTER_CATALOG
                boosterCatalog().forEach { def ->
                  val owned = ownedBoosters[def.id] ?: 0
                  if (owned > 0) {
                    val selected = def.id in selectedBoosters
                    val canSelect = canSelectBooster(selectedBoosters, def, catalogRef) || selected
                    val enabledState = if (selected) true else canSelect
                    Button(
                      onClick = {
                        if (selected) {
                          selectedBoosters = selectedBoosters - def.id
                        } else if (canSelect) {
                          selectedBoosters = selectedBoosters + def.id
                        }
                      },
                      enabled = enabledState,
                      colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) Color(0xFF22C55E) else Color(0xFF1F2937),
                        contentColor = if (selected) Color.Black else Color(0xFFE5E7EB),
                        disabledContainerColor = Color(0xFF374151),
                        disabledContentColor = Color(0xFF6B7280)
                      ),
                      shape = RoundedCornerShape(50),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(def.title, fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("(x$owned)", fontSize = 11.sp, color = if (selected) Color.Black.copy(alpha = 0.7f) else Color(0xFF9CA3AF))
                        if (def.isSuper) {
                          Spacer(Modifier.width(6.dp))
                          Text("⭐", fontSize = 10.sp)
                        }
                        if (selected) {
                          Spacer(Modifier.weight(1f))
                          Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                      }
                    }
                  }
                }
              }
              Spacer(Modifier.height(8.dp))
            }

            Button(
              onClick = { if (gameOver) restart() else running = true },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316), contentColor = Color.Black),
              shape = RoundedCornerShape(50)
            ) {
              Text(if (gameOver) "Opnieuw" else "Start", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
          }
        }
        Button(
          onClick = { if (!gameOver) running = !running else restart() },
          modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
          shape = RoundedCornerShape(50),
          colors = ButtonDefaults.buttonColors(containerColor = if (gameOver) Color(0xFFEF4444) else Color(0xFF1E293B))
        ) { Text( when { gameOver -> "↺"; running -> "⏸"; else -> "▶" }) }
      }
    }
  }

  // Panels (portrait vs landscape) inside Scaffold for snackbar hosting
  Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
     Box(Modifier.fillMaxSize().padding(padding)) {
      BoardWithOverlay(isFullscreen = true)
      // Simple booster / coin button overlay in the corner
      Box(Modifier.fillMaxSize()) {
        Row(Modifier.align(Alignment.TopStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
          Surface(shape = RoundedCornerShape(50), color = Color(0xFF111827)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
              Text("💰 $coins", color = Color(0xFFFDE68A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              Spacer(Modifier.width(8.dp))
              // Show booster credits if any (legacy metric) -- removed from UI for production clarity
              // if (boosterCredits > 0) {
              //   Text("\u2728 $boosterCredits", color = Color(0xFF9AE6B4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              //   Spacer(Modifier.width(8.dp))
              // }
              Button(onClick = { showShop = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E), contentColor = Color.Black), shape = RoundedCornerShape(50)) {
                Text("Shop", fontSize = 12.sp)
              }
              Spacer(Modifier.width(8.dp))
              Button(onClick = { showSettings = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = Color(0xFFE2E8F0)), shape = RoundedCornerShape(50)) {
                Text("⚙", fontSize = 12.sp)
              }
            }
          }
        }
        // Add Privacy button in the top-right corner
        Row(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
          Button(onClick = {
            try { ctx.startActivity(Intent(ctx, PrivacyPolicyActivity::class.java)) } catch (_: Throwable) {}
          }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) { Text("Privacy") }
        }
        // Banner Ad at bottom (hidden if Remove Ads is owned)
        if (!removeAds) {
          Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
            AndroidView(
              factory = { context ->
                AdView(context).apply {
                  setAdSize(adaptiveAdSize(context))
                  adUnitId = context.getString(R.string.admob_banner_ad_unit_id)
                  loadAd(AdRequest.Builder().build())
                }
              }
            )
          }
        }
      }
      if (showShop) {
        BoosterShopModal(
          coins = coins,
          ownedBoosters = ownedBoosters,
          onClose = { showShop = false },
          onPurchase = { id ->
            val def = boosterCatalog().find { it.id == id } ?: return@BoosterShopModal
            if (coins < def.price) {
              // Show insufficient coins snackbar using coroutine scope (not a composable context)
              billingScope.launch { snackbarHostState.showSnackbar("Niet genoeg munten voor ${def.title}") }
            } else {
              coins -= def.price
              val inv = ownedBoosters.toMutableMap(); inv[id] = (inv[id] ?: 0) + 1
              ownedBoosters = inv; saveCoins(ctx, coins); saveBoosterInventory(ctx, ownedBoosters)
              // Show success snackbar via coroutine
              billingScope.launch { snackbarHostState.showSnackbar("Gekocht: ${def.title}") }
            }
          },
          billingManager = billingManager,
          activity = activity
        )
      }
      // Settings modal with dynamic achievements
      if (showSettings) {
        SettingsModal(
          onClose = { showSettings = false },
          signedIn = signedIn,
          onSignIn = { ensureSignedIn() },
          ach = ach,
          bestScore = best,
          mode = mode,
          wrapWalls = wrapWalls,
          onToggleWrap = { wrapWalls = !wrapWalls },
          soundOn = soundOn,
          onToggleSound = { soundOn = !soundOn },
          musicOn = musicOn,
          onToggleMusic = { musicOn = !musicOn },
          musicVol = musicVol,
          onMusicVol = { musicVol = it.coerceIn(0f,0.2f) },
          hapticsOn = hapticsOn,
          onToggleHaptics = { hapticsOn = !hapticsOn },
          showDiag = showDiag,
          onToggleDiag = { showDiag = !showDiag },
          keepScreenOn = keepScreenOn,
          onToggleKeepOn = { keepScreenOn = !keepScreenOn; activity?.updateKeepScreenOn(keepScreenOn) },
          nativeAd = nativeAd,
          removeAds = removeAds,
          removeAdsDetails = removeAdsDetails,
          billingLoading = billingLoading,
          billingError = billingError,
          onPurchaseRemoveAds = {
            val act = activity
            val details = removeAdsDetails
            if (act != null && details != null && billingManager != null) {
              billingManager.launchPurchaseFlow(act, details)
            }
          },
          onReloadRemoveAds = {
            if (billingManager != null && !removeAds) {
              billingLoading = true; billingError = null; removeAdsDetails = null
              billingManager.queryProductDetails(listOf(BillingManager.PRODUCT_REMOVE_ADS)) { d ->
                billingLoading = false; removeAdsDetails = d.firstOrNull(); if (removeAdsDetails == null) billingError = "Not found" }
            }
          },
          isDebugBuild = isDebugBuild
        )
      }
    }
  }

  // Persist booster credits when they change to clear unused warning on save function
  LaunchedEffect(boosterCredits) { saveBoosterCredits(ctx, boosterCredits) }

  // remove later duplicate interstitial helpers
  // fun showInterstitialIfReady ... (moved above)
  // fun refreshInterstitial ... (moved above)
  // LaunchedEffect(removeAds) { refreshInterstitial() }
}

// Preference data holder
private open class Prefs(
  val mode: String,
  val speed: Int,
  val sound: Boolean,
  val music: Boolean,
  val haptics: Boolean,
  val musicVol: Float,
  val wrap: Boolean,
  val diag: Boolean
)
private class PrefsV2(
  mode: String,
  speed: Int,
  sound: Boolean,
  music: Boolean,
  haptics: Boolean,
  musicVol: Float,
  wrap: Boolean,
  diag: Boolean,
  val keepScreenOn: Boolean
): Prefs(mode, speed, sound, music, haptics, musicVol, wrap, diag)

private fun loadPrefs(ctx: Context): Prefs? = try {
  val p = ctx.getSharedPreferences("snake", Context.MODE_PRIVATE)
  val hasV2 = p.contains("pref_immersive") || p.contains("pref_keepOn")
  if (hasV2) PrefsV2(
    mode = p.getString(PrefKeys.MODE, "classic") ?: "classic",
    speed = p.getInt(PrefKeys.SPEED, 1),
    sound = p.getBoolean(PrefKeys.SOUND, true),
    music = p.getBoolean(PrefKeys.MUSIC, true),
    haptics = p.getBoolean(PrefKeys.HAPTICS, true),
    musicVol = p.getFloat(PrefKeys.MUSIC_VOL, 0.04f),
    wrap = p.getBoolean(PrefKeys.WRAP, false),
    diag = p.getBoolean(PrefKeys.DIAG, false),
    keepScreenOn = p.getBoolean(PrefKeys.KEEP_ON, true)
   ) else Prefs(
    mode = p.getString(PrefKeys.MODE, "classic") ?: "classic",
    speed = p.getInt(PrefKeys.SPEED, 1),
    sound = p.getBoolean(PrefKeys.SOUND, true),
    music = p.getBoolean(PrefKeys.MUSIC, true),
    haptics = p.getBoolean(PrefKeys.HAPTICS, true),
    musicVol = p.getFloat(PrefKeys.MUSIC_VOL, 0.04f),
    wrap = p.getBoolean(PrefKeys.WRAP, false),
    diag = p.getBoolean(PrefKeys.DIAG, false)
   )
} catch (_: Throwable) { null }

private fun savePrefs(ctx: Context, pr: Prefs) {
  val p = ctx.getSharedPreferences("snake", Context.MODE_PRIVATE)
  p.edit {
    putString(PrefKeys.MODE, pr.mode)
    putInt(PrefKeys.SPEED, pr.speed)
    putBoolean(PrefKeys.SOUND, pr.sound)
    putBoolean(PrefKeys.MUSIC, pr.music)
    putBoolean(PrefKeys.HAPTICS, pr.haptics)
    putFloat(PrefKeys.MUSIC_VOL, pr.musicVol)
    putBoolean(PrefKeys.WRAP, pr.wrap)
    putBoolean(PrefKeys.DIAG, pr.diag)
     if (pr is PrefsV2) putBoolean(PrefKeys.KEEP_ON, pr.keepScreenOn)
   }
 }

private fun loadAchievements(ctx: Context): Achievements {
  val p = ctx.getSharedPreferences("snake", Context.MODE_PRIVATE)
  val raw = p.getString(PrefKeys.ACHIEVEMENTS, null) ?: return Achievements()
  return try {
    AchJson.decodeFromString<Achievements>(raw)
  } catch (_: Throwable) {
    // Fallback legacy regex parsing for backward compatibility
    try {
      fun find(key:String, def:String="0"):String { val r = Regex("\"$key\":(\".*?\"|[0-9]+)").find(raw)?:return def; return r.groupValues[1].trim('"') }
      val longest = find("longest").toInt()
      val highest = find("highestCombo").toInt()
      val games = find("games").toInt()
      val food = find("food").toInt()
      val scoresStr = find("scores","\"").split(',').filter { it.isNotBlank() && it.all { c-> c.isDigit() } }.mapNotNull { it.toIntOrNull() }
      Achievements(longest, highest, games, food, scoresStr)
    } catch (_: Throwable) { Achievements() }
  }
}
private fun saveAchievements(ctx: Context, a: Achievements) {
  val json = Json.encodeToString(Achievements.serializer(), a)
  ctx.getSharedPreferences("snake", Context.MODE_PRIVATE).edit { putString(PrefKeys.ACHIEVEMENTS, json) }
}

// Sound FX engine (synthetic tones via generated WAVs loaded into SoundPool)
private class SfxEngine(ctx: Context) {
  // minSdk >= 26 — use modern builder directly
  private val pool: SoundPool = SoundPool.Builder().setMaxStreams(6).build()
  private val map = mutableMapOf<String, Int>()
  init {
    try {
      map["eat"] = loadTone(ctx, "se_eat", 880, 90)
      map["eat2"] = loadTone(ctx, "se_eat2", 1320, 70)
      map["pow"] = loadTone(ctx, "se_pow", 620, 140)
      map["fail"] = loadTone(ctx, "se_fail", 220, 300)
    } catch (_: Throwable) {}
  }
  private fun loadTone(ctx: Context, name:String, freq:Int, ms:Int): Int {
    val f = File(ctx.filesDir, "$name.wav")
    if (!f.exists()) writeWav(f, freq, ms)
    FileInputStream(f).use { fis ->
      return pool.load(fis.fd, 0L, f.length(), 1)
    }
  }
  fun play(key:String){ map[key]?.let { pool.play(it,1f,1f,1,0,1f) } }
  fun release(){ try { pool.release() } catch (_: Throwable) {} }
  private fun writeWav(file: File, freq:Int, ms:Int) {
    val sampleRate = 8000
    val samples = (sampleRate * ms / 1000.0).toInt()
    val pcm = ByteArray(samples*2)
    for(i in 0 until samples){ val v = (sin(2*Math.PI*i*freq/sampleRate)*Short.MAX_VALUE*0.4).toInt(); pcm[2*i]=(v and 0xFF).toByte(); pcm[2*i+1]=((v shr 8) and 0xFF).toByte() }
    val header = ByteArray(44)
    fun putLE(off:Int, v:Int){ header[off]=(v and 0xFF).toByte(); header[off+1]=((v shr 8) and 0xFF).toByte(); header[off+2]=((v shr 16) and 0xFF).toByte(); header[off+3]=((v shr 24) and 0xFF).toByte() }
    header[0]='R'.code.toByte(); header[1]='I'.code.toByte(); header[2]='F'.code.toByte(); header[3]='F'.code.toByte()
    putLE(4, 36+pcm.size); header[8]='W'.code.toByte(); header[9]='A'.code.toByte(); header[10]='V'.code.toByte(); header[11]='E'.code.toByte()
    header[12]='f'.code.toByte(); header[13]='m'.code.toByte(); header[14]='t'.code.toByte(); header[15]=' '.code.toByte(); putLE(16,16); header[20]=1; header[21]=0; header[22]=1; header[23]=0
    putLE(24, sampleRate); putLE(28, sampleRate*2); header[32]=2; header[33]=0; header[34]=16; header[35]=0
    header[36]='d'.code.toByte(); header[37]='a'.code.toByte(); header[38]='t'.code.toByte(); header[39]='a'.code.toByte(); putLE(40, pcm.size)
    file.outputStream().use { it.write(header); it.write(pcm) }
  }
}

// ---- Helpers ----
private fun colorFor(p: PowerUp) = when(p){
  PowerUp.GHOST -> Color(0xFF60A5FA)
  PowerUp.FAST -> Color(0xFFFBBF24)
  PowerUp.SLOW -> Color(0xFFA78BFA)
  PowerUp.DOUBLE -> Color(0xFFF472B6)
  PowerUp.PHASE -> Color(0xFF34D399)
}
private fun emitParticles(list: MutableList<Particle>, x: Float, y: Float, color: Color, n: Int) {
  val rnd = kotlin.random.Random
  repeat(n) {
    val ang = rnd.nextDouble() * Math.PI * 2
    val spd = 0.5 + rnd.nextDouble() * 1.5
    list.add(Particle(x, y, cos(ang).toFloat()*spd.toFloat(), sin(ang).toFloat()*spd.toFloat(), 1f, color))
  }
}

private fun buildInitialSnake(extraSegments: Int): List<Cell> {
  val base = mutableListOf(Cell(5,10), Cell(4,10), Cell(3,10))
  var last = base.last()
  repeat(extraSegments.coerceAtLeast(0)) {
    last = Cell(last.x - 1, last.y)
    base.add(last)
  }
  return base
}

private fun loadBest(ctx: Context) = ctx.getSharedPreferences("snake", Context.MODE_PRIVATE).getInt(PrefKeys.BEST, 0)
private fun saveBest(ctx: Context, v: Int) { ctx.getSharedPreferences("snake", Context.MODE_PRIVATE).edit { putInt(PrefKeys.BEST, v) } }
private fun saveRemoveAds(ctx: Context, v: Boolean) { ctx.getSharedPreferences("snake", Context.MODE_PRIVATE).edit { putBoolean(PrefKeys.REMOVE_ADS, v) } }
private fun loadRemoveAds(ctx: Context): Boolean = ctx.getSharedPreferences("snake", Context.MODE_PRIVATE).getBoolean(PrefKeys.REMOVE_ADS, false)
private fun saveBoosterCredits(ctx: Context, v: Int) { ctx.getSharedPreferences("snake", Context.MODE_PRIVATE).edit { putInt(PrefKeys.BOOSTER_CREDITS, v) } }
private fun loadBoosterCredits(ctx: Context): Int = ctx.getSharedPreferences("snake", Context.MODE_PRIVATE).getInt(PrefKeys.BOOSTER_CREDITS, 0)
private fun loadCoins(ctx: Context): Int = ctx.getSharedPreferences("snake", Context.MODE_PRIVATE).getInt(PrefKeys.COINS, 0)
private fun saveCoins(ctx: Context, coins: Int) { ctx.getSharedPreferences("snake", Context.MODE_PRIVATE).edit { putInt(PrefKeys.COINS, coins) } }
private fun loadBoosterInventory(ctx: Context): Map<String, Int> {
  val p = ctx.getSharedPreferences("snake", Context.MODE_PRIVATE)
  val raw = p.getString(PrefKeys.BOOSTERS, null) ?: return emptyMap()
  return try {
    raw.split(';').mapNotNull {
      if (!it.contains('=')) return@mapNotNull null
      val parts = it.split('=')
      val id = parts.getOrNull(0)?.trim().orEmpty()
      val count = parts.getOrNull(1)?.toIntOrNull() ?: 0
      if (id.isNotBlank() && count > 0) id to count else null
    }.toMap()
  } catch (_: Throwable) { emptyMap() }
}
private fun saveBoosterInventory(ctx: Context, inv: Map<String, Int>) {
  val p = ctx.getSharedPreferences("snake", Context.MODE_PRIVATE)
  val raw = inv.entries.joinToString(";") { "${it.key}=${it.value}" }
  p.edit { putString(PrefKeys.BOOSTERS, raw) }
}

private fun saveUnlockedAchievementSet(ctx: Context, set: Set<String>) {
  ctx.getSharedPreferences("snake", Context.MODE_PRIVATE).edit { putString(PrefKeys.UNLOCKED_ACH, set.joinToString(",")) }
}
private fun loadUnlockedAchievementSet(ctx: Context): Set<String> {
  val raw = ctx.getSharedPreferences("snake", Context.MODE_PRIVATE).getString(PrefKeys.UNLOCKED_ACH, "") ?: return emptySet()
  return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

// Crash ring buffer helper (store last 5 crashes)
private fun appendCrash(ctx: Context, tag: String, t: Throwable) {
  val p = ctx.getSharedPreferences("snake", Context.MODE_PRIVATE)
  val prev = p.getString(PrefKeys.CRASH_LOG, "") ?: ""
  val line = "${System.currentTimeMillis()}:$tag:${t::class.java.simpleName}:${t.message ?: ""}".replace('\n',' ')
  val parts = (prev.split('\n') + line).filter { it.isNotBlank() }.takeLast(5)
  p.edit { putString(PrefKeys.CRASH_LOG, parts.joinToString("\n")) }
}

private fun adaptiveAdSize(context: Context): AdSize {
  val dm = context.resources.displayMetrics
  val adWidth = (dm.widthPixels / dm.density).toInt()
  return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
}

@Composable
private fun BoardCanvas(
  snake: List<Cell>, food: Cell, powerUp: Pair<Cell, PowerUp>?, particles: List<Particle>,
  effectUntil: Map<PowerUp, Long>, mode: String, timeLeft: Int, comboCount: Int, gameOver: Boolean, paused: Boolean
) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val cellSize = min(size.width / COLS, size.height / ROWS)
    val boardWidth = cellSize * COLS
    val boardHeight = cellSize * ROWS
    val originX = (size.width - boardWidth) / 2f
    val originY = (size.height - boardHeight) / 2f
    drawRect(BG)
    drawRoundRect(
      color = Color(0xFF050B16),
      topLeft = Offset(originX, originY),
      size = androidx.compose.ui.geometry.Size(boardWidth, boardHeight),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
    )
    for (x in 0..COLS) {
      val xPos = originX + x * cellSize
      drawLine(GRID, Offset(xPos, originY), Offset(xPos, originY + boardHeight), 1f)
    }
    for (y in 0..ROWS) {
      val yPos = originY + y * cellSize
      drawLine(GRID, Offset(originX, yPos), Offset(originX + boardWidth, yPos), 1f)
    }
    val foodTopLeft = Offset(originX + food.x * cellSize + 2f, originY + food.y * cellSize + 2f)
    drawRoundRect(
      Color(0xFFFF6B6B),
      topLeft = foodTopLeft,
      size = androidx.compose.ui.geometry.Size(cellSize - 4f, cellSize - 4f),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
    )
    powerUp?.let { (pos, typ) ->
      val c = colorFor(typ)
      val cx = originX + pos.x * cellSize + cellSize / 2f
      val cy = originY + pos.y * cellSize + cellSize / 2f
      val r = (cellSize - 6f) / 2f
      val p = Path().apply {
        moveTo(cx, cy - r)
        lineTo(cx + r, cy)
        lineTo(cx, cy + r)
        lineTo(cx - r, cy)
        close()
      }
      drawPath(p, color = c, style = Fill)
    }
    snake.forEachIndexed { i, seg ->
      val glow = (isEffectActive(effectUntil, PowerUp.GHOST) || isEffectActive(effectUntil, PowerUp.PHASE)) && i == 0
      val color = if (i == 0) (if (glow) Color(0xFFA7F3D0) else Color(0xFF7CFC98)) else Color(0xFF50FA7B)
      val topLeft = Offset(originX + seg.x * cellSize + 2f, originY + seg.y * cellSize + 2f)
      drawRoundRect(
        color,
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(cellSize - 4f, cellSize - 4f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(if (i == 0) 8f else 6f, if (i == 0) 8f else 6f)
      )
    }
    particles.forEach { p ->
      val px = originX + p.x * cellSize + cellSize / 2f
      val py = originY + p.y * cellSize + cellSize / 2f
      drawRect(
        p.color.copy(alpha = p.life),
        topLeft = Offset(px, py),
        size = androidx.compose.ui.geometry.Size(2f, 2f)
      )
    }
    val now = System.currentTimeMillis()
    val bars = listOf(PowerUp.GHOST, PowerUp.FAST, PowerUp.SLOW, PowerUp.DOUBLE, PowerUp.PHASE)
    val barWidth = boardWidth - 12f
    var barY = originY + 6f
    bars.forEach { k ->
      val until = effectUntil[k] ?: 0L
      if (until > now) {
        val t = ((until - now) / POWERUP_DURATION_MS.toFloat()).coerceIn(0f, 1f)
        drawRect(colorFor(k), topLeft = Offset(originX + 6f, barY), size = androidx.compose.ui.geometry.Size(barWidth * t, 4f))
      }
      barY += 8f
    }
    if (comboCount > 1) {
      drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
          color = Color(0xFFFDE68A).toArgb()
          textSize = cellSize * 1.1f
          isFakeBoldText = true
          isAntiAlias = true
        }
        canvas.nativeCanvas.drawText(
          "COMBO x$comboCount",
          originX + 8f,
          originY + boardHeight - 10f,
          paint
        )
      }
    }
    if (mode == "time") {
      drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
          color = Color(0xFF93C5FD).toArgb()
          textSize = cellSize
          isFakeBoldText = true
          isAntiAlias = true
        }
        canvas.nativeCanvas.drawText(
          "${timeLeft}s",
          originX + boardWidth - 56f,
          originY + 18f,
          paint
        )
      }
    }
    if (gameOver || paused) {
      drawRect(Color(0xFF020617).copy(alpha = 0.65f), topLeft = Offset(originX, originY), size = androidx.compose.ui.geometry.Size(boardWidth, boardHeight))
      val title = if (gameOver) "GAME OVER" else "PAUZE"
      val subtitle = if (gameOver) "Gebruik Opnieuw" else "Druk Pauze/Verder"
      drawIntoCanvas { canvas ->
        val titlePaint = android.graphics.Paint().apply {
          color = Color(0xFFE2E8F0).toArgb()
          textSize = boardHeight * 0.12f
          isFakeBoldText = true
          textAlign = android.graphics.Paint.Align.CENTER
          isAntiAlias = true
        }
        val subPaint = android.graphics.Paint().apply {
          color = Color(0xFF94A3B8).toArgb()
          textSize = boardHeight * 0.045f
          textAlign = android.graphics.Paint.Align.CENTER
          isAntiAlias = true
        }
        val cx = originX + boardWidth / 2f
        val cy = originY + boardHeight / 2f
        canvas.nativeCanvas.drawText(title, cx, cy - 20f, titlePaint)
        canvas.nativeCanvas.drawText(subtitle, cx, cy + 30f, subPaint)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoosterShopModal(
  coins: Int,
  ownedBoosters: Map<String, Int>,
  onClose: () -> Unit,
  onPurchase: (String) -> Unit,
  billingManager: BillingManager?,
  activity: MainActivity?
) {
  var products by remember { mutableStateOf<List<ProductDetails>>(emptyList()) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(billingManager) {
    if (billingManager != null && billingManager.isReady()) {
      loading = true; error = null
      billingManager.queryProductDetails(listOf(
        BillingManager.PRODUCT_BOOSTER_PACK,
        BillingManager.PRODUCT_SUPER_BOOST
      )) { list ->
        products = list; loading = false; if (list.isEmpty()) error = "Geen producten" }
    }
  }
  val packDetails = products.firstOrNull { it.productId == BillingManager.PRODUCT_BOOSTER_PACK }
  val superDetails = products.firstOrNull { it.productId == BillingManager.PRODUCT_SUPER_BOOST }
  ModalBottomSheet(onDismissRequest = onClose) {
    Column(Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Boosters Shop", fontWeight = FontWeight.Bold, fontSize = 20.sp)
      Text("Munten: $coins", color = MaterialTheme.colorScheme.secondary)
      boosterCatalog().forEach { def ->
        val owned = ownedBoosters[def.id] ?: 0
        if (owned > 0) {
          Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(12.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(def.title, fontWeight = FontWeight.SemiBold)
                if (def.isSuper) { Spacer(Modifier.width(6.dp)); Text("SUPER", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp) }
                Spacer(Modifier.weight(1f))
                Text("$owned×", fontSize = 12.sp)
              }
              Spacer(Modifier.height(4.dp))
              Text(def.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(Modifier.height(8.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Prijs: ${def.price}", fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                val canBuy = coins >= def.price
                Button(onClick = { if (canBuy) onPurchase(def.id) }, enabled = canBuy, shape = RoundedCornerShape(50)) { Text("Koop", fontSize = 12.sp) }
              }
            }
          }
        }
      }
      Divider()
      Text("Koop munten met echt geld:", fontWeight = FontWeight.Medium, fontSize = 14.sp)
      if (loading) Text("Laden...")
      error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      if (!loading) {
        if (packDetails != null) {
          val price = packDetails.oneTimePurchaseOfferDetails?.formattedPrice ?: packDetails.name
          Card(shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("Booster Pack", fontWeight = FontWeight.Medium)
              Text("3 willekeurige boosters + 150 munten", fontSize = 12.sp)
              Button(onClick = { if (activity != null && billingManager != null) billingManager.launchPurchaseFlow(activity, packDetails) }, shape = RoundedCornerShape(50)) { Text("Koop ($price)", fontSize = 12.sp) }
            }
          }
        }
        if (superDetails != null) {
          val price = superDetails.oneTimePurchaseOfferDetails?.formattedPrice ?: superDetails.name
          Card(shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("Super Boost", fontWeight = FontWeight.Medium)
              Text("1 schild booster + 500 munten", fontSize = 12.sp)
              Button(onClick = { if (activity != null && billingManager != null) billingManager.launchPurchaseFlow(activity, superDetails) }, shape = RoundedCornerShape(50)) { Text("Koop ($price)", fontSize = 12.sp) }
            }
          }
        }
      }
      Spacer(Modifier.height(8.dp))
      Button(onClick = onClose, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)) { Text("Sluiten") }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsModal(
  onClose: () -> Unit,
  signedIn: Boolean,
  onSignIn: () -> Unit,
  ach: Achievements,
  bestScore: Int,
  mode: String,
  wrapWalls: Boolean,
  onToggleWrap: () -> Unit,
  soundOn: Boolean,
  onToggleSound: () -> Unit,
  musicOn: Boolean,
  onToggleMusic: () -> Unit,
  musicVol: Float,
  onMusicVol: (Float) -> Unit,
  hapticsOn: Boolean,
  onToggleHaptics: () -> Unit,
  showDiag: Boolean,
  onToggleDiag: () -> Unit,
  keepScreenOn: Boolean,
  onToggleKeepOn: () -> Unit,
  nativeAd: com.google.android.gms.ads.nativead.NativeAd?,
  removeAds: Boolean,
  removeAdsDetails: ProductDetails?,
  billingLoading: Boolean,
  billingError: String?,
  onPurchaseRemoveAds: () -> Unit,
  onReloadRemoveAds: () -> Unit,
  isDebugBuild: Boolean,
) {
  ModalBottomSheet(onDismissRequest = onClose) {
    Column(Modifier.fillMaxWidth().padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Instellingen", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Button(onClick = onClose, shape = RoundedCornerShape(50)) { Text("Sluit") }
      }
      Text("Huidige modus: ${if (mode=="classic") "Classic" else "Time Attack"}", fontSize = 12.sp)
      Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Google Play Games", fontWeight = FontWeight.SemiBold)
          Text(if (signedIn) "Ingelogd" else "Niet ingelogd", color = if (signedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontSize = 12.sp)
          if (!signedIn) Button(onClick = onSignIn, shape = RoundedCornerShape(50)) { Text("Inloggen") }
        }
      }
      Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          SettingToggle("Geluid", soundOn, onToggleSound)
          SettingToggle("Muziek", musicOn, onToggleMusic)
          VolumeSlider("Muziek Volume", musicVol, onMusicVol)
          SettingToggle("Haptics", hapticsOn, onToggleHaptics)
          SettingToggle("Wrap muren", wrapWalls, onToggleWrap)
          SettingToggle("Diagnostics", showDiag, onToggleDiag)
          SettingToggle("Scherm aanhouden", keepScreenOn, onToggleKeepOn)
        }
      }
      Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Achievements", fontWeight = FontWeight.SemiBold)
          Text("Longest: ${ach.longest} | Combo: ${ach.highestCombo} | Games: ${ach.games}", fontSize = 12.sp)
          Text("Food: ${ach.food} | Best: $bestScore", fontSize = 12.sp)
        }
      }
      Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Advertenties", fontWeight = FontWeight.SemiBold)
          when {
            removeAds -> Text("Ads verwijderd ✓", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            billingLoading -> Text("Product laden...", fontSize = 12.sp)
            billingError != null -> {
              Text("Fout: ${billingError}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
              Button(onClick = onReloadRemoveAds, shape = RoundedCornerShape(50)) { Text("Opnieuw", fontSize = 12.sp) }
            }
            removeAdsDetails != null -> {
              val price = removeAdsDetails.oneTimePurchaseOfferDetails?.formattedPrice ?: "Koop"
              Button(onClick = onPurchaseRemoveAds, shape = RoundedCornerShape(50)) { Text("Verwijder Ads ($price)", fontSize = 12.sp) }
            }
            else -> Button(onClick = onReloadRemoveAds, shape = RoundedCornerShape(50)) { Text("Laden", fontSize = 12.sp) }
          }
        }
      }
    }
  }
}

@Composable private fun SettingToggle(label: String, value: Boolean, onToggle: () -> Unit) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
    Switch(checked = value, onCheckedChange = { onToggle() })
  }
}
@Composable private fun VolumeSlider(label: String, value: Float, onChange: (Float)->Unit) {
  Column { Text(label, fontSize = 12.sp); Slider(value = value, onValueChange = onChange, valueRange = 0f..0.2f) }
}

