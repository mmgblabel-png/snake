# Keep default rules only; no special minification for this sample
# Intentionally left blank; using AGP default optimized rules

# Preserve Google Mobile Ads classes (recommended minimal set)
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Preserve Play Billing client models
-keep class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.api.**

# Preserve Play Games Services models (for Parcelable / reflection)
-keep class com.google.android.gms.games.** { *; }
-dontwarn com.google.android.gms.games.**

# Coroutines (avoid stripping debug metadata that can help crash reports)
-keepclassmembers class kotlinx.coroutines.** { *; }

# Keep your SfxEngine tone generation (not strictly needed but safe)
-keep class com.mmgb.snake.SfxEngine { *; }

# Keep MainActivity (entry point) and composables (Compose compiler already generates stability models, but we add explicit rule for safety)
-keep class com.mmgb.snake.MainActivity { *; }
-keep class com.mmgb.snake.SnakeAppKt { *; }

# Keep Google Mobile Ads and Play Games classes (avoid reflection related issues)
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.games.** { *; }
