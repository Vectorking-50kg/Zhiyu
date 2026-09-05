package funapp.ctrlcv.zhiyu

import android.app.Application

/** Regular debug builds intentionally keep the user's local state untouched. */
internal object DemoUsageSeeder {
    fun seed(application: Application) = Unit
}
