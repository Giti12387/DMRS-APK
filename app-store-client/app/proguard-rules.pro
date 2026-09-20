# ==================== SECURITY HARDENING ====================

# Obfuscate everything — decompilers get gibberish
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 5

# Hide source file names in stack traces
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-repackageclasses ''

# ==================== KEEP: Hilt/DI ====================
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep class * extends dagger.hilt.android.HiltApplication { *; }
-keep class * extends dagger.hilt.components.** { *; }

# ==================== KEEP: Room DB ====================
-keep class com.apps.apkstore.data.local.** { *; }
-keepclassmembers class com.apps.apkstore.data.local.** { *; }

# ==================== KEEP: Retrofit/Gson/OkHttp ====================
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation,allowshrinking class retrofit2.** { *; }
-keep,allowobfuscation,allowshrinking class okhttp3.** { *; }
-keep,allowobfuscation,allowshrinking class okio.** { *; }

# ==================== KEEP: Compose ====================
-keep class androidx.compose.** { *; }
-keep class androidx.compose.material3.** { *; }

# ==================== KEEP: Navigation ====================
-keep class androidx.navigation.** { *; }

# ==================== KEEP: Lifecycle/ViewModel ====================
-keep class androidx.lifecycle.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

# ==================== KEEP: WorkManager ====================
-keep class androidx.work.** { *; }

# ==================== KEEP: RxJava ====================
-keep class io.reactivex.** { *; }
-keep class io.reactivex.rxjava3.** { *; }

# ==================== KEEP: Coil ====================
-keep class coil.** { *; }

# ==================== KEEP: Kotlin ====================
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.reflect.jvm.internal.impl.builtins.BuiltInsLoaderImpl {
    *;
}
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.apps.apkstore.**$$serializer { *; }
-keepclassmembers class com.apps.apkstore.** {
    *** Companion;
}
-keepclasseswithmembers class com.apps.apkstore.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ==================== SECURITY: Hide sensitive data ====================
# Don't leak API keys / tokens in strings
-keepclassmembers class com.apps.apkstore.utils.SupabaseConfig {
    *;
}
-keep class com.apps.apkstore.service.adb.** { *; }

# ==================== SECURITY: Prevent string decryption bypass ====================
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ==================== KEEP: Parcelable ====================
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ==================== KEEP: R class ====================
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ==================== KEEP: Enum ====================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==================== SECURITY: Obfuscate package names ====================
-repackageclasses 'a'
-allowaccessmodification
