package funapp.ctrlcv.zhiyu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun ApplicationIcon(modifier: Modifier = Modifier, size: Dp = 54.dp) {
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
    val configuration = LocalConfiguration.current
    val bitmap = remember(context, pixels, configuration) {
        // Load the manifest's actual icon, including the platform's adaptive-icon mask.
        context.applicationInfo.loadIcon(context.packageManager).toBitmap(pixels, pixels).asImageBitmap()
    }
    Image(bitmap, "知余应用图标", modifier.size(size))
}
