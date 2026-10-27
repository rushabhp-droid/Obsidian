package com.obsidian.mobile.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Strict Stitch Shape System
// - Buttons, chips, segmented buttons, badges, nav active indicator: 8dp (Never pill)
// - Cards and info rows: 12dp
// - Text fields: 4dp top corners
val ButtonShape = RoundedCornerShape(8.dp)
val ChipShape = RoundedCornerShape(8.dp)
val BadgeShape = RoundedCornerShape(8.dp)
val NavIndicatorShape = RoundedCornerShape(8.dp)
val CardShape = RoundedCornerShape(12.dp)
val TextFieldShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 0.dp, bottomEnd = 0.dp)

val ObsidianShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp)
)
