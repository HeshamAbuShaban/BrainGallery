# Keep Hilt / Room / Media3 / MLKit
-keep class dagger.hilt.** { *; }
-keep class androidx.room.** { *; }
-keep class androidx.media3.** { *; }
# TFLite (reflection-light but keep the API surface)
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
# Room entities accessed from DAOs
-keep class com.brain.gallery.data.local.** { *; }
# ML Kit common pipeline
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
