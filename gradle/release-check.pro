# The instrumentation runner references this shared dependency after the target APK is shrunk.
# Keep only the test runtime bridge; production sync/serialization code remains fully optimized.
-keep class androidx.tracing.Trace { *; }
# AndroidJUnitRunner/Compose tests also share the target's Kotlin standard library. The test
# APK cannot restore APIs that R8 already inlined out of the target APK.
-keep class kotlin.** { *; }

# Entry points called from the separately compiled test APK must retain their signatures.
# The notification state, codec and all private implementation details remain fully shrinkable.
-keep,allowobfuscation class funapp.ctrlcv.zhiyu.core.data.notification.UsageAlertManager {
    public <init>(...);
    public void onUsageUpdated(funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo);
}
-keep,allowobfuscation class funapp.ctrlcv.zhiyu.core.data.notification.NotificationPreferences {
    public <init>(...);
    public boolean getNotificationsEnabled();
    public void setNotificationsEnabled(boolean);
}
-keep,allowobfuscation class funapp.ctrlcv.zhiyu.core.storage.AccountStore {
    public <init>(...);
}
-keep,allowobfuscation class funapp.ctrlcv.zhiyu.core.network.interceptor.SessionEventBus {
    public <init>();
}
