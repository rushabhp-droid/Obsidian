package com.obsidian.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.obsidian.mobile.theme.ButtonShape
import com.obsidian.mobile.theme.OnPrimary
import com.obsidian.mobile.theme.Outline
import com.obsidian.mobile.theme.Primary

@Composable
fun M3FilledButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = Primary,
        contentColor = OnPrimary
    ),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        shape = ButtonShape, // Strict 8dp corner radius
        colors = colors,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun M3OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = Outline,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        shape = ButtonShape, // Strict 8dp corner radius
        border = BorderStroke(1.dp, borderColor),
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun M3TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        shape = ButtonShape,
        content = content
    )
}
