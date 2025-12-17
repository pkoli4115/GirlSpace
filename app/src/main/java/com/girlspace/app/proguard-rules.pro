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
# Keep runtime annotations + signatures (needed for Gson/reflection)
############################################
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses

############################################
# Gson
############################################
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Keep fields annotated with @SerializedName (most important)
-keepclassmembers class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# If you use @Expose anywhere
-keepclassmembers class * {
  @com.google.gson.annotations.Expose <fields>;
}

############################################
# Keep your app models that are serialized / Firestore mapped
############################################
-keep class com.girlspace.app.model.** { *; }
-keep class com.girlspace.app.data.** { *; }        # includes reels/feed/chat repos & models
-keep class com.girlspace.app.ui.notifications.** { *; }
-keep class com.girlspace.app.notifications.** { *; }
-keep class com.girlspace.app.moderation.** { *; }

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

############################################
# Kotlinx Serialization (REQUIRED)
############################################

# Keep Kotlin metadata (ABSOLUTELY REQUIRED)
-keep class kotlin.Metadata { *; }
-keepattributes KotlinMetadata
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations
-keepattributes Signature,InnerClasses,EnclosingMethod

# Keep kotlinx.serialization runtime
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Keep generated serializer classes
-keep class **$serializer { *; }

# Keep serializer() accessors
-keepclassmembers class * {
    public static ** serializer(...);
}

# Keep companion objects used by serializers
-keepclassmembers class * {
    ** Companion;
}
############################################
# Kotlinx Serialization – FIX (Release crash)
############################################

# IMPORTANT: your previous rule used $$serializer (wrong)
-keep class **$serializer { *; }
-keepnames class **$serializer

# Keep Kotlin metadata needed by reflection/serialization
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes KotlinMetadata

# Keep kotlinx.serialization runtime
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Keep serializer() accessors + Companion (used by generated serializers)
-keepclassmembers class * {
    public static ** serializer(...);
}
-keepclassmembers class * {
    ** Companion;
}

############################################
# Reel model – keep + keepnames (your crash ua.a)
############################################
-keep class com.girlspace.app.data.reels.Reel { *; }
-keepclassmembers class com.girlspace.app.data.reels.Reel { *; }
-keepnames class com.girlspace.app.data.reels.Reel
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * { @androidx.annotation.Keep *; }
