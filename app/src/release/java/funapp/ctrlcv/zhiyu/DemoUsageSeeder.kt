package funapp.ctrlcv.zhiyu

import android.app.Application

/** Release builds never seed sample accounts or usage data. */
internal object DemoUsageSeeder {
    fun seed(application: Application) = Unit
}
