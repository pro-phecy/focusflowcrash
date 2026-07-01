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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun FocusSessionHistorySidebar(
    viewModel: FocusViewModel,
    isOpen: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.focusSessions.collectAsStateWithLifecycle()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    val isOnline = viewModel.isSupabaseActive()
    
    // Automatically trigger a refresh/sync of stats and sessions from Supabase on open
    LaunchedEffect(isOpen) {
        if (isOpen) {
            viewModel.refreshAllStats()
        }
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(animationSpec = tween(300)) + slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(350, easing = EaseOutQuart)
        ),
        exit = fadeOut(animationSpec = tween(250)) + slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(300, easing = EaseInQuart)
        ),
        modifier = modifier.fillMaxSize()
    ) {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        // If screen is narrow (mobile), take up full width; if wide (tablet), take up 380.dp sidebar
        val sidebarWidth = if (screenWidth > 600.dp) 380.dp else screenWidth

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.CenterEnd
        ) {
            // Sidebar Content Container
            Column(
                modifier = Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Prevent closing when clicking inside sidebar
                    )
                    .background(FFColors.bg)
                    .border(
                        BorderStroke(1.dp, FFColors.borderSubtle),
                        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .testTag("history_log_sidebar")
            ) {
                // Header of Sidebar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FOCUS HISTORY LOG",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = FFColors.textMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Completed Sessions",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = FFColors.textPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Sync Button
                        IconButton(
                            onClick = {
                                viewModel.refreshAllStats()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(FFColors.surface, CircleShape)
                                .testTag("sync_sessions_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync focus sessions with Supabase",
                                tint = if (isOnline && isAuthenticated) FFColors.orange else FFColors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Close Button
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(36.dp)
                                .background(FFColors.surface, CircleShape)
                                .testTag("close_history_sidebar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Sidebar",
                                tint = FFColors.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Divider(color = FFColors.borderSubtle, thickness = 1.dp)

                // Sync Status Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FFColors.surface)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isOnline && isAuthenticated) Icons.Default.CloudQueue else Icons.Default.CloudOff,
                                contentDescription = "Sync status icon",
                                tint = if (isOnline && isAuthenticated) Color(0xFF4CAF50) else FFColors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isOnline && isAuthenticated -> "Supabase Cloud Saved"
                                    isAuthenticated -> "Offline (Pending Cloud Sync)"
                                    else -> "Local Mode (Sign up to sync)"
                                },
                                fontSize = 11.sp,
                                color = if (isOnline && isAuthenticated) Color(0xFF4CAF50) else FFColors.textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (isOnline && isAuthenticated) {
                            Text(
                                text = "ACTIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier
                                    .background(Color(0x1A4CAF50), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Divider(color = FFColors.borderSubtle, thickness = 1.dp)

                // Filter out non-completed ones
                val completedSessions = remember(sessions) {
                    sessions.filter { it.completed }
                }

                if (completedSessions.isEmpty()) {
                    // Empty State
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "No sessions logged",
                            tint = FFColors.textMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No focus sessions completed yet",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = FFColors.textPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Set up a timer, concentrate, and complete your session to start logging stats.",
                            fontSize = 12.sp,
                            color = FFColors.textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                } else {
                    // List of focus sessions
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(completedSessions) { session ->
                            FocusSessionLogItem(session)
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

    val formattedDate = remember(session.startedAt) {
        formatIsoDate(session.startedAt)
    }

    // Get color based on task name using global package-level helpers
    val taskColor = remember(session.goal) {
        getTaskColor(session.goal)
    }
    val taskIcon = remember(session.goal) {
        getTaskIcon(session.goal)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(FFColors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, FFColors.borderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Task Visual Dot/Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(taskColor.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, taskColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = taskIcon,
                    contentDescription = session.goal,
                    tint = taskColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

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
                        fontWeight = FontWeight.Bold,
                        color = FFColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = durationText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = FFColors.orange
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formattedDate,
                        fontSize = 11.sp,
                        color = FFColors.textSecondary
                    )

                    // Tiny cloud synced icon representing Supabase connection status
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Synced to cloud",
                        tint = Color(0xFF4CAF50).copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
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
