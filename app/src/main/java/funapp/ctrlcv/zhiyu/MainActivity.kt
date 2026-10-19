package funapp.ctrlcv.zhiyu

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import funapp.ctrlcv.zhiyu.navigation.AppNavGraph
import funapp.ctrlcv.zhiyu.core.ui.theme.ZhiyuTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import funapp.ctrlcv.zhiyu.feature.widget.WidgetDestination
import funapp.ctrlcv.zhiyu.feature.widget.WidgetIntents

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var widgetDestination by mutableStateOf<WidgetDestination?>(null)
    private var widgetRequestId by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleWidgetIntent(intent)
        enableEdgeToEdge()
        setContent {
            ZhiyuTheme {
                AppNavGraph(widgetDestination, widgetRequestId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        WidgetIntents.destination(intent)?.let {
            widgetDestination = it
            widgetRequestId++
        }
    }
}
