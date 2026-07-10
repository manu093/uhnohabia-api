# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# Firestore model serialization
-keep class com.sharedshoppinglists.app.data.local.entity.** { *; }
-keep class com.sharedshoppinglists.app.domain.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *

# Kotlin serialization
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }

# Keep R8 from stripping Compose
-keep class androidx.compose.** { *; }