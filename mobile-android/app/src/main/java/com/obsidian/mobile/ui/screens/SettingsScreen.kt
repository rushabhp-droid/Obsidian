package com.obsidian.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obsidian.mobile.theme.MonospaceBodyMedium
import com.obsidian.mobile.theme.OnPrimary
import com.obsidian.mobile.theme.OnSurface
import com.obsidian.mobile.theme.OnSurfaceVariant
import com.obsidian.mobile.theme.OutlineVariant
import com.obsidian.mobile.theme.Primary
import com.obsidian.mobile.theme.Surface

@Composable
fun SettingsScreen(
    userEmail: String,
    isNotificationAccessOn: Boolean,
    onNotificationAccessChanged: (Boolean) -> Unit,
    isFingerprintLockOn: Boolean,
    onFingerprintLockChanged: (Boolean) -> Unit,
    isHighContrastBlackOn: Boolean,
    onHighContrastBlackChanged: (Boolean) -> Unit,
    onSignOutClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onLicensesClick: () -> Unit,
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Normal,
                color = OnSurface
            )
        }

        // Section: Protection
        SettingsSectionHeader(title = "Protection")
        SettingsSwitchRow(
            icon = Icons.Filled.Notifications,
            title = "Notification access",
            subtitle = if (isNotificationAccessOn) "On" else "Off",
            checked = isNotificationAccessOn,
            onCheckedChange = onNotificationAccessChanged
        )
        SettingsSwitchRow(
            icon = Icons.Filled.Fingerprint,
            title = "Lock with fingerprint",
            subtitle = "Required to open the app and edit rules",
            checked = isFingerprintLockOn,
            onCheckedChange = onFingerprintLockChanged
        )

        SettingsDivider()

        // Section: Appearance
        SettingsSectionHeader(title = "Appearance")
        SettingsNavigationRow(
            icon = Icons.Filled.DarkMode,
            title = "Theme",
            subtitle = "System default",
            onClick = {}
        )
        SettingsSwitchRow(
            icon = Icons.Filled.Contrast,
            title = "High contrast black",
            subtitle = "Pure black background for OLED screens",
            checked = isHighContrastBlackOn,
            onCheckedChange = onHighContrastBlackChanged
        )

        SettingsDivider()

        // Section: Account
        SettingsSectionHeader(title = "Account")
        SettingsNavigationRow(
            icon = Icons.Filled.AccountCircle,
            title = userEmail,
            subtitle = "Signed in",
            onClick = {}
        )
        SettingsNavigationRow(
            icon = Icons.Filled.Logout,
            title = "Sign out",
            subtitle = null,
            onClick = onSignOutClick
        )

        SettingsDivider()

        // Section: About
        SettingsSectionHeader(title = "About")
        SettingsNavigationRow(
            icon = Icons.Filled.Policy,
            title = "Privacy policy",
            subtitle = null,
            trailingIcon = Icons.Filled.OpenInNew,
            onClick = onPrivacyPolicyClick
        )
        SettingsNavigationRow(
            icon = Icons.Filled.Description,
            title = "Open source licenses",
            subtitle = null,
            onClick = onLicensesClick
        )
        SettingsInfoRow(
            icon = Icons.Filled.Info,
            title = "Version",
            value = "1.0.0"
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        color = Primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsDivider() {
    Divider(
        color = OutlineVariant.copy(alpha = 0.5f),
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OnSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = OnSurface
                )
                subtitle?.let {
                    Text(
                        text = it,
                        fontSize = 14.sp,
                        color = OnSurfaceVariant
                    )
                }
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnPrimary,
                checkedTrackColor = Primary
            )
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OnSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = OnSurface
                )
                subtitle?.let {
                    Text(
                        text = it,
                        fontSize = 14.sp,
                        color = OnSurfaceVariant
                    )
                }
            }
        }

        trailingIcon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = OnSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OnSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = OnSurface
                )
                Text(
                    text = value,
                    style = MonospaceBodyMedium,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}
