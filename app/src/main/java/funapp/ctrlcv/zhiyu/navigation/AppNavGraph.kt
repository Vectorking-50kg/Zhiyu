package funapp.ctrlcv.zhiyu.navigation

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import funapp.ctrlcv.zhiyu.feature.auth.AuthWebViewScreen
import funapp.ctrlcv.zhiyu.ui.MonitorApp
import funapp.ctrlcv.zhiyu.ui.MonitorViewModel

@Composable
fun AppNavGraph() {
    val navigation = rememberNavController()
    val viewModel: MonitorViewModel = hiltViewModel()
    NavHost(navigation, startDestination = "monitor", modifier = Modifier.fillMaxSize()) {
        composable("monitor") {
            MonitorApp(viewModel) { platform, accountId ->
                navigation.navigate("auth/${platform.key}" + (accountId?.let { "?accountId=${Uri.encode(it)}" } ?: ""))
            }
        }
        composable("auth/{platform}?accountId={accountId}", arguments = listOf(
            navArgument("accountId") { type = NavType.StringType; nullable = true; defaultValue = null },
        )) {
            AuthWebViewScreen(
                onBack = { navigation.popBackStack() },
                onSuccess = { viewModel.onAuthorizationSuccess(); navigation.popBackStack() },
            )
        }
    }
}
