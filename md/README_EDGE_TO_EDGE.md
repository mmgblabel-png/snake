# Edge-to-Edge (Android 15 / SDK 35+) Compliance

Starting with Android 15 (API 35), apps that target SDK 35+ are shown edge-to-edge by default. This project targets SDK 36, so explicit inset handling is required to prevent UI elements from clashing with system bars.

## Implementation Summary
- `MainActivity` and `PrivacyPolicyActivity` call `enableEdgeToEdge()` using the newer `SystemBarStyle` overload (transparent dark bars) instead of legacy `setStatusBarColor` / `setNavigationBarColor` calls or theme overrides.
- We call `WindowCompat.setDecorFitsSystemWindows(window, false)` to opt out of legacy fitting and manually handle insets.
- Deprecated or discouraged theme attributes for system bar colors and `windowLayoutInDisplayCutoutMode` have been removed from XML; runtime code sets cutout mode via `WindowManager.LayoutParams` (using `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` when available) and bar appearance via `SystemBarStyle`.
- Compose UI elements avoid overlap with system bars using `Modifier.systemBarsPadding()` (HUD overlays, Privacy policy screen, etc.).
- Immersive gameplay hides system bars via `WindowInsetsControllerCompat.hide(Type.systemBars())` with transient swipe behavior; when bars reappear, padding preserves layout safety.

## Migration Away From Deprecated APIs
Android 15 tooling may flag direct use of:
- `Window.setStatusBarColor`
- `Window.setNavigationBarColor`
- XML `windowLayoutInDisplayCutoutMode` with `shortEdges`

We replaced these with:
- `enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))`
- Conditional runtime cutout configuration using `WindowManager.LayoutParams` (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`), avoiding XML constants.
- Centralized appearance control through `WindowInsetsControllerCompat` for behavior only (not colors).

## Insets Handling in Compose
Use:
- `Modifier.systemBarsPadding()` for combined status + navigation bars.
- `Modifier.statusBarsPadding()` / `Modifier.navigationBarsPadding()` if granular control is required.

## Testing Checklist
1. Android 15 emulator (portrait & landscape) – HUD not clipped, bars transparent.
2. Swipe to reveal transient system bars – content still visible (padding applied).
3. Privacy Policy screen – top text not obscured; bottom scroll area clear above nav bar.
4. Rotate device – board centers correctly; overlays remain within safe area.
5. Cutout device (Pixel with notch) – snake board not unintentionally cropped.
6. Gesture vs 3-button navigation – navigation bar visibility doesn’t overlap overlays.

## Future Enhancements
- Debounce immersive reapply to reduce flicker after transient bar swipe.
- Predictive back visualization (fade or progress indicator) for pause/game over.
- Animated inset transitions using `WindowInsetsAnimationCompat`.
- Screenshot/UI tests validating insets across API levels and window sizes.
- Dynamic gradient scrim behind system bars for light backgrounds.

## References
- Android Dev: Edge-to-Edge & WindowInsets APIs.
- AndroidX Activity `enableEdgeToEdge()` helper.

This document supplements `README.md` focusing specifically on edge-to-edge compliance and migration for Android 15+.
