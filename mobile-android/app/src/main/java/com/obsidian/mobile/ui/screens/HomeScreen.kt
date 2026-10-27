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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obsidian.mobile.R
import com.obsidian.mobile.data.MessageCheckItem
import com.obsidian.mobile.data.MessageCheckStatus
import com.obsidian.mobile.network.ChannelType
import com.obsidian.mobile.theme.MonospaceBodyMedium
import com.obsidian.mobile.theme.OnSurface
import com.obsidian.mobile.theme.OnSurfaceVariant
import com.obsidian.mobile.theme.Primary
import com.obsidian.mobile.theme.ScamError
import com.obsidian.mobile.theme.ScamErrorContainer
import com.obsidian.mobile.theme.SuspiciousTertiary
import com.obsidian.mobile.theme.SuspiciousTertiaryContainer

@Composable
fun HomeScreen(
    messages: List<MessageCheckItem>,
    isProtectionActive: Boolean,
    onMessageClick: (MessageCheckItem) -> Unit,
    onSeeAllMessagesClick: () -> Unit,
    onCheckLinkClick: () -> Unit,
    onReviewAccessClick: () -> Unit,
    onNotificationBellClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Top App Bar - Figma Pixel-Perfect Match
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8F9FA))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Obsidian Brand Logo (Left)
            Icon(
                painter = painterResource(id = R.drawable.ic_obsidian_logo),
                contentDescription = "Obsidian Logo",
                tint = Color.Unspecified,
                modifier = Modifier.size(28.dp)
            )

            // Centered Title
            Text(
                text = "Your protection",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E293B)
            )

            // Bell Icon with Red Alert Badge (Right)
            Box(contentAlignment = Alignment.TopEnd) {
                IconButton(
                    onClick = onNotificationBellClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Notifications",
                        tint = Color(0xFF334155),
                        modifier = Modifier.size(24.dp)
                    )
                }
                // Red badge dot
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp, end = 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                )
            }
        }

        // Main Scrollable Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                // Card 1: Protection Status Banner
                if (!isProtectionActive) {
                    // Setup Needed Banner (Figma Exact)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFFFDF5))
                            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp))
                            .clickable { onReviewAccessClick() }
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Amber Warning Icon
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFEF3C7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                // "Setup needed" Pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFFEF3C7))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Setup needed",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Finish message protection",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Notification access is off. Manual link checks are ready to use.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B),
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { onReviewAccessClick() }
                                ) {
                                    Text(
                                        text = "Review access",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = Color(0xFF0F172A),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Active Protection Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF0FDF4))
                            .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(16.dp))
                            .clickable { onReviewAccessClick() }
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFDCFCE7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFDCFCE7))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Active protection",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF166534)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Your device is protected",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Messages and browser link interception are actively guarding against phishing.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Section 2: CHECK A LINK Header
                Text(
                    text = "CHECK A LINK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // "Check a suspicious link" Card (Figma Exact)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 0.5.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .clickable { onCheckLinkClick() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Check a suspicious link",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Paste a URL before you open it",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Section 3: RECENT Header (Figma Exact)
                Text(
                    text = "RECENT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Security activity",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    Text(
                        text = "View all",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.clickable { onSeeAllMessagesClick() }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Prototype / Feed Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Live protection feed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Recent List Items (Figma Card List)
            val displayItems = messages.take(5)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 0.5.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        displayItems.forEachIndexed { index, item ->
                            RecentActivityRow(
                                item = item,
                                onClick = { onMessageClick(item) }
                            )

                            if (index < displayItems.size - 1) {
                                Divider(
                                    color = Color(0xFFF1F5F9),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun RecentActivityRow(
    item: MessageCheckItem,
    onClick: () -> Unit
) {
    val isScam = item.status == MessageCheckStatus.SCAM
    val isSuspicious = item.status == MessageCheckStatus.SUSPICIOUS

    val iconBg = when {
        isScam -> Color(0xFFFEE2E2)
        isSuspicious -> Color(0xFFFEF3C7)
        item.channel == ChannelType.EMAIL -> Color(0xFFF1F5F9)
        else -> Color(0xFFEFF6FF)
    }

    val iconTint = when {
        isScam -> Color(0xFFDC2626)
        isSuspicious -> Color(0xFFD97706)
        item.channel == ChannelType.EMAIL -> Color(0xFF64748B)
        else -> Color(0xFF2563EB)
    }

    val channelIcon: ImageVector = when (item.channel) {
        ChannelType.WHATSAPP -> Icons.Outlined.Chat
        ChannelType.SMS -> Icons.Filled.Sms
        ChannelType.EMAIL -> Icons.Filled.Mail
        ChannelType.BROWSER -> Icons.Filled.Language
        else -> Icons.Filled.Link
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel Icon in rounded square
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = channelIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Titles and Details
        Column(modifier = Modifier.weight(1f)) {
            val titleText = when (item.status) {
                MessageCheckStatus.SCAM -> "Malicious link blocked"
                MessageCheckStatus.SUSPICIOUS -> "Link needs your attention"
                MessageCheckStatus.CHECKING -> "Security analysis in progress"
                MessageCheckStatus.NO_THREATS_FOUND -> if (item.channel == ChannelType.EMAIL) "Email analysis complete" else "Link verified safe"
            }

            Text(
                text = titleText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            val channelName = when (item.channel) {
                ChannelType.WHATSAPP -> "WhatsApp"
                ChannelType.SMS -> "SMS"
                ChannelType.EMAIL -> "Gmail"
                ChannelType.BROWSER -> "Browser"
                else -> item.channel.name
            }

            Text(
                text = "$channelName · ${item.timestamp}",
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = item.displayDomain,
                style = MonospaceBodyMedium,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF334155),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Status Badge Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when (item.status) {
                        MessageCheckStatus.SCAM -> Color(0xFFFEE2E2)
                        MessageCheckStatus.SUSPICIOUS -> Color(0xFFFEF3C7)
                        MessageCheckStatus.CHECKING -> Color(0xFFF1F5F9)
                        MessageCheckStatus.NO_THREATS_FOUND -> Color(0xFFF1F5F9)
                    }
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = when (item.status) {
                    MessageCheckStatus.SCAM -> "Scam"
                    MessageCheckStatus.SUSPICIOUS -> "Suspicious"
                    MessageCheckStatus.CHECKING -> "Checking"
                    MessageCheckStatus.NO_THREATS_FOUND -> "No known threats"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = when (item.status) {
                    MessageCheckStatus.SCAM -> Color(0xFF991B1B)
                    MessageCheckStatus.SUSPICIOUS -> Color(0xFF92400E)
                    MessageCheckStatus.CHECKING -> Color(0xFF475569)
                    MessageCheckStatus.NO_THREATS_FOUND -> Color(0xFF475569)
                }
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFCBD5E1),
            modifier = Modifier.size(16.dp)
        )
    }
}
