# Add project specific ProGuard rules here.

# Keep LiteRT-LM classes
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.LiteRtLmJniException

# Keep Hilt components
-keep class **$Hilt_ProductionComponentImpl { *; }
-keep @dagger.hilt.android.components.** { *; }
-keepclassmembers @dagger.hilt.android.components.** { *; }

# Keep data classes
-keep class com.liteagent.textadventure.model.** { *; }

# Keep Room
-keep class **_**Impl { *; }

# Logging utilities
-assumenosideeffects android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** w(...);
    public static *** e(...);
    public static *** i(...);
}
