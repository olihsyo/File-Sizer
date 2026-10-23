package com.filesizer.app.core.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.filesizer.app.core.navigation.Screen

@Composable
fun FileSizerNavigationBar(
    currentRoute: String?,
    onNavigateToRoute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Screen.Home,
        Screen.Browse,
        Screen.StorageAnalyzer,
        Screen.Settings
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp,
        modifier = modifier
    ) {
        items.forEach { screen ->
            val isSelected = currentRoute?.startsWith(screen.route.substringBefore("?")) == true
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigateToRoute(screen.route) },
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = stringResource(screen.titleRes),
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = stringResource(screen.titleRes),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
