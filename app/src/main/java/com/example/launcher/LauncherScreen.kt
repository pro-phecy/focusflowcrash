package com.example.launcher

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Converters
import com.example.data.ScheduleEntry
import com.example.data.UserProfileEntity
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// --- COLORS ENUMERATION ---
object FFColors {
    var isDark by mutableStateOf(true)

    val bg get() = if (isDark) Color(0xFF09090B) else Color(0xFFFAFAFA)
    val surface get() = if (isDark) Color(0xFF18181B) else Color(0xFFFFFFFF)
    val surfaceAlt get() = if (isDark) Color(0xFF27272A) else Color(0xFFF4F4F5)
    val border get() = if (isDark) Color(0xFF3F3F46) else Color(0xFFE4E4E7)
    val borderSubtle get() = if (isDark) Color(0xFF27272A) else Color(0xFFF4F4F5)
    val textPrimary get() = if (isDark) Color(0xFFFAFAFA) else Color(0xFF09090B)
    val textSecondary get() = if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A)
    val textMuted get() = if (isDark) Color(0xFF71717A) else Color(0xFFA1A1AA)
    val textDisabled get() = if (isDark) Color(0xFF52525B) else Color(0xFFD4D4D8)
    val orange = Color(0xFFF97316)
    val orangeBg get() = if (isDark) Color(0x1FF97316) else Color(0x1AF97316)
    val blue = Color(0xFF3B82F6)
    val green = Color(0xFF22C55E)
    val red = Color(0xFFEF4444)
    val purple = Color(0xFFA855F7)
}

enum class FocusTab {
    HOME, TIMER, PROFILE
}

enum class MainAppScreen {
    LOGIN, ONBOARDING, FOCUS_LAUNCHER, MAIN_SCAFFOLD
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    viewModel: FocusViewModel,
    onToggleStatusBar: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val user by viewModel.user.collectAsStateWithLifecycle()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val timeLeft by viewModel.timeLeft.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(FocusTab.HOME) }

    // Dynamic color reactivity linkage
    FFColors.isDark = user?.darkMode ?: true

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FFColors.bg)
    ) {
        val currentScreen = when {
            !isAuthenticated -> MainAppScreen.LOGIN
            !isOnboardingCompleted -> MainAppScreen.ONBOARDING
            activeSession != null -> MainAppScreen.FOCUS_LAUNCHER
            else -> MainAppScreen.MAIN_SCAFFOLD
        }

        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
            },
            label = "MainAppScreenTransition",
            modifier = Modifier.fillMaxSize()
        ) { screen ->
            when (screen) {
                MainAppScreen.LOGIN -> {
                    LoginScreen(viewModel = viewModel)
                }
                MainAppScreen.ONBOARDING -> {
                    OnboardingScreen(viewModel = viewModel)
                }
                MainAppScreen.FOCUS_LAUNCHER -> {
                    FocusLauncher(
                        duration = activeSession?.duration ?: 0,
                        timeLeft = timeLeft,
                        goal = activeSession?.goal ?: "",
                        allowedAppsJson = activeSession?.allowedAppsJson ?: "[]",
                        isLockRestrictEnabled = user?.privacyMode ?: false,
                        onEnd = { completed ->
                            if (completed) {
                                Toast.makeText(context, "Outstanding! You completed your deep focus!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Session saved as incomplete.", Toast.LENGTH_SHORT).show()
                            }
                            viewModel.endFocusSessionPrematurely()
                        },
                        onToggleStatusBar = onToggleStatusBar
                    )
                }
                MainAppScreen.MAIN_SCAFFOLD -> {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = FFColors.bg,
                        bottomBar = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(FFColors.bg)
                            ) {
                                Divider(color = FFColors.borderSubtle, thickness = 1.dp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .windowInsetsPadding(WindowInsets.navigationBars)
                                        .height(76.dp)
                                        .background(FFColors.bg),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BottomTabItem(
                                        label = "Home",
                                        icon = Icons.Default.Home,
                                        iconSelected = Icons.Default.Home,
                                        isSelected = currentTab == FocusTab.HOME,
                                        onClick = { currentTab = FocusTab.HOME }
                                    )
                                    BottomTabItem(
                                        label = "Timer",
                                        icon = Icons.Default.HourglassEmpty,
                                        iconSelected = Icons.Default.HourglassFull,
                                        isSelected = currentTab == FocusTab.TIMER,
                                        onClick = { currentTab = FocusTab.TIMER }
                                    )
                                    BottomTabItem(
                                        label = "Profile",
                                        icon = Icons.Default.Person,
                                        iconSelected = Icons.Default.Person,
                                        isSelected = currentTab == FocusTab.PROFILE,
                                        onClick = { currentTab = FocusTab.PROFILE }
                                    )
                                }
                            }
                        }
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(paddingValues)
                        ) {
                            when (currentTab) {
                                FocusTab.HOME -> HomeScreen(viewModel = viewModel, onStartFocusClick = { currentTab = FocusTab.TIMER })
                                FocusTab.TIMER -> TimerScreen(viewModel = viewModel)
                                FocusTab.PROFILE -> ProfileScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }

        // Fullscreen Loading HUD Overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA09090B))
                    .clickable(
                        onClick = {},
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = FFColors.orange)
            }
        }
    }
}

// --- SUB-COMPONENT: BOTTOM BAR NAVIGATION TAB ---
@Composable
fun BottomTabItem(
    label: String,
    icon: ImageVector,
    iconSelected: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(80.dp)
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .testTag("tab_item_${label.lowercase()}")
    ) {
        Icon(
            imageVector = if (isSelected) iconSelected else icon,
            contentDescription = label,
            tint = if (isSelected) FFColors.textPrimary else FFColors.textDisabled,
            modifier = Modifier.size(28.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = if (isSelected) FFColors.textPrimary else FFColors.textDisabled
        )
    }
}


