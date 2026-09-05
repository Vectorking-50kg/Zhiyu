# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class funapp.ctrlcv.zhiyu.core.domain.model.** { *; }
-keep class funapp.ctrlcv.zhiyu.core.storage.OAuthCredential { *; }
-keep class funapp.ctrlcv.zhiyu.core.storage.BackupData { *; }
-keep class funapp.ctrlcv.zhiyu.feature.widget.WidgetUsageData { *; }
-keep class funapp.ctrlcv.zhiyu.feature.widget.WidgetPlatformItem { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
