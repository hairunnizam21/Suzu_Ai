# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class io.suzuai.app.**$$serializer { *; }
-keepclassmembers class io.suzuai.app.** {
    *** Companion;
}
-keepclasseswithmembers class io.suzuai.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase

# Compose
-keep class androidx.compose.** { *; }

# Keep DTO classes (data classes used in serialization)
-keep class io.suzuai.app.data.** { *; }
