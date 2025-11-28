# SnakeV1 – Publish-ready build

This project is configured to produce a signed Android App Bundle (AAB) suitable for Play Store upload.

Quick checklist
- App ID: `com.mmgb.snake` (configured in `app/build.gradle.kts`)
- Min/Target/Compile SDK: minSdk 23, targetSdk 36, compileSdk 36
- Release signing: loaded from `keystore.properties` at the project root
- Code shrinking: R8 minification and resource shrinking enabled for `release`
- Launcher activity: `com.mmgb.snake.MainActivity` (declared in `AndroidManifest.xml`)
- Permissions: VIBRATE, INTERNET, ACCESS_NETWORK_STATE, Google Ads ID (for AdMob)
- Privacy Policy Activity: `PrivacyPolicyActivity` with button in UI
- Consent: UMP flow initiated (gated ad load in `SnakeApp`)
- In-app products: `remove_ads`, `booster_pack`, `super_boost` (coin + booster awards)

## Monetization Overview
| Product | Type | Award | Description |
|---------|------|-------|-------------|
| remove_ads | One-time | Disables all AdMob ads | Cleaner experience |
| booster_pack | One-time | 3 random standard boosters + 150 coins | Progress accelerator |
| super_boost | One-time | 1 shield booster + 500 coins | High-value protection & currency |

Coins are also earned via gameplay: `score / 10` (minimum 1 per game). Boosters can be purchased with coins in the in-app shop.

## Release QA Checklist (pre-upload)
- [ ] Bump `versionCode` and `versionName`.
- [ ] Run `./gradlew clean assembleRelease` (verify AAB at `app/build/outputs/bundle/release`).
- [ ] Launch debug build on physical device (verify banner/interstitial frequency, remove-ads, booster purchases, consent form).
- [ ] Test Remove Ads, Booster Pack, Super Boost (sandbox) and confirm inventory/coin awards.
- [ ] Inspect memory (Android Studio profiler) during long session (>10 min): snake growth, particles cleared.
- [ ] Check Play Games sign-in + leaderboard submission.
- [ ] Verify achievements unlock (length, combo, score milestones).
- [ ] Confirm haptics respect system setting.
- [ ] Validate edge-to-edge rendering & safe gesture navigation.
- [ ] Run `./gradlew detekt test` (triage remaining warnings; suppressed non-critical complexity).

## Data Safety & Privacy (Play Console)
Declare:
- Advertising: Uses AdMob (device identifiers for ads/analytics).
- Crash Logging: Local only in SharedPreferences (not transmitted).
- Purchases: BillingClient for one-time products; data not uploaded externally.
- No collection of location, contacts, photos, or sensitive personal data.
Add privacy policy URL matching `PrivacyPolicyActivity` content.

## Security Hardening
- R8/ProGuard: Enabled (minify + shrink resources).
- No dynamic class loading beyond SDK internals.
- Crash log is minimal (no PII).

## Performance Notes
- Tick interval clamps to ≥40ms.
- Particle list pruned by life timer (loop guarded against leaks).
- Interstitial refresh limited by frequency & consent/remove-ads.

## Known Post-Release Enhancements
- Migrate prefs & achievements to DataStore.
- Remote config for ad frequency.
- Minimal analytics events (consent-driven).
- Instrumented UI tests for shop + consent.

## Dev Notes
- Preferences keys centralized in `PreferenceKeys.kt` (`PrefKeys`).
- Booster selection rules (max 3 standard + 1 super) defined in `BoosterSelection.kt`.
- Achievements persisted via `kotlinx.serialization` with legacy fallback.
- Added structured billing award logic awarding coins & boosters idempotently.

### Building (Windows)
```bat
cd /d C:\Users\mmgbl\Downloads\SnakeV1
gradlew.bat clean assembleDebug --no-daemon
```

### Release Build
```bat
cd /d C:\Users\mmgbl\Downloads\SnakeV1
gradlew.bat clean bundleRelease --no-daemon
```
Output: `app/build/outputs/bundle/release/app-release.aab`.

### Troubleshooting
- Java 17 required. Set `org.gradle.java.home` if needed.
- Use `--stacktrace` for failing builds.
