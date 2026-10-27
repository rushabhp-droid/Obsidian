package com.obsidian.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.obsidian.mobile.network.IndicatorType
import com.obsidian.mobile.theme.CardShape
import com.obsidian.mobile.theme.OnSurface
import com.obsidian.mobile.theme.OnSurfaceVariant
import com.obsidian.mobile.theme.Primary
import com.obsidian.mobile.theme.Surface
import com.obsidian.mobile.theme.TextFieldShape
import com.obsidian.mobile.ui.components.M3FilledButton
import com.obsidian.mobile.ui.components.M3TextButton

@Composable
fun AddRuleDialog(
    initialTab: RulesTab,
    onDismiss: () -> Unit,
    onConfirm: (IndicatorType, String, RulesTab, Int?) -> Unit
) {
    var indicatorType by remember { mutableStateOf(IndicatorType.URL) }
    var indicatorValue by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(initialTab) }
    var ttlMinutes by remember { mutableStateOf<Int?>(if (initialTab == RulesTab.TRUSTED) 15 else null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = CardShape, // 12dp rounded
            colors = CardDefaults.cardColors(containerColor = Surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Add Threat Rule",
                    fontSize = 20.sp,
                    color = OnSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Type selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val types = listOf(IndicatorType.URL, IndicatorType.PHONE, IndicatorType.EMAIL)
                    types.forEach { type ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = indicatorType == type,
                                onClick = { indicatorType = type },
                                colors = RadioButtonDefaults.colors(selectedColor = Primary)
                            )
                            Text(
                                text = type.name,
                                fontSize = 13.sp,
                                color = OnSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = indicatorValue,
                    onValueChange = { indicatorValue = it },
                    label = { Text("Value (e.g. evil-domain.com)") },
                    shape = TextFieldShape,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // List Type Selector
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedTab == RulesTab.BLOCKED,
                            onClick = { selectedTab = RulesTab.BLOCKED },
                            colors = RadioButtonDefaults.colors(selectedColor = Primary)
                        )
                        Text("Blocked", fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedTab == RulesTab.TRUSTED,
                            onClick = { selectedTab = RulesTab.TRUSTED },
                            colors = RadioButtonDefaults.colors(selectedColor = Primary)
                        )
                        Text("Trusted", fontSize = 14.sp)
                    }
                }

                if (selectedTab == RulesTab.TRUSTED) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Duration (TTL Bypass)",
                        fontSize = 13.sp,
                        color = OnSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(Pair(15, "15 min"), Pair(60, "1 hr"), Pair(null, "Always")).forEach { (min, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = ttlMinutes == min,
                                    onClick = { ttlMinutes = min },
                                    colors = RadioButtonDefaults.colors(selectedColor = Primary)
                                )
                                Text(label, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    M3TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    M3FilledButton(
                        onClick = {
                            if (indicatorValue.isNotBlank()) {
                                onConfirm(indicatorType, indicatorValue.trim(), selectedTab, ttlMinutes)
                            }
                        },
                        enabled = indicatorValue.isNotBlank()
                    ) {
                        Text("Add Rule")
                    }
                }
            }
        }
    }
}
