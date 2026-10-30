# FOXUS ProGuard / R8 rules
-keepattributes *Annotation*,InnerClasses,Signature
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable <fields>;
    companion object;
}
