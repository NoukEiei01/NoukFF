# VPN App ProGuard Rules
-keep class com.vpnapp.model.** { *; }
-keep class com.vpnapp.service.** { *; }
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
