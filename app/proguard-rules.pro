# Библиотеки (Compose, CameraX, DataStore) приносят свои consumer-правила.
# Собственных reflection-вызовов в приложении нет.

# ML Kit находит свои компоненты через reflection (ComponentRegistrar по имени из манифеста).
# Без этих правил R8 в release-сборке их вырезал, и TextRecognition.getClient() падал
# с NullPointerException при открытии камеры.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_bundled_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**
