############################################
# Firebase / Play Services
############################################
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

############################################
# Firestore object mapping (CustomClassMapper)
############################################
-keepclassmembers class * {
    public <init>();
}

# Keep your Firestore models (VERY IMPORTANT)
-keep class com.girlspace.app.data.feed.** { *; }
-keep class com.girlspace.app.model.** { *; }

############################################
# Hilt / DI (usually ok, but safe)
############################################
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**
-dontwarn javax.inject.**

############################################
# Kotlin coroutines / Flow (avoid stripping)
############################################
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

############################################
# Jetpack Compose (generally ok, but safe)
############################################
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**
