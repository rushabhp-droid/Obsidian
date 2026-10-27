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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obsidian.mobile.data.MessageCheckItem
import com.obsidian.mobile.data.MessageCheckStatus
import com.obsidian.mobile.network.ChannelType
import com.obsidian.mobile.theme.ChipShape
import com.obsidian.mobile.theme.MonospaceBodyMedium
import com.obsidian.mobile.theme.OnPrimaryContainer
import com.obsidian.mobile.theme.OnSurface
import com.obsidian.mobile.theme.OnSurfaceVariant
import com.obsidian.mobile.theme.Outline
import com.obsidian.mobile.theme.OutlineVariant
import com.obsidian.mobile.theme.PrimaryContainer
import com.obsidian.mobile.theme.Surface
import com.obsidian.mobile.theme.SurfaceContainerHighest
import com.obsidian.mobile.theme.TextFieldShape
import com.obsidian.mobile.ui.components.getStatusIcon

enum class MessageFilter {
    ALL,
    SCAM,
    SUSPICIOUS,
    CHECKING
}

@Composable
fun MessagesScreen(
    messages: List<MessageCheckItem>,
    onMessageClick: (MessageCheckItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(MessageFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredMessages = messages.filter { item ->
        val matchesFilter = when (selectedFilter) {
            MessageFilter.ALL -> true
            MessageFilter.SCAM -> item.status == MessageCheckStatus.SCAM
            MessageFilter.SUSPICIOUS -> item.status == MessageCheckStatus.SUSPICIOUS
            MessageFilter.CHECKING -> item.status == MessageCheckStatus.CHECKING
        }
        val matchesSearch = if (searchQuery.isBlank()) {
            true
        } else {
            item.sender.contains(searchQuery, ignoreCase = true) ||
                    item.displayDomain.contains(searchQuery, ignoreCase = true) ||
                    item.fullUrl.contains(searchQuery, ignoreCase = true)
        }
        matchesFilter && matchesSearch
    }

    val groupedMessages = filteredMessages.groupBy { it.dateGroup }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Surface)
            .padding(horizontal = 16.dp)
    ) {
        // Top App Bar or Search Bar
        if (isSearchActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search sender or URL...", fontSize = 14.sp) },
                    singleLine = true,
                    shape = TextFieldShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceContainerHighest,
                        unfocusedContainerColor = SurfaceContainerHighest,
                        focusedBorderColor = Outline,
                        unfocusedBorderColor = OutlineVariant
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    isSearchActive = false
                    searchQuery = ""
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close search", tint = OnSurfaceVariant)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Messages",
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Normal,
                    color = OnSurface
                )
                IconButton(onClick = { isSearchActive = true }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = OnSurfaceVariant)
                }
            }
        }

        // Filter Chips Row (8dp corner radius - STRICT, never pill)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            val filters = listOf(
                Pair(MessageFilter.ALL, "All"),
                Pair(MessageFilter.SCAM, "Scam"),
                Pair(MessageFilter.SUSPICIOUS, "Suspicious"),
                Pair(MessageFilter.CHECKING, "Checking")
            )

            items(filters) { (filter, label) ->
                val isSelected = selectedFilter == filter
                Row(
                    modifier = Modifier
                        .clip(ChipShape) // Strict 8dp corner radius
                        .then(
                            if (isSelected) {
                                Modifier.background(PrimaryContainer)
                            } else {
                                Modifier.border(1.dp, OutlineVariant, ChipShape)
                            }
                        )
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = OnPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) OnPrimaryContainer else OnSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Message List Grouped by Date (Today / Yesterday)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            groupedMessages.forEach { (dateGroup, groupItems) ->
                item {
                    Text(
                        text = dateGroup,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurface,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
                    )
                }

                itemsIndexed(groupItems) { index, item ->
                    val (statusIcon, iconTint) = getStatusIcon(item.status)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMessageClick(item) }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Avatar Initial or Channel Badge
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.sender.firstOrNull()?.toString()?.uppercase() ?: "?",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.sender,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (item.channel == ChannelType.WHATSAPP) "· WhatsApp" else "· SMS",
                                    fontSize = 12.sp,
                                    color = OnSurfaceVariant
                                )
                            }
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
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = item.timestamp.split(", ").lastOrNull() ?: item.timestamp,
                                fontSize = 11.sp,
                                color = OnSurfaceVariant
                            )
                        }
                    }

                    if (index < groupItems.size - 1) {
                        Divider(
                            color = OutlineVariant.copy(alpha = 0.5f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
