package com.obsidian.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obsidian.mobile.data.UiThreatRule
import com.obsidian.mobile.network.IndicatorType
import com.obsidian.mobile.theme.BadgeShape
import com.obsidian.mobile.theme.MonospaceBodyMedium
import com.obsidian.mobile.theme.OnPrimaryContainer
import com.obsidian.mobile.theme.OnSurface
import com.obsidian.mobile.theme.OnSurfaceVariant
import com.obsidian.mobile.theme.OutlineVariant
import com.obsidian.mobile.theme.PrimaryContainer
import com.obsidian.mobile.theme.Surface
import com.obsidian.mobile.theme.SuspiciousTertiaryContainer

enum class RulesTab {
    BLOCKED,
    TRUSTED
}

@Composable
fun RulesScreen(
    blockedRules: List<UiThreatRule>,
    trustedRules: List<UiThreatRule>,
    onAddRuleClick: () -> Unit,
    onDeleteRuleClick: (UiThreatRule) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(RulesTab.BLOCKED) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Surface)
            .padding(horizontal = 16.dp)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Rules",
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Normal,
                color = OnSurface
            )

            IconButton(onClick = onAddRuleClick) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add Rule",
                    tint = OnSurface
                )
            }
        }

        // Segmented Switcher (8dp corner radius - STRICT, never pill)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Blocked Button
            val isBlockedSelected = activeTab == RulesTab.BLOCKED
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(BadgeShape)
                    .then(
                        if (isBlockedSelected) {
                            Modifier.background(PrimaryContainer)
                        } else {
                            Modifier.border(1.dp, OutlineVariant, BadgeShape)
                        }
                    )
                    .clickable { activeTab = RulesTab.BLOCKED }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isBlockedSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = OnPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = "Blocked",
                    fontSize = 14.sp,
                    fontWeight = if (isBlockedSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isBlockedSelected) OnPrimaryContainer else OnSurfaceVariant
                )
            }

            // Trusted Button
            val isTrustedSelected = activeTab == RulesTab.TRUSTED
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(BadgeShape)
                    .then(
                        if (isTrustedSelected) {
                            Modifier.background(PrimaryContainer)
                        } else {
                            Modifier.border(1.dp, OutlineVariant, BadgeShape)
                        }
                    )
                    .clickable { activeTab = RulesTab.TRUSTED }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isTrustedSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = OnPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = "Trusted",
                    fontSize = 14.sp,
                    fontWeight = if (isTrustedSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isTrustedSelected) OnPrimaryContainer else OnSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Explanatory Text
        Text(
            text = if (activeTab == RulesTab.BLOCKED) {
                "Blocked links and senders are stopped on this device and reported to your organization."
            } else {
                "Trusted links and senders are never blocked on this device. Your organization can still block them."
            },
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp, start = 2.dp)
        )

        // Rules List
        val currentRules = if (activeTab == RulesTab.BLOCKED) blockedRules else trustedRules

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(currentRules) { index, rule ->
                val icon = getIndicatorIcon(rule.indicatorType)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rule.indicatorValue,
                            style = MonospaceBodyMedium,
                            color = OnSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rule.subtitle,
                                fontSize = 12.sp,
                                color = OnSurfaceVariant
                            )
                            rule.expiresAtText?.let { expires ->
                                Spacer(modifier = Modifier.width(4.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(BadgeShape)
                                        .background(SuspiciousTertiaryContainer.copy(alpha = 0.5f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Timer,
                                        contentDescription = null,
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = expires,
                                        fontSize = 11.sp,
                                        color = OnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = { onDeleteRuleClick(rule) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete Rule",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (index < currentRules.size - 1) {
                    Divider(
                        color = OutlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

private fun getIndicatorIcon(type: IndicatorType): ImageVector {
    return when (type) {
        IndicatorType.URL, IndicatorType.DOMAIN -> Icons.Filled.Link
        IndicatorType.PHONE -> Icons.Filled.Phone
        IndicatorType.EMAIL -> Icons.Filled.Mail
        else -> Icons.Filled.Link
    }
}