// ==========================================
// 1. LOGIN & SIGN UP FORMS
// ==========================================
@Composable
fun LoginScreen(viewModel: FocusViewModel) {
    var isSignUpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val authError by viewModel.authError.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FFColors.bg)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Spark Logo Accent
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFFFFECD1), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.OfflineBolt,
                    contentDescription = "Logo",
                    tint = FFColors.orange,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = if (isSignUpMode) "Begin your Flow" else "Welcome to FocusFlow",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = FFColors.textPrimary,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = if (isSignUpMode) "Create an account for cloud synced statistics" else "Deep work and scheduled concentration management",
                fontSize = 15.sp,
                color = FFColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, bottom = 36.dp)
            )

            // Dynamic Tab switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(FFColors.surface, RoundedCornerShape(16.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (!isSignUpMode) FFColors.surfaceAlt else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { isSignUpMode = false },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Sign In", color = FFColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (isSignUpMode) FFColors.surfaceAlt else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { isSignUpMode = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Sign Up", color = FFColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Form Fields
            if (isSignUpMode) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("display_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FFColors.orange,
                        unfocusedBorderColor = FFColors.border,
                        focusedLabelColor = FFColors.orange,
                        focusedTextColor = FFColors.textPrimary,
                        unfocusedTextColor = FFColors.textPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("email_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FFColors.orange,
                    unfocusedBorderColor = FFColors.border,
                    focusedLabelColor = FFColors.orange,
                    focusedTextColor = FFColors.textPrimary,
                    unfocusedTextColor = FFColors.textPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FFColors.orange,
                    unfocusedBorderColor = FFColors.border,
                    focusedLabelColor = FFColors.orange,
                    focusedTextColor = FFColors.textPrimary,
                    unfocusedTextColor = FFColors.textPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordTransformationM3(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password",
                            tint = FFColors.textSecondary
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            // Auth error string
            if (authError != null) {
                Text(
                    text = authError!!,
                    color = FFColors.red,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Large White Tall Button
            Button(
                onClick = {
                    if (isSignUpMode) {
                        viewModel.register(email, password, displayName)
                    } else {
                        viewModel.login(email, password)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("auth_submit_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FFColors.textPrimary,
                    contentColor = if (FFColors.isDark) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (isSignUpMode) "Create Account" else "Sign In",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

// Simple legacy helper for hiding passwords
class PasswordTransformationM3 : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val hidden = "*".repeat(text.text.length)
        return TransformedText(
            androidx.compose.ui.text.AnnotatedString(hidden),
            androidx.compose.ui.text.input.OffsetMapping.Identity
        )
    }
}


// ==========================================
// 2. ONBOARDING SCREEN FLOW
// ==========================================
@Composable
fun OnboardingScreen(viewModel: FocusViewModel) {
    var step by remember { mutableStateOf(1) }
    
    // Step States
    var dailyGoalMinutes by remember { mutableStateOf(120) }
    val defaultEss = listOf("com.google.android.apps.messaging", "com.android.dialer", "com.google.android.gm", "com.google.android.calendar")
    val selectedApps = remember { mutableStateListOf<String>().apply { addAll(defaultEss) } }
    val installedApps by viewModel.installedPackages.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FFColors.bg)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
        ) {
            // Top Progress Tracker Indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (i in 1..4) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(
                                color = if (i <= step) FFColors.textPrimary else FFColors.surfaceAlt,
                                shape = RoundedCornerShape(999.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(44.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(400))).togetherWith(
                        scaleOut(
                            targetScale = 0.92f,
                            animationSpec = tween(300)
                        ) + fadeOut(animationSpec = tween(300))
                    )
                },
                label = "OnboardingStepTransition",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { currentStep ->
                when (currentStep) {
                    1 -> OnboardingWelcome()
                    2 -> OnboardingDailyGoal(
                        minutes = dailyGoalMinutes,
                        onMinutesChange = { dailyGoalMinutes = it }
                    )
                    3 -> OnboardingAppEssentials(
                        installedApps = installedApps,
                        selected = selectedApps,
                        onToggle = { pkg ->
                            if (selectedApps.contains(pkg)) {
                                selectedApps.remove(pkg)
                            } else {
                                selectedApps.add(pkg)
                            }
                        }
                    )
                    4 -> OnboardingDone(
                        onFinish = {
                            viewModel.completeOnboarding(dailyGoalMinutes, selectedApps.toList())
                        }
                    )
                }
            }

            // Buttons
            if (step < 4) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { if (step > 1) step -= 1 },
                        enabled = step > 1
                    ) {
                        Text(
                            text = "Back",
                            color = if (step > 1) FFColors.textSecondary else Color.Transparent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = { step += 1 },
                        modifier = Modifier
                            .width(160.dp)
                            .height(56.dp)
                            .testTag("onboarding_next_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FFColors.textPrimary,
                            contentColor = if (FFColors.isDark) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Next", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingWelcome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color(0xFFFFF7ED), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AllInclusive,
                contentDescription = "Welcome",
                tint = FFColors.orange,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = "Welcome to FocusFlow",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = FFColors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Take back your time. Create mindful boundaries, scheduler structures, and restrict non-essential apps for optimal mental focus.",
            fontSize = 15.sp,
            color = FFColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun OnboardingDailyGoal(minutes: Int, onMinutesChange: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Set Your Daily Goal",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = FFColors.textPrimary
        )

        Text(
            text = "Select your target hours of hyper-focused work per day",
            fontSize = 13.sp,
            color = FFColors.textSecondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            textAlign = TextAlign.Center
        )

        // Custom hours display
        val h = minutes / 60
        val m = minutes % 60
        val displayStr = if (h > 0) "${h}h ${m}m" else "${m}m"

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { if (minutes > 15) onMinutesChange(minutes - 15) },
                modifier = Modifier
                    .size(56.dp)
                    .background(FFColors.surface, CircleShape)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Sub", tint = FFColors.textPrimary)
            }

            Text(
                text = displayStr,
                fontSize = 48.sp,
                fontWeight = FontWeight.Light,
                color = FFColors.textPrimary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            IconButton(
                onClick = { if (minutes < 960) onMinutesChange(minutes + 15) },
                modifier = Modifier
                    .size(56.dp)
                    .background(FFColors.surface, CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = FFColors.textPrimary)
            }
        }

        Spacer(modifier = Modifier.height(44.dp))

        // Chip presets: 60, 120, 240, 480
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val presets = listOf(60, 120, 240, 480)
            presets.forEach { preset ->
                val label = "${preset / 60}h"
                val act = preset == minutes
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(
                            color = if (act) FFColors.textPrimary else FFColors.surface,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (act) FFColors.textPrimary else FFColors.borderSubtle,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onMinutesChange(preset) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (act) Color.Black else FFColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingAppEssentials(
    installedApps: List<AppInfo>,
    selected: List<String>,
    onToggle: (String) -> Unit
) {
    var searchFilter by remember { mutableStateOf("") }
    
    val appsToDisplay = remember(installedApps, searchFilter) {
        val baseList = if (installedApps.isNotEmpty()) {
            installedApps.map { AppDef(id = "", name = it.label, iconName = "", androidPackage = it.packageName) }
        } else {
            AppLibrary.ALL_APPS
        }
        
        if (searchFilter.isBlank()) {
            baseList
        } else {
            baseList.filter { it.name.contains(searchFilter, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "App Essentials",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = FFColors.textPrimary
        )

        Text(
            text = "Select default companion apps that are always allowed to skip focus block restrictions.",
            fontSize = 13.sp,
            color = FFColors.textSecondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )

        // Search text bar
        OutlinedTextField(
            value = searchFilter,
            onValueChange = { searchFilter = it },
            placeholder = { Text("Search apps...", color = FFColors.textDisabled) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FFColors.orange,
                unfocusedBorderColor = FFColors.borderSubtle,
                focusedTextColor = FFColors.textPrimary,
                unfocusedTextColor = FFColors.textPrimary
            ),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Grid of real or mock apps
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(appsToDisplay) { app ->
                val isSel = selected.contains(app.androidPackage)
                Box(
                    modifier = Modifier
                        .height(84.dp)
                        .background(
                            color = if (isSel) {
                                if (FFColors.isDark) Color(0xFF1E1B18) else Color(0xFFFFF7ED)
                            } else FFColors.surface,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            1.dp,
                            if (isSel) FFColors.orange else FFColors.borderSubtle,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onToggle(app.androidPackage) }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    if (isSel) FFColors.orange else FFColors.surfaceAlt,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (installedApps.isNotEmpty()) {
                                AppIconImage(
                                    packageName = app.androidPackage,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = AppLibrary.getIcon(app.iconName),
                                    contentDescription = app.name,
                                    tint = if (isSel) Color.White else FFColors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = app.name,
                            fontSize = 10.sp,
                            color = if (isSel) FFColors.textPrimary else FFColors.textSecondary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isSel) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(12.dp)
                                .background(FFColors.orange, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Checked",
                                tint = Color.White,
                                modifier = Modifier.size(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingDone(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color(0xFFDCFCE7), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                tint = FFColors.green,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = "You're All Set!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = FFColors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your personalized focus engine is fully initialized. Tap below to access the home workspace.",
            fontSize = 15.sp,
            color = FFColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("get_started_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = FFColors.textPrimary,
                contentColor = if (FFColors.isDark) Color.Black else Color.White
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}


// ==========================================
// 3. HOME SCREEN TAB WORKSPACE
// ==========================================
@Composable
fun HomeScreen(viewModel: FocusViewModel, onStartFocusClick: () -> Unit) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedPackages.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAppDrawer by remember { mutableStateOf(false) }

    val favoritesList = remember(user?.preferredAppsJson) {
        user?.preferredAppsJson?.let { Converters().toStringList(it) } ?: emptyList()
    }

    val favoriteApps = remember(favoritesList, installedApps) {
        installedApps.filter { favoritesList.contains(it.packageName) }
    }

    // Pull to Refresh state trigger
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Query stats periodically when tab launches
    LaunchedEffect(Unit) {
        viewModel.refreshAllStats()
    }

    // Header Time calculations
    var timeString by remember { mutableStateOf("") }
    var dateString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val formatTime = SimpleDateFormat("h:mm", Locale.getDefault())
            val formatDate = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
            timeString = formatTime.format(Date())
            dateString = formatDate.format(Date()).uppercase()
            delay(1000)
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Decimals of screen dimensions for responsive sizing
    val responsiveHSpacing = (screenWidth.value * 0.06f).coerceIn(16f, 32f).dp
    val responsiveVSpacing = (screenHeight.value * 0.03f).coerceIn(16f, 32f).dp
    val smallSpacer = (screenHeight.value * 0.012f).coerceIn(6f, 16f).dp
    val mediumSpacer = (screenHeight.value * 0.022f).coerceIn(12f, 24f).dp
    val largeSpacer = (screenHeight.value * 0.038f).coerceIn(24f, 48f).dp

    // Dynamic text scale factors based on screen width - beautifully tuned to be elegant, readable, and cohesive
    val baseScale = (screenWidth.value / 360f).coerceIn(0.85f, 1.25f)
    val dateFontSize = (11 * baseScale).sp
    val timeFontSize = (38 * baseScale).sp
    val greetingFontSize = (22 * baseScale).sp
    val sectionHeaderFontSize = (12 * baseScale).sp
    val bodyFontSize = (13 * baseScale).sp
    val buttonFontSize = (15 * baseScale).sp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FFColors.bg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = responsiveHSpacing, vertical = responsiveVSpacing)
        ) {
            
            // Live Block
            Text(
                text = dateString,
                fontSize = dateFontSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = FFColors.textMuted
            )

            Spacer(modifier = Modifier.height(smallSpacer))

            Text(
                text = timeString,
                fontSize = timeFontSize,
                fontWeight = FontWeight.Light,
                color = FFColors.textPrimary,
                modifier = Modifier.padding(vertical = smallSpacer)
            )

            // Greeting Block
            val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                in 0..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                else -> "Good evening"
            }
            val name = user?.displayName ?: "Explorer"

            Column(
                modifier = Modifier.padding(vertical = mediumSpacer)
            ) {
                Text(
                    text = "$greeting,",
                    fontSize = greetingFontSize,
                    fontWeight = FontWeight.Normal,
                    color = FFColors.textSecondary,
                    lineHeight = (greetingFontSize.value * 1.3f).sp
                )
                Spacer(modifier = Modifier.height((smallSpacer.value * 0.8f).coerceIn(4f, 10f).dp))
                Text(
                    text = name,
                    fontSize = greetingFontSize * 1.1f,
                    fontWeight = FontWeight.Bold,
                    color = FFColors.textPrimary,
                    lineHeight = (greetingFontSize.value * 1.35f).sp
                )
            }

            // PINNED FAVORITES ROW (SYSTEM HOME LAUNCHER)
            if (favoriteApps.isNotEmpty()) {
                Text(
                    text = "FAVORITE APPS",
                    fontSize = sectionHeaderFontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = FFColors.textMuted,
                    modifier = Modifier.padding(top = smallSpacer, bottom = smallSpacer)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = mediumSpacer),
                    verticalArrangement = Arrangement.spacedBy(smallSpacer)
                ) {
                    val rows = favoriteApps.chunked(4)
                    rows.forEach { rowApps ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(smallSpacer)
                        ) {
                            rowApps.forEach { app ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            try {
                                                app.launch(context)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Failed to launch ${app.label}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(smallSpacer / 2)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size((56 * baseScale).coerceIn(48f, 72f).dp)
                                            .background(FFColors.surface, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AppIconImage(
                                            packageName = app.packageName, 
                                            modifier = Modifier.size((32 * baseScale).coerceIn(24f, 48f).dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(smallSpacer / 2))
                                    Text(
                                        text = app.label,
                                        fontSize = (13 * baseScale).sp,
                                        color = FFColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            // Add filler spaces to keep layout items equal width
                            val fillers = 4 - rowApps.size
                            if (fillers > 0) {
                                repeat(fillers) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                // If favorites are empty, show a small minimalist prompt option
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = mediumSpacer)
                        .background(FFColors.surface, RoundedCornerShape(20.dp))
                        .border(1.dp, FFColors.borderSubtle, RoundedCornerShape(20.dp))
                        .clickable { showAppDrawer = true }
                        .padding(horizontal = responsiveHSpacing, vertical = (responsiveVSpacing.value * 0.8f).coerceIn(12f, 24f).dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.StarBorder,
                            contentDescription = "Pin Favorite",
                            tint = FFColors.orange,
                            modifier = Modifier.size((24 * baseScale).dp)
                        )
                        Spacer(modifier = Modifier.width(smallSpacer))
                        Text(
                            text = "Tap to pin favorite apps to launcher desktop",
                            color = FFColors.textSecondary,
                            fontSize = bodyFontSize,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Today's Focus Card Progress Block
            // Calculate goal total
            val dailyGoalMinutes = user?.dailyGoal ?: 120
            val focusedMinutesToday = stats.daily.firstOrNull { 
                val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                formatter.format(Date()) == it.dayDate
            }?.minutes ?: 0

            val progressVal = if (dailyGoalMinutes > 0) focusedMinutesToday.toFloat() / dailyGoalMinutes.toFloat() else 0f
            val displayPct = (progressVal * 100).toInt().coerceIn(0, 100)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FFColors.surface, RoundedCornerShape(32.dp))
                    .border(1.dp, FFColors.borderSubtle, RoundedCornerShape(32.dp))
                    .padding(horizontal = responsiveHSpacing, vertical = (responsiveVSpacing.value * 1.1f).coerceIn(20f, 36f).dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TODAY'S FOCUS PROGRESS",
                            fontSize = sectionHeaderFontSize,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = FFColors.textMuted,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "$displayPct%",
                            fontSize = (18 * baseScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = FFColors.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(smallSpacer))

                    // Custom Hours Formulations
                    val todayH = focusedMinutesToday / 60
                    val todayM = focusedMinutesToday % 60
                    val goalH = dailyGoalMinutes / 60
                    val goalM = dailyGoalMinutes % 60

                    val todayDisplay = if (todayH > 0) "${todayH}h ${todayM}m" else "${todayM}m"
                    val goalDisplay = if (goalH > 0) "${goalH}h ${goalM}m" else "${goalM}m"

                    Text(
                        text = "$todayDisplay / $goalDisplay",
                        fontSize = (18 * baseScale).sp,
                        fontWeight = FontWeight.Medium,
                        color = FFColors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(mediumSpacer))

                    // Thin progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((10 * baseScale).dp)
                            .background(if (FFColors.isDark) FFColors.surfaceAlt else FFColors.border, RoundedCornerShape((10 * baseScale).dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressVal.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(FFColors.orange, RoundedCornerShape((10 * baseScale).dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(mediumSpacer))

            // Large white button to start focus mode
            Button(
                onClick = onStartFocusClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height((68 * baseScale).coerceIn(56f, 84f).dp)
                    .testTag("start_session_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FFColors.textPrimary,
                    contentColor = if (FFColors.isDark) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        tint = if (FFColors.isDark) Color.Black else Color.White,
                        modifier = Modifier.size((24 * baseScale).dp)
                    )
                    Spacer(modifier = Modifier.width(smallSpacer))
                    Text(
                        text = "Start Focus Session",
                        fontWeight = FontWeight.Bold,
                        fontSize = buttonFontSize,
                        color = if (FFColors.isDark) Color.Black else Color.White
                    )
                }
            }

        Spacer(modifier = Modifier.height(largeSpacer))

        // Activity Section Header and Streak Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONCENTRATION HISTORIC",
                fontSize = sectionHeaderFontSize,
                color = FFColors.textSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Streak Pill
            Box(
                modifier = Modifier
                    .background(Color(0xFF321E14), RoundedCornerShape(999.dp)) // subtle dark orange
                    .padding(horizontal = (16 * baseScale).dp, vertical = (8 * baseScale).dp)
            ) {
                Text(
                    text = "🔥 ${stats.streak} DAY STREAK",
                    color = FFColors.orange,
                    fontWeight = FontWeight.Bold,
                    fontSize = (12 * baseScale).sp,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(smallSpacer))

        // Custom Bar Chart: Draws Mon-Sun bars using rows with rounded tops
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((200 * baseScale).coerceIn(160f, 260f).dp)
                .background(FFColors.surface, RoundedCornerShape(28.dp))
                .border(1.dp, FFColors.borderSubtle, RoundedCornerShape(28.dp))
                .padding(horizontal = responsiveHSpacing, vertical = (responsiveVSpacing.value * 0.8f).coerceIn(12f, 24f).dp)
        ) {
            if (stats.daily.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No local history found.", color = FFColors.textDisabled, fontSize = (15 * baseScale).sp)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Maximum height ratio
                    val maxMinutes = stats.daily.maxOfOrNull { it.minutes }?.coerceAtLeast(60) ?: 60

                    stats.daily.forEachIndexed { idx, bar ->
                        val isToday = idx == stats.daily.size - 1
                        val ratio = bar.minutes.toFloat() / maxMinutes.toFloat()
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (bar.minutes > 0) {
                                Text(
                                    text = "${bar.minutes}m",
                                    fontSize = (11 * baseScale).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isToday) FFColors.textPrimary else FFColors.textSecondary,
                                    modifier = Modifier.padding(bottom = (6 * baseScale).dp)
                                )
                            } else {
                                Box(modifier = Modifier.height((18 * baseScale).dp))
                            }

                            // Render Bar Column
                            Box(
                                modifier = Modifier
                                    .width((24 * baseScale).dp)
                                    .fillMaxHeight(ratio.coerceIn(0.05f, 0.75f))
                                    .background(
                                        color = if (isToday) FFColors.orange else (if (FFColors.isDark) FFColors.surfaceAlt else FFColors.border),
                                        shape = RoundedCornerShape(topStart = (8 * baseScale).dp, topEnd = (8 * baseScale).dp)
                                    )
                             )

                            Spacer(modifier = Modifier.height((10 * baseScale).dp))

                            Text(
                                text = bar.dayLabel,
                                fontSize = (12 * baseScale).sp,
                                color = if (isToday) FFColors.textPrimary else FFColors.textDisabled,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(largeSpacer))

        // 3-stat Grid Row (Equal Width and Height Cards)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy((10 * baseScale).dp)
        ) {
            val avg = if (stats.sessionCount > 0) stats.weekMinutes / stats.sessionCount else 0
            
            StatCard(label = "THIS WEEK", value = "${stats.weekMinutes}m", modifier = Modifier.weight(1f).fillMaxHeight())
            StatCard(label = "AVG SESSION", value = "${avg}m", modifier = Modifier.weight(1f).fillMaxHeight())
            StatCard(label = "SESSIONS", value = "${stats.sessionCount}", modifier = Modifier.weight(1f).fillMaxHeight())
        }

        Spacer(modifier = Modifier.height(largeSpacer))

        // Open App Drawer Button
        Button(
            onClick = { showAppDrawer = true },
            modifier = Modifier
                .fillMaxWidth()
                .height((68 * baseScale).coerceIn(56f, 84f).dp)
                .testTag("open_app_drawer_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = FFColors.surface,
                contentColor = FFColors.textPrimary
            ),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, FFColors.borderSubtle)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Drawer",
                    tint = FFColors.textSecondary,
                    modifier = Modifier.size((24 * baseScale).dp)
                )
                Spacer(modifier = Modifier.width(smallSpacer))
                Text("All Apps & Tools Drawer", fontWeight = FontWeight.SemiBold, fontSize = (16 * baseScale).sp)
            }
        }

        Spacer(modifier = Modifier.height(largeSpacer * 2.5f)) // Avoid overlaps
    }

    // --- APP DRAWER OVERLAY ---
    if (showAppDrawer) {
        Dialog(
            onDismissRequest = { showAppDrawer = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FFColors.bg) // true black background for extreme minimalism
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(24.dp)
                ) {
                    // Title / Search Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "App Drawer",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = FFColors.textPrimary
                        )
                        IconButton(onClick = { showAppDrawer = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Drawer", tint = FFColors.textPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    var drawerSearchFilter by remember { mutableStateOf("") }

                    OutlinedTextField(
                        value = drawerSearchFilter,
                        onValueChange = { drawerSearchFilter = it },
                        placeholder = { Text("Search phone apps...", color = FFColors.textDisabled) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FFColors.orange,
                            unfocusedBorderColor = FFColors.borderSubtle,
                            focusedTextColor = FFColors.textPrimary,
                            unfocusedTextColor = FFColors.textPrimary
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val filteredDrawerApps = remember(drawerSearchFilter, installedApps) {
                        if (drawerSearchFilter.isBlank()) {
                            installedApps
                        } else {
                            installedApps.filter { it.label.contains(drawerSearchFilter, ignoreCase = true) }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        if (filteredDrawerApps.isEmpty()) {
                            item { 
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    Text("No launcher apps found.", color = FFColors.textDisabled, fontSize = 14.sp)
                                }
                            }
                        } else {
                            items(filteredDrawerApps) { app ->
                                val isFav = favoritesList.contains(app.packageName)
                                Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                try {
                                                    app.launch(context)
                                                    showAppDrawer = false
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error launching app.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .padding(vertical = 10.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .background(FFColors.surface, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AppIconImage(packageName = app.packageName, modifier = Modifier.size(28.dp))
                                            }

                                            Spacer(modifier = Modifier.width(16.dp))

                                            Column {
                                                Text(
                                                    text = app.label,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = FFColors.textPrimary
                                                )
                                                Text(
                                                    text = app.packageName,
                                                    fontSize = 11.sp,
                                                    color = FFColors.textMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.togglePreferredApp(app.packageName)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "Pin Favorite",
                                                tint = if (isFav) FFColors.orange else FFColors.textDisabled
                                            )
                                        }
                                    }
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
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val baseScale = (screenWidth.value / 360f).coerceIn(0.85f, 1.4f)
    val horizontalPadding = (14 * baseScale).coerceIn(6f, 20f).dp
    val verticalPadding = (14 * baseScale).coerceIn(10f, 24f).dp

    Box(
        modifier = modifier
            .background(FFColors.surface, RoundedCornerShape(24.dp))
            .border(1.dp, FFColors.borderSubtle, RoundedCornerShape(24.dp))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = (10 * baseScale).sp,
                color = FFColors.textMuted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = (12 * baseScale).sp
            )
            Spacer(modifier = Modifier.height((6 * baseScale).dp))
            Text(
                text = value,
                fontSize = (20 * baseScale).sp,
                color = FFColors.textPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}


// ==========================================
// 4. TIMER SCREEN WORKSPACE
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(viewModel: FocusViewModel) {
    val context = LocalContext.current
    var intentionText by remember { mutableStateOf("") }
    var durationHrs by remember { mutableStateOf(0) }
    var durationMins by remember { mutableStateOf(25) }
    var durationSecs by remember { mutableStateOf(0) }

    val user by viewModel.user.collectAsStateWithLifecycle()
    val selectedAppsSet by viewModel.selectedSessionApps.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedPackages.collectAsStateWithLifecycle()

    var showAppPickerModal by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    val baseScale = (screenWidth.value / 360f).coerceIn(0.85f, 1.4f)
    val responsiveHSpacing = (screenWidth.value * 0.06f).coerceIn(16f, 32f).dp
    val responsiveVSpacing = (screenHeight.value * 0.03f).coerceIn(16f, 32f).dp
    val smallSpacer = (screenHeight.value * 0.012f).coerceIn(6f, 16f).dp
    val mediumSpacer = (screenHeight.value * 0.022f).coerceIn(12f, 24f).dp
    val largeSpacer = (screenHeight.value * 0.038f).coerceIn(24f, 48f).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FFColors.bg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = responsiveHSpacing, vertical = responsiveVSpacing)
        ) {
            Text(
                text = "Focus Period",
                fontSize = (28 * baseScale).sp,
                fontWeight = FontWeight.Bold,
                color = FFColors.textPrimary
            )
            Text(
                text = "Define your space for deep work.",
                fontSize = (13 * baseScale).sp,
                color = FFColors.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = responsiveVSpacing)
            )

        // Card 1: Hour/Min/Sec Column Selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FFColors.surface),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, FFColors.borderSubtle)
        ) {
            Column(modifier = Modifier.padding(responsiveHSpacing), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "DURATION LIMIT",
                    fontSize = (10 * baseScale).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = FFColors.textMuted,
                    modifier = Modifier.padding(bottom = smallSpacer)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hours column
                    DurationPickerColumn(
                        value = durationHrs,
                        onIncrement = { if (durationHrs < 23) durationHrs += 1 },
                        onDecrement = { if (durationHrs > 0) durationHrs -= 1 }
                    )

                    Text(":", fontSize = (36 * baseScale).sp, buildStyle = TextStyle(color = FFColors.textSecondary), modifier = Modifier.padding(horizontal = (8 * baseScale).dp))

                    // Minutes column
                    DurationPickerColumn(
                        value = durationMins,
                        onIncrement = { if (durationMins < 59) durationMins += 5 else durationMins = 0 },
                        onDecrement = { if (durationMins >= 5) durationMins -= 5 else durationMins = 55 }
                    )

                    Text(":", fontSize = (36 * baseScale).sp, buildStyle = TextStyle(color = FFColors.textSecondary), modifier = Modifier.padding(horizontal = (8 * baseScale).dp))

                    // Seconds column
                    DurationPickerColumn(
                        value = durationSecs,
                        onIncrement = { if (durationSecs < 50) durationSecs += 10 else durationSecs = 0 },
                        onDecrement = { if (durationSecs >= 10) durationSecs -= 10 else durationSecs = 50 }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(smallSpacer))

        // Card 2: Interoperable Intention Text Field
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FFColors.surface),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, FFColors.borderSubtle)
        ) {
            Column(modifier = Modifier.padding(responsiveHSpacing)) {
                Text(
                    text = "CONCENTRATION OBJECTIVE",
                    fontSize = (10 * baseScale).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = FFColors.textMuted,
                    modifier = Modifier.padding(bottom = smallSpacer)
                )

                BasicTextField(
                    value = intentionText,
                    onValueChange = { intentionText = it },
                    textStyle = TextStyle(
                        color = FFColors.textPrimary,
                        fontSize = (20 * baseScale).sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(FFColors.orange),
                    decorationBox = { innerTextField ->
                        if (intentionText.isEmpty()) {
                            Text(
                                text = "What will you achieve?",
                                color = FFColors.textDisabled,
                                fontSize = (20 * baseScale).sp
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("intention_input")
                )
            }
        }

        Spacer(modifier = Modifier.height(smallSpacer))

        // Card 3: Essentials (Allowed Apps grid snapshot up to 7 items + "+N" overflow)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FFColors.surface),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, FFColors.borderSubtle)
        ) {
            Column(modifier = Modifier.padding(responsiveHSpacing)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COMPANION ALLOWLIST",
                        fontSize = (10 * baseScale).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp,
                        color = FFColors.textMuted,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = "Manage",
                        fontSize = (11 * baseScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = FFColors.orange,
                        modifier = Modifier.clickable { showAppPickerModal = true }
                    )
                }

                Spacer(modifier = Modifier.height(smallSpacer))

                val matchingAppList = remember(installedApps, selectedAppsSet) {
                    if (installedApps.isNotEmpty()) {
                        installedApps.filter { selectedAppsSet.contains(it.packageName) }.map {
                            AppDef(id = "", name = it.label, iconName = "", androidPackage = it.packageName)
                        }
                    } else {
                        AppLibrary.ALL_APPS.filter { selectedAppsSet.contains(it.androidPackage) }
                    }
                }

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy((8 * baseScale).dp),
                    verticalArrangement = Arrangement.spacedBy((8 * baseScale).dp)
                ) {
                    matchingAppList.forEach { app ->
                        Box(
                            modifier = Modifier
                                .size((40 * baseScale).dp)
                                .background(FFColors.surfaceAlt, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (installedApps.isNotEmpty()) {
                                AppIconImage(
                                    packageName = app.androidPackage,
                                    modifier = Modifier.size((24 * baseScale).dp)
                                )
                            } else {
                                Icon(
                                    imageVector = AppLibrary.getIcon(app.iconName),
                                    contentDescription = app.name,
                                    tint = FFColors.textSecondary,
                                    modifier = Modifier.size((20 * baseScale).dp)
                                )
                            }
                        }
                    }

                    // Dashed add block
                    Box(
                        modifier = Modifier
                            .size((40 * baseScale).dp)
                            .background(Color.Transparent, CircleShape)
                            .border(1.dp, FFColors.border, CircleShape)
                            .clickable { showAppPickerModal = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add More",
                            tint = FFColors.textSecondary,
                            modifier = Modifier.size((16 * baseScale).dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(mediumSpacer))

        // "Enter Focus" Start Button
        val totalSeconds = (durationHrs * 3600) + (durationMins * 60) + durationSecs
        val canStart = totalSeconds > 0 && intentionText.isNotBlank()

        Button(
            onClick = {
                if (canStart) {
                    viewModel.startFocusSession(totalSeconds, intentionText, selectedAppsSet.toList())
                }
            },
            enabled = canStart,
            modifier = Modifier
                .fillMaxWidth()
                .height((60 * baseScale).dp)
                .testTag("enter_focus_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canStart) FFColors.textPrimary else FFColors.surface,
                contentColor = if (canStart) (if (FFColors.isDark) Color.Black else Color.White) else FFColors.textDisabled,
                disabledContainerColor = FFColors.surface,
                disabledContentColor = FFColors.textDisabled
            ),
            shape = RoundedCornerShape(16.dp),
            border = if (!canStart) BorderStroke(1.dp, FFColors.borderSubtle) else null
        ) {
            Text(
                text = "Enter Focus",
                fontWeight = FontWeight.Bold,
                fontSize = (16 * baseScale).sp
            )
        }

        Spacer(modifier = Modifier.height(largeSpacer * 2.5f))
    }
}

    // Modal Sheet of ALL APPS Library Checklists
    if (showAppPickerModal) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val density = androidx.compose.ui.platform.LocalDensity.current
        
        val screenWidth = configuration.screenWidthDp.dp
        val screenHeight = configuration.screenHeightDp.dp
        
        val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
        val topInset = systemBarsPadding.calculateTopPadding()
        val bottomInset = systemBarsPadding.calculateBottomPadding()
        
        Dialog(
            onDismissRequest = { showAppPickerModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE609090B)) // opaque dark overlay
                    .clickable { showAppPickerModal = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight - topInset - 60.dp)
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(FFColors.surface)
                        .clickable(enabled = true, onClick = {}) // absorb clicks inside sheet
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Allowed App Library",
                            fontSize = 20.sp,
                            color = FFColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(onClick = { showAppPickerModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = FFColors.textPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    var searchFilter by remember { mutableStateOf("") }

                    // Search text bar
                    OutlinedTextField(
                        value = searchFilter,
                        onValueChange = { searchFilter = it },
                        placeholder = { Text("Search apps...", color = FFColors.textDisabled) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FFColors.orange,
                            unfocusedBorderColor = FFColors.borderSubtle,
                            focusedTextColor = FFColors.textPrimary,
                            unfocusedTextColor = FFColors.textPrimary
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val filteredApps = remember(searchFilter, installedApps) {
                        val baseList = if (installedApps.isNotEmpty()) {
                            installedApps.map { AppDef(id = "", name = it.label, iconName = "", androidPackage = it.packageName) }
                        } else {
                            AppLibrary.ALL_APPS
                        }
                        if (searchFilter.isBlank()) {
                            baseList
                        } else {
                            baseList.filter { it.name.contains(searchFilter, ignoreCase = true) }
                        }
                    }

                    // Lazy grid of selector companion tiles
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 96.dp),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredApps) { app ->
                            val isChosen = selectedAppsSet.contains(app.androidPackage)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .height(118.dp)
                                    .background(
                                        color = if (isChosen) {
                                            if (FFColors.isDark) Color(0xFF2E251E) else Color(0xFFFFF7ED)
                                        } else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isChosen) FFColors.orange else FFColors.borderSubtle,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { viewModel.toggleAppSessionSelection(app.androidPackage) }
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (isChosen) FFColors.orange else FFColors.surfaceAlt,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (installedApps.isNotEmpty()) {
                                        AppIconImage(
                                            packageName = app.androidPackage,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = AppLibrary.getIcon(app.iconName),
                                            contentDescription = app.name,
                                            tint = if (isChosen) Color.White else FFColors.textSecondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = app.name,
                                    fontSize = 12.sp,
                                    color = if (isChosen) FFColors.textPrimary else FFColors.textSecondary,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sticky apply CTA button
                    Button(
                        onClick = { showAppPickerModal = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FFColors.textPrimary,
                            contentColor = if (FFColors.isDark) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "Apply (${selectedAppsSet.size} apps)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Space based on bottom system navigation inset to prevent rendering under toolbar
                    val safeGap = if (bottomInset > 0.dp) bottomInset + 12.dp else 24.dp
                    Spacer(modifier = Modifier.height(safeGap))
                }
            }
        }
    }
}
}

@Composable
fun Text(text: String, fontSize: androidx.compose.ui.unit.TextUnit, buildStyle: TextStyle, modifier: Modifier) {
    Text(
        text = text,
        fontSize = fontSize,
        style = buildStyle,
        modifier = modifier
    )
}

@Composable
fun DurationPickerColumn(
    value: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onIncrement, modifier = Modifier.size(52.dp)) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Up",
                tint = FFColors.textSecondary,
                modifier = Modifier.size(32.dp)
            )
        }

        Text(
            text = String.format(Locale.getDefault(), "%02d", value),
            fontSize = 56.sp,
            fontWeight = FontWeight.Light,
            color = FFColors.textPrimary,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        IconButton(onClick = onDecrement, modifier = Modifier.size(52.dp)) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Down",
                tint = FFColors.textSecondary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}


// ==========================================
// 5. PROFILE SCREEN WORKSPACE
// ==========================================
@Composable
fun ProfileScreen(viewModel: FocusViewModel) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Notifications enabled successfully!", Toast.LENGTH_SHORT).show()
            NotificationHelper.sendNotification(
                context,
                "Push Notifications Enabled",
                "You will now receive alerts, session logs, and daily focus reminders!"
            )
        } else {
            Toast.makeText(context, "Notification permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    var showEditProfileModal by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf("") }

    var showGoalModal by remember { mutableStateOf(false) }
    var editGoalVal by remember { mutableStateOf(120) }

    var showScheduleModal by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<ScheduleEntry?>(null) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    val baseScale = (screenWidth.value / 360f).coerceIn(0.85f, 1.4f)
    val responsiveHSpacing = (screenWidth.value * 0.06f).coerceIn(16f, 32f).dp
    val responsiveVSpacing = (screenHeight.value * 0.03f).coerceIn(16f, 32f).dp
    val smallSpacer = (screenHeight.value * 0.012f).coerceIn(6f, 16f).dp
    val mediumSpacer = (screenHeight.value * 0.022f).coerceIn(12f, 24f).dp
    val largeSpacer = (screenHeight.value * 0.038f).coerceIn(24f, 48f).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FFColors.bg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = responsiveHSpacing, vertical = responsiveVSpacing)
        ) {
            // --- HEADER CARD PROFILE ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FFColors.surface, RoundedCornerShape(32.dp))
                    .border(1.dp, FFColors.borderSubtle, RoundedCornerShape(32.dp))
                    .padding(horizontal = responsiveHSpacing, vertical = responsiveVSpacing)
            ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                // Initial Letter circle avatar
                Box(
                    modifier = Modifier
                        .size((80 * baseScale).dp)
                        .background(FFColors.surfaceAlt, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val fallbackLetter = user?.displayName?.take(1)?.uppercase() ?: "F"
                    Text(
                        text = fallbackLetter,
                        fontSize = (32 * baseScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = FFColors.orange
                    )
                }

                Spacer(modifier = Modifier.height(smallSpacer))

                Text(
                    text = user?.displayName ?: "Focus Flowee",
                    fontSize = (22 * baseScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = FFColors.textPrimary
                )

                Text(
                    text = user?.email ?: "anonymous@focusflow.ai",
                    fontSize = (13 * baseScale).sp,
                    color = FFColors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = smallSpacer)
                )

                // Edit Button
                OutlinedButton(
                    onClick = {
                        editNameInput = user?.displayName ?: ""
                        showEditProfileModal = true
                    },
                    border = BorderStroke(1.dp, FFColors.border),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Edit Profile", color = FFColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = (12 * baseScale).sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(smallSpacer))

        // --- GOAL ROW CARD ---
        RowCardItem(
            label = "Daily Concentration Goal",
            value = "${(user?.dailyGoal ?: 120) / 60} hours",
            onClick = {
                editGoalVal = user?.dailyGoal ?: 120
                showGoalModal = true
            }
        )

        Spacer(modifier = Modifier.height(smallSpacer))

        // --- WEEKLY SCHEDULES CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FFColors.surface),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, FFColors.borderSubtle)
        ) {
            Column(modifier = Modifier.padding(responsiveHSpacing)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FOCUS SCHEDULES",
                        fontSize = (10 * baseScale).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp,
                        color = FFColors.textMuted,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = "+ Add",
                        fontSize = (11 * baseScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = FFColors.orange,
                        modifier = Modifier.clickable {
                            editingSchedule = null
                            showScheduleModal = true
                        }
                    )
                }

                Spacer(modifier = Modifier.height(smallSpacer))

                val scheduleList = remember(user?.scheduleJson) {
                    Converters().toScheduleList(user?.scheduleJson)
                }

                if (scheduleList.isEmpty()) {
                    Text(
                        text = "No focus times scheduled yet. Build deep work calendars to plan consistency.",
                        color = FFColors.textDisabled,
                        fontSize = (12 * baseScale).sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        scheduleList.forEach { schedule ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(FFColors.surfaceAlt, RoundedCornerShape(12.dp))
                                    .clickable {
                                        editingSchedule = schedule
                                        showScheduleModal = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Day Initial Icon Circle
                                    Box(
                                        modifier = Modifier
                                            .size((28 * baseScale).dp)
                                            .background(FFColors.surface, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = schedule.day.take(1).uppercase(),
                                            fontSize = (11 * baseScale).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FFColors.textPrimary
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width((12 * baseScale).dp))

                                    Column {
                                        Text(text = schedule.day, color = FFColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = (13 * baseScale).sp)
                                        Text(text = "${schedule.startTime} - ${schedule.endTime}", color = FFColors.textSecondary, fontSize = (11 * baseScale).sp)
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            editingSchedule = schedule
                                            showScheduleModal = true
                                        },
                                        modifier = Modifier.size((36 * baseScale).dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Block",
                                            tint = FFColors.textSecondary,
                                            modifier = Modifier.size((16 * baseScale).dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width((4 * baseScale).dp))
                                    IconButton(
                                        onClick = { viewModel.deleteScheduleBlock(schedule) },
                                        modifier = Modifier.size((36 * baseScale).dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = FFColors.red,
                                            modifier = Modifier.size((16 * baseScale).dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(smallSpacer))

        // --- SETTINGS TOGGLES CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FFColors.surface),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, FFColors.borderSubtle)
        ) {
            Column(modifier = Modifier.padding(responsiveHSpacing)) {
                Text(
                    text = "SETTINGS / PREFERENCES",
                    fontSize = (10 * baseScale).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = FFColors.textMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Notification custom toggle
                val notifyChecked = user?.notifications ?: true
                val darkChecked = user?.darkMode ?: true
                val privChecked = user?.privacyMode ?: false

                SettingRowToggle(
                    icon = Icons.Default.Notifications,
                    label = "Push Notifications",
                    checked = notifyChecked,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                NotificationHelper.sendNotification(
                                    context,
                                    "Push Notifications Enabled",
                                    "You will now receive alerts, session logs, and daily focus reminders!"
                                )
                            }
                        }
                        viewModel.updateSettings(isChecked, darkChecked, privChecked)
                    }
                )

                SettingRowToggle(
                    icon = Icons.Default.DarkMode,
                    label = "System Dark Mode",
                    checked = darkChecked,
                    onCheckedChange = { viewModel.updateSettings(notifyChecked, it, privChecked) }
                )

                SettingRowToggle(
                    icon = Icons.Default.Lock,
                    label = "Lock restrict mode",
                    checked = privChecked,
                    onCheckedChange = { viewModel.updateSettings(notifyChecked, darkChecked, it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(smallSpacer))

        // --- DANGER ZONE CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FFColors.surface),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, FFColors.borderSubtle)
        ) {
            Column(modifier = Modifier.padding(responsiveHSpacing)) {
                Text(
                    text = "DANGER ZONE",
                    fontSize = (10 * baseScale).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = FFColors.textMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Sign Out Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.logout() }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Log Out",
                        color = FFColors.red,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (14 * baseScale).sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(Icons.Default.ExitToApp, contentDescription = "Exit", tint = FFColors.red, modifier = Modifier.size((18 * baseScale).dp))
                }

                Divider(color = FFColors.borderSubtle, thickness = 1.dp)

                // Delete Account
                var showDeleteConfirm by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeleteConfirm = true }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Delete Account",
                        color = FFColors.red,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (14 * baseScale).sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(Icons.Default.DeleteForever, contentDescription = "Delete", tint = FFColors.red, modifier = Modifier.size((18 * baseScale).dp))
                }

                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        containerColor = FFColors.surface,
                        title = { Text("Delete Account permanently?", color = FFColors.textPrimary) },
                        text = { Text("This will delete your profiles, scheduled blockers, and focus timelines. This action is irreversible.", color = FFColors.textSecondary) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeleteConfirm = false
                                    viewModel.deleteAccount()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = FFColors.red)
                            ) {
                                Text("Delete Forever", color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text("Cancel", color = FFColors.textSecondary)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(120.dp))
    }
}

    // Modal forms
    if (showEditProfileModal) {
        AlertDialog(
            onDismissRequest = { showEditProfileModal = false },
            containerColor = FFColors.surface,
            title = { Text("Update Profile", color = FFColors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = editNameInput,
                    onValueChange = { editNameInput = it },
                    label = { Text("Display Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FFColors.orange,
                        unfocusedBorderColor = FFColors.border,
                        focusedTextColor = FFColors.textPrimary,
                        unfocusedTextColor = FFColors.textPrimary,
                        focusedLabelColor = FFColors.orange,
                        unfocusedLabelColor = FFColors.textSecondary
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditProfileModal = false
                        viewModel.updateDisplayName(editNameInput)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FFColors.textPrimary,
                        contentColor = if (FFColors.isDark) Color.Black else Color.White
                    )
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold, color = if (FFColors.isDark) Color.Black else Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileModal = false }) {
                    Text("Discard", color = FFColors.textSecondary)
                }
            }
        )
    }

    // Daily Goal Step Modal
    if (showGoalModal) {
        AlertDialog(
            onDismissRequest = { showGoalModal = false },
            containerColor = FFColors.surface,
            title = { Text("Daily Concentration Goal", color = FFColors.textPrimary) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Set minutes of focus per day",
                        color = FFColors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { if (editGoalVal > 15) editGoalVal -= 15 }) {
                            Icon(Icons.Default.Remove, contentDescription = "Sub", tint = FFColors.textPrimary)
                        }
                        
                        Text(
                            text = "${editGoalVal}m",
                            fontSize = 32.sp,
                            color = FFColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        IconButton(onClick = { if (editGoalVal < 960) editGoalVal += 15 }) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = FFColors.textPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "(${editGoalVal / 60}h ${editGoalVal % 60}m per day)", color = FFColors.textDisabled, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGoalModal = false
                        viewModel.updateDailyGoal(editGoalVal)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FFColors.textPrimary,
                        contentColor = if (FFColors.isDark) Color.Black else Color.White
                    )
                ) {
                    Text("Apply Goal", fontWeight = FontWeight.Bold, color = if (FFColors.isDark) Color.Black else Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalModal = false }) {
                    Text("Cancel", color = FFColors.textSecondary)
                }
            }
        )
    }

    // Focus Schedule ADD/EDIT Modal
    if (showScheduleModal) {
        val isEditing = editingSchedule != null
        val initialDay = editingSchedule?.day ?: "Monday"

        val startParsed = remember(editingSchedule) {
            parseTimeString(editingSchedule?.startTime ?: "09:00 AM")
        }
        val endParsed = remember(editingSchedule) {
            parseTimeString(editingSchedule?.endTime ?: "05:00 PM")
        }

        var selectedDay by remember(editingSchedule) { mutableStateOf(initialDay) }

        var startHour by remember(editingSchedule) { mutableStateOf(startParsed.first) }
        var startMinute by remember(editingSchedule) { mutableStateOf(startParsed.second) }
        var startAmPm by remember(editingSchedule) { mutableStateOf(if (startParsed.third) "PM" else "AM") }

        var endHour by remember(editingSchedule) { mutableStateOf(endParsed.first) }
        var endMinute by remember(editingSchedule) { mutableStateOf(endParsed.second) }
        var endAmPm by remember(editingSchedule) { mutableStateOf(if (endParsed.third) "PM" else "AM") }

        AlertDialog(
            onDismissRequest = {
                showScheduleModal = false
                editingSchedule = null
            },
            containerColor = FFColors.surface,
            title = {
                Text(
                    text = if (isEditing) "Edit Focus Time Block" else "Add Focus Time Block",
                    color = FFColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("DAY OF WEEK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FFColors.textMuted, letterSpacing = 1.2.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                    
                    // Horizontal Day Stepper Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        days.forEach { day ->
                            val active = day == selectedDay
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (active) FFColors.textPrimary else FFColors.surfaceAlt,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedDay = day }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = day,
                                    fontSize = 11.sp,
                                    color = if (active) (if (FFColors.isDark) Color.Black else Color.White) else FFColors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text("START TIME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FFColors.textMuted, letterSpacing = 1.2.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = startHour,
                            onValueChange = { newVal ->
                                val clean = newVal.filter { it.isDigit() }.take(2)
                                if (clean.isEmpty()) {
                                    startHour = ""
                                } else {
                                    val num = clean.toIntOrNull() ?: 12
                                    if (num in 1..12) {
                                        startHour = clean
                                    }
                                }
                            },
                            placeholder = { Text("09", color = FFColors.textDisabled) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FFColors.orange,
                                unfocusedBorderColor = FFColors.borderSubtle,
                                focusedTextColor = FFColors.textPrimary,
                                unfocusedTextColor = FFColors.textPrimary
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.width(60.dp)
                        )

                        Text(":", color = FFColors.textPrimary, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = startMinute,
                            onValueChange = { newVal ->
                                val clean = newVal.filter { it.isDigit() }.take(2)
                                if (clean.isEmpty()) {
                                    startMinute = ""
                                } else {
                                    val num = clean.toIntOrNull() ?: 0
                                    if (num in 0..59) {
                                        startMinute = clean
                                    }
                                }
                            },
                            placeholder = { Text("00", color = FFColors.textDisabled) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FFColors.orange,
                                unfocusedBorderColor = FFColors.borderSubtle,
                                focusedTextColor = FFColors.textPrimary,
                                unfocusedTextColor = FFColors.textPrimary
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.width(60.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Segmented AM/PM Row
                        Row(
                            modifier = Modifier
                                .background(FFColors.surfaceAlt, RoundedCornerShape(8.dp))
                                .padding(2.dp)
                        ) {
                            listOf("AM", "PM").forEach { opt ->
                                val active = opt == startAmPm
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (active) FFColors.orange else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { startAmPm = opt }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = opt,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color.White else FFColors.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("END TIME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FFColors.textMuted, letterSpacing = 1.2.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = endHour,
                            onValueChange = { newVal ->
                                val clean = newVal.filter { it.isDigit() }.take(2)
                                if (clean.isEmpty()) {
                                    endHour = ""
                                } else {
                                    val num = clean.toIntOrNull() ?: 5
                                    if (num in 1..12) {
                                        endHour = clean
                                    }
                                }
                            },
                            placeholder = { Text("05", color = FFColors.textDisabled) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FFColors.orange,
                                unfocusedBorderColor = FFColors.borderSubtle,
                                focusedTextColor = FFColors.textPrimary,
                                unfocusedTextColor = FFColors.textPrimary
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.width(60.dp)
                        )

                        Text(":", color = FFColors.textPrimary, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = endMinute,
                            onValueChange = { newVal ->
                                val clean = newVal.filter { it.isDigit() }.take(2)
                                if (clean.isEmpty()) {
                                    endMinute = ""
                                } else {
                                    val num = clean.toIntOrNull() ?: 0
                                    if (num in 0..59) {
                                        endMinute = clean
                                    }
                                }
                            },
                            placeholder = { Text("00", color = FFColors.textDisabled) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FFColors.orange,
                                unfocusedBorderColor = FFColors.borderSubtle,
                                focusedTextColor = FFColors.textPrimary,
                                unfocusedTextColor = FFColors.textPrimary
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.width(60.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Segmented AM/PM Row
                        Row(
                            modifier = Modifier
                                .background(FFColors.surfaceAlt, RoundedCornerShape(8.dp))
                                .padding(2.dp)
                        ) {
                            listOf("AM", "PM").forEach { opt ->
                                val active = opt == endAmPm
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (active) FFColors.orange else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { endAmPm = opt }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = opt,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color.White else FFColors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalStartH = startHour.padStart(2, '0').ifEmpty { "12" }
                        val finalStartM = startMinute.padStart(2, '0').ifEmpty { "00" }
                        val finalEndH = endHour.padStart(2, '0').ifEmpty { "12" }
                        val finalEndM = endMinute.padStart(2, '0').ifEmpty { "00" }

                        val finalStart = "$finalStartH:$finalStartM $startAmPm"
                        val finalEnd = "$finalEndH:$finalEndM $endAmPm"

                        showScheduleModal = false
                        if (isEditing) {
                            viewModel.editScheduleBlock(editingSchedule!!, ScheduleEntry(selectedDay, finalStart, finalEnd))
                        } else {
                            viewModel.addScheduleBlock(ScheduleEntry(selectedDay, finalStart, finalEnd))
                        }
                        editingSchedule = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FFColors.textPrimary,
                        contentColor = if (FFColors.isDark) Color.Black else Color.White
                    )
                ) {
                    Text(
                        text = if (isEditing) "Save Changes" else "Block Focus",
                        fontWeight = FontWeight.Bold,
                        color = if (FFColors.isDark) Color.Black else Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showScheduleModal = false
                    editingSchedule = null
                }) {
                    Text("Cancel", color = FFColors.textSecondary)
                }
            }
        )
    }
}

@Composable
fun RowCardItem(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FFColors.surface, RoundedCornerShape(24.dp))
            .border(1.dp, FFColors.borderSubtle, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 13.sp, color = FFColors.textMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, fontSize = 24.sp, color = FFColors.textPrimary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Edit", tint = FFColors.textSecondary, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun SettingRowToggle(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(FFColors.surfaceAlt, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = FFColors.textSecondary, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(18.dp))
            Text(
                text = label,
                color = FFColors.textPrimary,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = FFColors.orange,
                uncheckedThumbColor = FFColors.textSecondary,
                uncheckedTrackColor = FFColors.surfaceAlt
            )
        )
    }
}

fun parseTimeString(timeStr: String): Triple<String, String, Boolean> {
    var hour = "09"
    var minute = "00"
    var isPm = false

    try {
        val clean = timeStr.trim().uppercase()
        val hasAmPm = clean.endsWith("AM") || clean.endsWith("PM")
        val amPmPart = if (clean.endsWith("PM")) "PM" else if (clean.endsWith("AM")) "AM" else ""
        
        val timePart = if (hasAmPm) clean.substring(0, clean.length - 2).trim() else clean
        val parts = timePart.split(":")
        if (parts.isNotEmpty()) {
            var h = parts[0].toIntOrNull() ?: 9
            val m = if (parts.size > 1) {
                parts[1].filter { it.isDigit() }.toIntOrNull() ?: 0
            } else 0
            
            if (hasAmPm) {
                isPm = amPmPart == "PM"
                if (h < 1) h = 12
                if (h > 12) h = 12
                hour = String.format("%02d", h)
                minute = String.format("%02d", m)
            } else {
                if (h >= 12) {
                    isPm = true
                    val h12 = if (h == 12) 12 else h - 12
                    hour = String.format("%02d", h12)
                } else {
                    isPm = false
                    val h12 = if (h == 0) 12 else h
                    hour = String.format("%02d", h12)
                }
                minute = String.format("%02d", m)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return Triple(hour, minute, isPm)
}
