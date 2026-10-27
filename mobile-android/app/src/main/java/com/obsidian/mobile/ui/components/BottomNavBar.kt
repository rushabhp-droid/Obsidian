package com.obsidian.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obsidian.mobile.theme.ActiveIndicator
import com.obsidian.mobile.theme.NavBarBackground
import com.obsidian.mobile.theme.NavIndicatorShape
import com.obsidian.mobile.theme.OnActiveIndicator
import com.obsidian.mobile.theme.OnSurfaceVariant
import com.obsidian.mobile.theme.OutlineVariant

import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.ShowChart

enum class NavTab(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    ACTIVITY("activity", "Activity", Icons.Filled.ShowChart, Icons.Outlined.ShowChart),
    RULES("rules", "My Rules", Icons.Filled.Tune, Icons.Outlined.Tune),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings);

    companion object {
        // Alias for backward compatibility
        val MESSAGES get() = ACTIVITY
    }
}

@Composable
fun ObsidianBottomNavBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NavBarBackground)
    ) {
        Divider(
            thickness = 1.dp,
            color = OutlineVariant.copy(alpha = 0.4f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            onTabSelected(tab)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .width(64.dp)
                            .then(
                                if (isSelected) {
                                    // STRICT: 8dp corner radius, NEVER pill
                                    Modifier
                                        .clip(NavIndicatorShape)
                                        .background(ActiveIndicator)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.title,
                            tint = if (isSelected) OnActiveIndicator else OnSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = tab.title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) OnActiveIndicator else OnSurfaceVariant
                    )
                }
            }
        }
    }
}
