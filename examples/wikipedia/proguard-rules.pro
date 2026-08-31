# Wikipedia app ProGuard rules

# Keep kotlinx.serialization models — R8 strips them and breaks JSON parsing
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep class com.thelightphone.wikipedia.** { *; }
-keepclassmembers class com.thelightphone.wikipedia.** { *; }

# Keep DataStore preferences keys
-keep class androidx.datastore.preferences.core.* { *; }

# Keep Light SDK classes
-keep class com.thelightphone.sdk.** { *; }

# Keep Ktor/OkHttp
-keep class io.ktor.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Suppress Ktor's IntellijIdeaDebugDetector referencing java.lang.management (not on Android)
-dontwarn java.lang.management.**

# Keep Java standard libs used by reflection
-keep class java.lang.reflect.** { *; }

# Keep ViewModel
-keep class com.thelightphone.wikipedia.WikipediaViewModel { *; }
-keep class com.thelightphone.wikipedia.WikipediaApi { *; }
