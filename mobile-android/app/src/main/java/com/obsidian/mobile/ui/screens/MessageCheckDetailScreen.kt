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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obsidian.mobile.data.MessageCheckItem
import com.obsidian.mobile.network.ChannelType
import com.obsidian.mobile.theme.CardShape
import com.obsidian.mobile.theme.MonospaceBodyMedium
import com.obsidian.mobile.theme.OnSurface
import com.obsidian.mobile.theme.OnSurfaceVariant
import com.obsidian.mobile.theme.OutlineVariant
import com.obsidian.mobile.theme.ScamError
import com.obsidian.mobile.theme.ScamErrorContainer
import com.obsidian.mobile.theme.Surface
import com.obsidian.mobile.theme.SurfaceContainerHighest
import com.obsidian.mobile.ui.components.M3FilledButton
import com.obsidian.mobile.ui.components.M3OutlinedButton

@Composable
fun MessageCheckDetailScreen(
    messageItem: MessageCheckItem,
    onBackClick: () -> Unit,
    onReportClick: () -> Unit,
    onTrustClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Surface)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = OnSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Message check",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = OnSurface
                )
            }

            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options",
                    tint = OnSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Threat Banner Card (12dp rounded)
        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = ScamErrorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Dangerous,
                    contentDescription = null,
                    tint = ScamError,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "This link is a known scam",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = ScamError
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "We blocked it before it could open. Don't enter any passwords or one-time codes on this site.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = OnSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Details Section
        Text(
            text = "Details",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = OnSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Detail Rows
        DetailRow(label = "From") {
            Column {
                Text(
                    text = messageItem.sender,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = OnSurface
                )
                Text(
                    text = "${if (messageItem.isSavedContact) "Saved contact" else "Unknown sender"} · ${if (messageItem.channel == ChannelType.WHATSAPP) "WhatsApp" else "SMS"}",
                    fontSize = 13.sp,
                    color = OnSurfaceVariant
                )
            }
        }

        Divider(color = OutlineVariant.copy(alpha = 0.4f), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

        DetailRow(label = "Link") {
            Text(
                text = messageItem.fullUrl,
                style = MonospaceBodyMedium,
                color = OnSurface
            )
        }

        Divider(color = OutlineVariant.copy(alpha = 0.4f), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

        DetailRow(label = "Received") {
            Text(
                text = messageItem.timestamp,
                fontSize = 14.sp,
                color = OnSurface
            )
        }

        Divider(color = OutlineVariant.copy(alpha = 0.4f), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

        DetailRow(label = "Checked by") {
            Text(
                text = messageItem.checkedBy,
                fontSize = 14.sp,
                color = OnSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Privacy Guarantee Card (12dp rounded)
        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighest),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Only the link was checked. Your message text stayed on this phone.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = OnSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            M3FilledButton(
                onClick = onReportClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Report as scam",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            M3OutlinedButton(
                onClick = onTrustClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "I trust this link",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = "Trusting this link allows it on this device.",
                fontSize = 12.sp,
                color = OnSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DetailRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = OnSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}
