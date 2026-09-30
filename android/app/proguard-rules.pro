# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.schoolproject.screenclose.**$$serializer { *; }
-keepclassmembers class com.schoolproject.screenclose.** { *** Companion; }
-keepclasseswithmembers class com.schoolproject.screenclose.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit (suspend functions + generic signatures)
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# MSAL / Microsoft identity common
-keep class com.microsoft.identity.** { *; }
-dontwarn com.microsoft.identity.**
-keep class com.google.gson.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn io.opentelemetry.**
-dontwarn com.nimbusds.**
