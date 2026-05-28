package funapp.ctrlcv.zhiyu.core.ui.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

object CustomColors {
    val topBarColors: TopAppBarColors
        @Composable get() = if (!LocalDarkMode.current) TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.surfaceContainer,
            scrolledContainerColor = colorScheme.surfaceContainer
        ) else TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.surfaceContainer,
            scrolledContainerColor = colorScheme.surfaceContainer
        )

    val cardColors: CardColors
        @Composable get() = CardDefaults.cardColors(containerColor = colorScheme.surfaceBright)

    val listItemColors: ListItemColors
        @Composable get() = ListItemDefaults.colors(containerColor = colorScheme.surfaceBright)
}
