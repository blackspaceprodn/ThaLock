# =============================================================================
# ThaLock — R8/ProGuard rules for release builds.
#
# isMinifyEnabled = true and isShrinkResources = true are on for `release`,
# so anything reached only via reflection MUST be kept here or it will be
# stripped and the app will crash at runtime.
# =============================================================================

# -----------------------------------------------------------------------------
# Kotlin / Coroutines
# -----------------------------------------------------------------------------
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations

-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.debug.**

# -----------------------------------------------------------------------------
# Room — DAOs, entities, type converters, generated _Impl classes
# -----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.TypeConverters class * { *; }
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class **_Impl { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
-dontwarn androidx.room.paging.**

# -----------------------------------------------------------------------------
# SQLCipher — JNI bridge classes are looked up by name from native code
# -----------------------------------------------------------------------------
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.**
-keep class androidx.sqlite.** { *; }

# -----------------------------------------------------------------------------
# Gson — model serialization. Anything Gson reads/writes via reflection
# must keep its fields. This covers the data.model package.
# -----------------------------------------------------------------------------
-keep class com.google.gson.** { *; }
-keep class com.thalock.app.data.model.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Gson uses TypeToken via anonymous subclasses
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keepattributes Signature

# -----------------------------------------------------------------------------
# Compose — keep ProvidableCompositionLocal default values + @Composable
# annotations needed by the runtime
# -----------------------------------------------------------------------------
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# -----------------------------------------------------------------------------
# AndroidX Security / Tink — security-crypto:1.1.0-alpha06 uses Tink under
# the hood, which loads key managers reflectively from class names.
# -----------------------------------------------------------------------------
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**

# -----------------------------------------------------------------------------
# Biometric
# -----------------------------------------------------------------------------
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# -----------------------------------------------------------------------------
# ML Kit — text recognition loads model+manifest classes reflectively
# -----------------------------------------------------------------------------
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# -----------------------------------------------------------------------------
# CameraX
# -----------------------------------------------------------------------------
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# -----------------------------------------------------------------------------
# ThaLock — keep public Activity / Provider entry points referenced by
# AndroidManifest. R8 already keeps these from the manifest, but pinning
# them here documents the contract and protects DocumentsProvider, which
# is instantiated by the system.
# -----------------------------------------------------------------------------
-keep class com.thalock.app.MainActivity { *; }
-keep class com.thalock.app.ThaLockApp { *; }
-keep class com.thalock.app.provider.** { *; }

# -----------------------------------------------------------------------------
# Strip android.util.Log calls from release builds. Keep w/e/wtf so genuine
# crash signal still surfaces in Play Vitals.
# -----------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# -----------------------------------------------------------------------------
# Reflective enums — Room/Gson both serialize enums by name
# -----------------------------------------------------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# -----------------------------------------------------------------------------
# Native methods (SQLCipher, Tink, ML Kit, CameraX all bind JNI symbols)
# -----------------------------------------------------------------------------
-keepclasseswithmembernames class * {
    native <methods>;
}

# -----------------------------------------------------------------------------
# Parcelable — required if any model is sent through Intents / Bundles
# -----------------------------------------------------------------------------
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
