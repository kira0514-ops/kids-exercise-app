# Keep kotlinx.serialization models used for JSON parsing of Yahoo Finance responses.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.kira.stockscope.**$$serializer { *; }
-keepclassmembers class com.kira.stockscope.** {
    *** Companion;
}
-keepclasseswithmembers class com.kira.stockscope.** {
    kotlinx.serialization.KSerializer serializer(...);
}
