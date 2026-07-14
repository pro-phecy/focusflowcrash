package com.example.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInQuart
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.FocusSessionEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FocusSessionHistoryScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.focusSessions.collectAsStateWithLifecycle()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    val isOnline = viewModel.isSupabaseActive()
    
    // Automatically trigger a refresh/sync of stats and sessions from Supabase on load
    LaunchedEffect(Unit) {
        viewModel.refreshAllStats()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FFColors.bg)
    ) {
        AmbientGlowingBackground(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .testTag("history_log_screen"),
                contentAlignment = Alignment.TopCenter
            ) {
                val parentWidth = maxWidth
                val parentHeight = maxHeight

                // Device-safe view dimension percentage ratios based on parent container dimensions rather than fixed pixel values
                val dynamicTopPadding = (parentHeight * 0.025f).coerceIn(10.dp, 24.dp)
                val dynamicSidePadding = (parentWidth * 0.05f).coerceIn(16.dp, 32.dp)

                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                ) {
                    // Header of Standalone Screen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dynamicSidePadding, vertical = dynamicTopPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FOCUS HISTORY LOG",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                color = FFColors.textMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Completed Sessions",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = FFColors.textPrimary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Sync Button
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(GlassmorphismStyle.getGlassBackgroundColor(), CircleShape)
                                    .border(1.dp, GlassmorphismStyle.getGlassBorderBrush(), CircleShape)
                                    .clickable {
                                        viewModel.refreshAllStats()
                                    }
                                    .testTag("sync_sessions_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync focus sessions with Supabase",
                                    tint = if (isOnline && isAuthenticated) FFColors.orange else FFColors.textMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Go Back Button
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(GlassmorphismStyle.getGlassBackgroundColor(), CircleShape)
                                    .border(1.dp, GlassmorphismStyle.getGlassBorderBrush(), CircleShape)
                                    .clickable { onBack() }
                                    .testTag("close_history_screen_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Go Back",
                                    tint = FFColors.textPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                // Premium search, filter, and sort states
                var searchQuery by remember { mutableStateOf("") }
                var selectedCategory by remember { mutableStateOf("All") }
                var sortBy by remember { mutableStateOf(0) } // 0: Newest, 1: Longest, 2: Shortest

                // Filter out non-completed ones
                val completedSessions = remember(sessions) {
                    sessions.filter { it.completed }
                }

                // Dynamic Categories extracted from logged sessions
                val categories = remember(completedSessions) {
                    listOf("All") + completedSessions.map { it.goal }.distinct().sorted()
                }

                // Filtered and sorted sessions
                val filteredSessions = remember(completedSessions, searchQuery, selectedCategory, sortBy) {
                    completedSessions
                        .filter {
                            (selectedCategory == "All" || it.goal.equals(selectedCategory, ignoreCase = true)) &&
                            (it.goal.contains(searchQuery, ignoreCase = true) || formatIsoDate(it.startedAt).contains(searchQuery, ignoreCase = true))
                        }
                        .sortedWith(
                            when (sortBy) {
                                1 -> compareByDescending { it.duration } // Longest First
                                2 -> compareBy { it.duration } // Shortest First
                                else -> compareByDescending { it.startedAt } // Newest First
                            }
                        )
                }

                if (completedSessions.isNotEmpty()) {
                    val totalMinutes = completedSessions.sumOf { it.duration } / 60
                    val totalHours = totalMinutes / 60
                    val remainingMinutes = totalMinutes % 60
                    val totalTimeText = when {
                        totalHours > 0 -> "${totalHours}h ${remainingMinutes}m"
                        else -> "${totalMinutes}m"
                    }

                    val avgSeconds = if (completedSessions.isNotEmpty()) completedSessions.sumOf { it.duration } / completedSessions.size else 0
                    val avgMinutes = avgSeconds / 60
                    val avgTimeText = if (avgMinutes > 0) "${avgMinutes}m" else "${avgSeconds % 60}s"

                    // Ultra-Minimalist Premium Stats Summary Panel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dynamicSidePadding, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "SESSIONS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = FFColors.textMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = completedSessions.size.toString(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = FFColors.textPrimary
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "TOTAL TIME",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = FFColors.textMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = totalTimeText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = FFColors.textPrimary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "AVERAGE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = FFColors.textMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = avgTimeText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = FFColors.textPrimary
                            )
                        }
                    }

                    // Minimalist Search & Sort Panel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dynamicSidePadding, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Search logs...",
                                    fontSize = 11.sp,
                                    color = FFColors.textMuted
                                )
                            },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = FFColors.textPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = FFColors.orange
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(FFColors.surfaceAlt.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = FFColors.textMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { searchQuery = "" }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = FFColors.textMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            } else null
                        )

                        // Sort Selector Button
                        val sortIcon = when (sortBy) {
                            1 -> Icons.Default.ArrowUpward
                            2 -> Icons.Default.ArrowDownward
                            else -> Icons.Default.Sort
                        }
                        val sortLabel = when (sortBy) {
                            1 -> "Longest"
                            2 -> "Shortest"
                            else -> "Newest"
                        }

                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(FFColors.surfaceAlt.copy(alpha = 0.5f))
                                .clickable {
                                    sortBy = (sortBy + 1) % 3
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = sortIcon,
                                    contentDescription = "Sort order",
                                    tint = FFColors.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = sortLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FFColors.textSecondary
                                )
                            }
                        }
                    }

                    // Category Pill Horizontal Scroller
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = dynamicSidePadding)
                    ) {
                        items(categories) { category ->
                            val isSelected = selectedCategory == category
                            val categoryColor = if (category == "All") FFColors.orange else getTaskColor(category)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) categoryColor.copy(alpha = 0.12f)
                                        else Color.Transparent
                                    )
                                    .clickable { selectedCategory = category }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (category != "All") {
                                        Icon(
                                            imageVector = getTaskIcon(category),
                                            contentDescription = null,
                                            tint = if (isSelected) categoryColor else FFColors.textSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = category,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) FFColors.textPrimary else FFColors.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (filteredSessions.isEmpty()) {
                    // Empty State
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(FFColors.borderSubtle, CircleShape)
                                .border(1.dp, FFColors.border, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "No sessions",
                                tint = FFColors.textSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = if (completedSessions.isEmpty()) "No Focus Sessions Yet" else "No Matching Sessions",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = FFColors.textPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (completedSessions.isEmpty()) 
                                "Your completed focus sessions will appear here. Start a timer to log your first session."
                            else 
                                "Try adjusting your search query or category filter to find other logged sessions.",
                            fontSize = 12.sp,
                            color = FFColors.textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = dynamicSidePadding, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        itemsIndexed(filteredSessions) { index, session ->
                            AnimatedEntrance(
                                delayMillis = (index * 45).coerceAtMost(250),
                                durationMillis = 500,
                                direction = EntranceDirection.BOTTOM
                            ) {
                                FocusSessionLogItem(session)
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun FocusSessionLogItem(session: FocusSessionEntity) {
    val durationMin = session.duration / 60
    val durationSec = session.duration % 60
    val durationText = when {
        durationMin > 0 && durationSec > 0 -> "${durationMin}m ${durationSec}s"
        durationMin > 0 -> "${durationMin}m"
        else -> "${durationSec}s"
    }

    val (formattedDate, formattedTime) = remember(session.startedAt) {
        formatElegantDate(session.startedAt)
    }

    val taskColor = remember(session.goal) {
        getTaskColor(session.goal)
    }
    val taskIcon = remember(session.goal) {
        getTaskIcon(session.goal)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(taskColor.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = taskIcon,
                    contentDescription = session.goal,
                    tint = taskColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = session.goal,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FFColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = durationText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = FFColors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formattedDate,
                        fontSize = 11.sp,
                        color = FFColors.textSecondary,
                        fontWeight = FontWeight.Normal
                    )
                    
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = FFColors.textMuted
                    )

                    Text(
                        text = formattedTime,
                        fontSize = 11.sp,
                        color = FFColors.textMuted,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        Divider(
            color = FFColors.borderSubtle.copy(alpha = 0.4f),
            thickness = 0.5.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Simple Helper to Parse ISO UTC format into date and time pair
private fun formatElegantDate(isoStr: String): Pair<String, String> {
    return try {
        val sdfUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = sdfUtc.parse(isoStr) ?: return Pair(isoStr, "")
        val sdfDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        val sdfTime = SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        Pair(sdfDate.format(date), sdfTime.format(date))
    } catch (e: Exception) {
        Pair(isoStr, "")
    }
}

// Simple Helper to Parse ISO UTC format
private fun formatIsoDate(isoStr: String): String {
    return try {
        val sdfUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = sdfUtc.parse(isoStr) ?: return isoStr
        val sdfLocal = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        sdfLocal.format(date)
    } catch (e: Exception) {
        isoStr
    }
}
