# Add any project specific keep rules here:

# F-Droid Reproducible Build Fixes
-keep class kotlinx.coroutines.CoroutineExceptionHandler
-keep class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-dontnote kotlinx.coroutines.internal.MainDispatcherFactory
-dontwarn org.slf4j.**
