# Preserve metadata used by serialization and annotations.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Java serialization support for values passed between activities.
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keepclassmembers class com.theuncoaffair.fishpipe.data.MediaItem { <fields>; }
-keepclassmembers class com.theuncoaffair.fishpipe.data.MediaItem$* { <fields>; }
