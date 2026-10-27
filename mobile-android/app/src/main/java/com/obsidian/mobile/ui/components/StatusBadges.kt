package com.obsidian.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obsidian.mobile.data.MessageCheckStatus
import com.obsidian.mobile.theme.BadgeShape
import com.obsidian.mobile.theme.NeutralContainer
import com.obsidian.mobile.theme.NeutralVariant
import com.obsidian.mobile.theme.OnScamErrorContainer
import com.obsidian.mobile.theme.OnSurfaceVariant
import com.obsidian.mobile.theme.OnSuspiciousTertiaryContainer
import com.obsidian.mobile.theme.ScamError
import com.obsidian.mobile.theme.ScamErrorContainer
import com.obsidian.mobile.theme.SurfaceContainerHighest
import com.obsidian.mobile.theme.SuspiciousTertiary
import com.obsidian.mobile.theme.SuspiciousTertiaryContainer

@Composable
fun ThreatStatusBadge(
    status: MessageCheckStatus,
    modifier: Modifier = Modifier
) {
    val (label, bgColor, textColor) = when (status) {
        MessageCheckStatus.SCAM -> Triple(
            "Scam",
            ScamErrorContainer,
            OnScamErrorContainer
        )
        MessageCheckStatus.SUSPICIOUS -> Triple(
            "Suspicious",
            SuspiciousTertiaryContainer,
            OnSuspiciousTertiaryContainer
        )
        MessageCheckStatus.CHECKING -> Triple(
            "Checking",
            SurfaceContainerHighest,
            OnSurfaceVariant
        )
        MessageCheckStatus.NO_THREATS_FOUND -> Triple(
            "No threats found",
            NeutralContainer,
            NeutralVariant
        )
    }

    Box(
        modifier = modifier
            .clip(BadgeShape)
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            lineHeight = 12.sp
        )
    }
}

fun getStatusIcon(status: MessageCheckStatus): Pair<ImageVector, Color> {
    return when (status) {
        MessageCheckStatus.SCAM -> Pair(Icons.Filled.Dangerous, ScamError)
        MessageCheckStatus.SUSPICIOUS -> Pair(Icons.Filled.Warning, SuspiciousTertiary)
        MessageCheckStatus.CHECKING -> Pair(Icons.Filled.Schedule, OnSurfaceVariant)
        MessageCheckStatus.NO_THREATS_FOUND -> Pair(Icons.Filled.CheckCircle, NeutralVariant)
    }
}
