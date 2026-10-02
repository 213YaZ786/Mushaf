# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class org.mushaf.app.** {
    *** Companion;
}
-keepclasseswithmembers class org.mushaf.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
