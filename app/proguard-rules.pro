# Genko Notes release rules. Compose + AndroidX ship their own consumer rules;
# these cover the persisted model (MMKV JSON must survive updates) and MMKV.

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# kotlinx.serialization: keep every @Serializable model and its generated
# $$serializer, plus the Companion hooks the plugin generates.
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class ** {
    *** Companion;
}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**
-dontnote kotlinx.serialization.AnnotationsKt

# MMKV native wrapper (belt and braces over its own consumer rules).
-keep class com.tencent.mmkv.** { *; }

# Persisted model, explicitly — notes must decode after every update.
-keep class com.rgprince.genkonotes.data.StoredNote { *; }
-keep class com.rgprince.genkonotes.editor.InlineRun { *; }
-keep class com.rgprince.genkonotes.editor.HighlightInk { *; }
