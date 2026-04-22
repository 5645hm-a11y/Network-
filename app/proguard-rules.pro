# Network Absorb ProGuard rules

# Keep VPN service
-keep class com.networkabsorb.vpn.** { *; }
-keep class com.networkabsorb.proxy.** { *; }
-keep class com.networkabsorb.security.** { *; }

# BouncyCastle - needed for cert generation
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
