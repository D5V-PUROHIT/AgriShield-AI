# ProGuard Rules for AgriShield AI

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keep public class org.tensorflow.lite.InterpreterApi { *; }
-dontwarn org.tensorflow.lite.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.agrishield.ai.models.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**
