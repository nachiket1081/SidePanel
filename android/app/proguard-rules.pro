# SidePanel ProGuard / R8 Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.jetbrains.annotations.* <fields>;
    @org.jetbrains.annotations.* <methods>;
}
# Keep Kotlinx Serialization models
-keepattributes *Annotation*,InnerClasses,EnclosingMethod
-keepclassmembers class com.sidepanel.app.data.protocol.** {
    *** Companion;
    public <methods>;
}
