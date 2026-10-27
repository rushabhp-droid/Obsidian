package com.obsidian.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.obsidian.mobile.security.PinHasher

/** Six dots showing how many digits are entered (never the digits). */
@Composable
fun PinDots(count: Int, error: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.semantics { contentDescription = "$count of ${PinHasher.PIN_LENGTH} digits entered" },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(PinHasher.PIN_LENGTH) { i ->
            val filled = i < count
            Box(
                Modifier
                    .size(14.dp)
                    .background(if (filled) (if (error) c.error else c.primary) else c.surface, CircleShape)
                    .border(1.5.dp, if (error) c.error else c.outline, CircleShape)
            )
        }
    }
}

/**
 * A 3×4 number pad. [onFingerprint] adds a fingerprint key at the bottom left.
 * Digits are appended by the caller, which owns the entered PIN.
 */
@Composable
fun PinPad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onFingerprint: (() -> Unit)? = null,
) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit -> Key(digit, enabled) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                if (onFingerprint != null) {
                    IconButton(onClick = onFingerprint, Modifier.size(72.dp)) {
                        Icon(Icons.Outlined.Fingerprint, "Use fingerprint", Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Key('0', enabled) { onDigit('0') }
            IconButton(onClick = onDelete, Modifier.size(72.dp), enabled = enabled) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete")
            }
        }
    }
}

@Composable
private fun Key(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(72.dp)) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}
