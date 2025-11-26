# SnakeV1 Production Release

## Overview
SnakeV1 is a full-screen edge-to-edge Compose Android game featuring:
- Classic and Time Attack snake modes
- Power-ups (Ghost, Fast, Slow, Double, Phase)
- Booster economy (length, score multiplier, extra time, shield) with coin shop
- In-app purchases (Remove Ads, Booster Pack, Super Boost) via Play Billing v6
- AdMob banner, interstitial (shown on game over), and optional native ad in settings
- Google Play Games Services (leaderboard + achievements)
- GDPR consent via UMP

## Build
```powershell
./gradlew.bat assembleRelease
```
Output AAB: `app/build/outputs/bundle/release/app-release.aab`

## Signing
Provide `keystore.properties` (mirroring `keystore.properties.example`) with:
```
storeFile=keystore/upload-keystore.jks
storePassword=****
keyAlias=upload
keyPassword=****
```
If absent, release build will be unsigned (sign in Play Console instead).

## Versioning
Current versionCode: 7, versionName: 7.1. Increment both for subsequent store releases.

## Ads & Consent
Real AdMob unit IDs are defined in `app/src/main/res/values/strings.xml`. Interstitial frequency limited (every 2 games) and only after consent resolution.

## Diagnostics
Set `showDiag` preference to true to see FPS/TPS and ad diagnostics. Disabled by default for production.

## Testing Economy Award Logic
Run unit tests:
```powershell
./gradlew.bat testDebugUnitTest --tests "com.mmgb.snake.BillingAwardTest"
```

## Proguard
`proguard-rules.pro` keeps Play Billing, Play Games, and Ads SDK classes.

## Checklist Before Store Submission
- [ ] Update app icon & adaptive icon if needed
- [ ] Localize strings (Dutch + English)
- [ ] Verify consent flow in EEA device/emulator
- [ ] Upload signed AAB to Play Console internal testing
- [ ] Validate in-app purchases live (booster_pack, super_boost, remove_ads)
- [ ] Check leaderboard & achievements IDs match Play Console
- [ ] Fill in privacy policy URL on Play listing

## Troubleshooting
| Issue | Action |
|-------|--------|
| Interstitial not showing | Ensure consentResolved true, removeAds false, frequency condition met. Use debug diag panel. |
| Purchase grants nothing | Check Billing logs; verify product IDs in Play Console; ensure acknowledged. |
| Ads test vs production | Confirm using production IDs, not test ID string. |
| Achievements not unlocking | Verify IDs start with `Cgk` and signed in. |

Enjoy your release!
