package funapp.ctrlcv.zhiyu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import funapp.ctrlcv.zhiyu.navigation.AppNavGraph
import funapp.ctrlcv.zhiyu.core.ui.theme.ZhiyuTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZhiyuTheme {
                AppNavGraph()
            }
        }
    }
}
