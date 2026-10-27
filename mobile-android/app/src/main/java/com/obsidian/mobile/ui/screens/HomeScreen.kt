package com.obsidian.mobile.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obsidian.mobile.data.MessageCheckItem
import com.obsidian.mobile.theme.CardShape
import com.obsidian.mobile.theme.MonospaceBodyMedium
import com.obsidian.mobile.theme.OnSurface
import com.obsidian.mobile.theme.OnSurfaceVariant
import com.obsidian.mobile.theme.OutlineVariant
import com.obsidian.mobile.theme.Primary
import com.obsidian.mobile.theme.Surface
import com.obsidian.mobile.theme.SurfaceContainerHighest
import com.obsidian.mobile.ui.components.M3FilledButton
import com.obsidian.mobile.ui.components.ThreatStatusBadge
import com.obsidian.mobile.ui.components.getStatusIcon

@Composable
fun HomeScreen(
    messages: List<MessageCheckItem>,
    onMessageClick: (MessageCheckItem) -> Unit,
    onSeeAllMessagesClick: () -> Unit,
    onAccountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Surface)
            .padding(horizontal = 16.dp)
    ) {
        // Status Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "9:30",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Wifi, contentDescription = "WiFi", modifier = Modifier.size(17.dp), tint = OnSurface)
                Icon(Icons.Filled.SignalCellularAlt, contentDescription = "Cellular", modifier = Modifier.size(17.dp), tint = OnSurface)
                Icon(Icons.Filled.BatteryFull, contentDescription = "Battery", modifier = Modifier.size(17.dp), tint = OnSurface)
            }
        }

        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Obsidian",
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Normal,
                color = OnSurface
            )

            IconButton(
                onClick = onAccountClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = "Account",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Status Card: Filled card with 12dp rounded corners
            item {
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighest),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Message checking is on",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 24.sp,
                                color = OnSurface
                            )
                            Text(
                                text = "Links in WhatsApp and SMS messages are checked as they arrive.",
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Recent checks header
            item {
                Text(
                    text = "Recent checks",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp,
                    color = OnSurface,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }

            // Up to 5 Recent Checks ListItems
            val displayList = messages.take(5)
            itemsIndexed(displayList) { index, item ->
                val (statusIcon, iconTint) = getStatusIcon(item.status)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMessageClick(item) }
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = item.sender,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 24.sp,
                            color = OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.displayDomain,
                            style = MonospaceBodyMedium,
                            color = OnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.timestamp,
                            fontSize = 11.sp,
                            color = OnSurfaceVariant
                        )
                        ThreatStatusBadge(status = item.status)
                    }
                }

                if (index < displayList.size - 1) {
                    Divider(
                        color = OutlineVariant.copy(alpha = 0.5f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            // See all messages button
            item {
                Spacer(modifier = Modifier.height(24.dp))
                M3FilledButton(
                    onClick = onSeeAllMessagesClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "See all messages",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
