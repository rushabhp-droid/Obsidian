package com.obsidian.mobile.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obsidian.mobile.theme.CardShape
import com.obsidian.mobile.theme.OnSurface
import com.obsidian.mobile.theme.OnSurfaceVariant
import com.obsidian.mobile.theme.Primary
import com.obsidian.mobile.theme.Surface
import com.obsidian.mobile.theme.SurfaceContainerHighest
import com.obsidian.mobile.ui.components.M3FilledButton
import com.obsidian.mobile.ui.components.M3TextButton

@Composable
fun HomeSetupScreen(
    onEnableNotificationClicked: () -> Unit,
    onLearnMoreClicked: () -> Unit,
    onAccountClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                text = "Obsidian",
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Normal,
                color = OnSurface
            )

            IconButton(
                onClick = onAccountClicked,
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

        Spacer(modifier = Modifier.height(4.dp))

        // Status Card: Filled card with 12dp rounded corners
        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighest),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsOff,
                        contentDescription = null,
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Message checking is off",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 24.sp,
                            color = OnSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Obsidian needs notification access to check links in WhatsApp and SMS. For saved contacts, only links are checked. Message text stays on this phone.",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = OnSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    M3FilledButton(
                        onClick = onEnableNotificationClicked
                    ) {
                        Text(
                            text = "Turn on message checking",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    M3TextButton(
                        onClick = onLearnMoreClicked
                    ) {
                        Text(
                            text = "Learn what we access",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent checks section
        Text(
            text = "Recent checks",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp,
            color = OnSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )

        // Empty state
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No messages checked yet.",
                fontSize = 14.sp,
                color = OnSurfaceVariant
            )
        }
    }
}
