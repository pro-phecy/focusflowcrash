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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.roundToInt
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
    val isTimerRunning by viewModel.isTimerRunning.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(FocusTab.HOME) }
    var showHistoryLog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.checkTimerSync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Dynamic color reactivity linkage
    FFColors.isDark = user?.darkMode ?: true

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FFColors.bg)
    ) {
        val currentScreen = when {
            !isOnboardingCompleted -> MainAppScreen.ONBOARDING
            !isAuthenticated -> MainAppScreen.LOGIN
            activeSession != null -> MainAppScreen.FOCUS_LAUNCHER
            else -> MainAppScreen.MAIN_SCAFFOLD
        }

        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState == MainAppScreen.FOCUS_LAUNCHER) {
                    // Zoom/Scale and slide up transition when launching focus mode (extremely premium!)
                    (fadeIn(animationSpec = tween(600, easing = EaseOutQuart)) + 
                     scaleIn(initialScale = 0.92f, animationSpec = tween(600, easing = EaseOutQuart)) + 
                     slideInVertically(initialOffsetY = { it / 6 }, animationSpec = tween(600, easing = EaseOutQuart)))
                        .togetherWith(fadeOut(animationSpec = tween(450, easing = EaseInQuart)) + 
                                     scaleOut(targetScale = 0.96f, animationSpec = tween(450, easing = EaseInQuart)))
                } else if (initialState == MainAppScreen.FOCUS_LAUNCHER) {
                    // Premium zoom-out fade when leaving focus mode
                    (fadeIn(animationSpec = tween(500, easing = EaseOutQuart)) + 
                     scaleIn(initialScale = 1.04f, animationSpec = tween(500, easing = EaseOutQuart)))
                        .togetherWith(fadeOut(animationSpec = tween(500, easing = EaseInQuart)) + 
                                     scaleOut(targetScale = 0.95f, animationSpec = tween(500, easing = EaseInQuart)) +
                                     slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(500, easing = EaseInQuart)))
                } else {
                    // Standard premium slide/fade between regular auth / onboarding / main screens
                    (fadeIn(animationSpec = tween(450, easing = EaseInOutQuart)) + 
                     scaleIn(initialScale = 0.98f, animationSpec = tween(450, easing = EaseInOutQuart)))
                        .togetherWith(fadeOut(animationSpec = tween(300, easing = EaseInOutQuart)))
                }
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
                        isTimerRunning = isTimerRunning,
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
                        onPause = { viewModel.pauseFocusSession() },
                        onResume = { viewModel.resumeFocusSession() },
                        onResetTimer = { viewModel.resetFocusSessionTimer() },
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
                        AmbientGlowingBackground(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(paddingValues)
                        ) {
                            AnimatedContent(
                                targetState = currentTab,
                                transitionSpec = {
                                    val direction = if (initialState.ordinal < targetState.ordinal) {
                                        AnimatedContentTransitionScope.SlideDirection.Left
                                    } else {
                                        AnimatedContentTransitionScope.SlideDirection.Right
                                    }
                                    slideIntoContainer(
                                        towards = direction,
                                        animationSpec = tween(400, easing = EaseOutQuart)
                                    ) togetherWith slideOutOfContainer(
                                        towards = direction,
                                        animationSpec = tween(400, easing = EaseOutQuart)
                                    )
                                },
                                label = "TabTransition",
                                modifier = Modifier.fillMaxSize()
                            ) { tab ->
                                when (tab) {
                                    FocusTab.HOME -> HomeScreen(
                                        viewModel = viewModel,
                                        onStartFocusClick = { currentTab = FocusTab.TIMER },
                                        onOpenHistoryClick = { showHistoryLog = true }
                                    )
                                    FocusTab.TIMER -> TimerScreen(viewModel = viewModel)
                                    FocusTab.PROFILE -> ProfileScreen(viewModel = viewModel)
                                }
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 16.dp)
        )

        FocusSessionHistorySidebar(
            viewModel = viewModel,
            isOpen = showHistoryLog,
            onClose = { showHistoryLog = false }
        )
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
    val tint by animateColorAsState(
        targetValue = if (isSelected) FFColors.textPrimary else FFColors.textDisabled,
        animationSpec = tween(durationMillis = 350, easing = EaseOutQuart),
        label = "tab_tint"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tab_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(80.dp)
            .fillMaxHeight()
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
            .testTag("tab_item_${label.lowercase()}")
    ) {
        Icon(
            imageVector = if (isSelected) iconSelected else icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = tint
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
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.clearAuthError()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FFColors.bg)
            .safeDrawingPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        // Decorative ambient background glow
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val primaryGlow = if (FFColors.isDark) Color(0x0AF97316) else Color(0x06F97316)
            val secondaryGlow = if (FFColors.isDark) Color(0x083B82F6) else Color(0x043B82F6)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryGlow, Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.2f),
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondaryGlow, Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.8f),
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f
            )
        }

        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Spark Minimal Logo Accent
            AnimatedEntrance(delayMillis = 50) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(FFColors.orange.copy(alpha = 0.15f), FFColors.purple.copy(alpha = 0.1f))
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .border(1.dp, FFColors.orange.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.OfflineBolt,
                        contentDescription = "Logo",
                        tint = FFColors.orange,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedEntrance(delayMillis = 120) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isSignUpMode) "Begin your Flow" else "Welcome to FocusFlow",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = FFColors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = if (isSignUpMode) "Create an account for cloud synced statistics" else "Deep work and scheduled concentration management",
                        fontSize = 13.sp,
                        color = FFColors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 28.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }

            // Glassmorphic Form Card
            AnimatedEntrance(delayMillis = 200) {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Dynamic Tab Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(FFColors.surfaceAlt.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .border(1.dp, FFColors.borderSubtle, RoundedCornerShape(14.dp))
                                .padding(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        if (!isSignUpMode) FFColors.surface else Color.Transparent,
                                        RoundedCornerShape(11.dp)
                                    )
                                    .clickable(enabled = !isLoading) { 
                                        isSignUpMode = false 
                                        viewModel.clearAuthError()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sign In", 
                                    color = if (!isSignUpMode) FFColors.textPrimary else FFColors.textSecondary, 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 13.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        if (isSignUpMode) FFColors.surface else Color.Transparent,
                                        RoundedCornerShape(11.dp)
                                    )
                                    .clickable(enabled = !isLoading) { 
                                        isSignUpMode = true 
                                        viewModel.clearAuthError()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sign Up", 
                                    color = if (isSignUpMode) FFColors.textPrimary else FFColors.textSecondary, 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Form Fields
                        if (isSignUpMode) {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                placeholder = { Text("Display Name", color = FFColors.textDisabled) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Name Icon",
                                        tint = FFColors.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("display_name_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FFColors.orange,
                                    unfocusedBorderColor = FFColors.border,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = FFColors.textPrimary,
                                    unfocusedTextColor = FFColors.textPrimary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = { Text("Email Address", color = FFColors.textDisabled) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Mail,
                                    contentDescription = "Email Icon",
                                    tint = FFColors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("email_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FFColors.orange,
                                unfocusedBorderColor = FFColors.border,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = FFColors.textPrimary,
                                unfocusedTextColor = FFColors.textPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = { Text("Password", color = FFColors.textDisabled) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Password Icon",
                                    tint = FFColors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FFColors.orange,
                                unfocusedBorderColor = FFColors.border,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = FFColors.textPrimary,
                                unfocusedTextColor = FFColors.textPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    enabled = !isLoading,
                                    onClick = { showPassword = !showPassword }
                                ) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle password",
                                        tint = FFColors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true
                        )

                        // Auth Error Display
                        val errorMsg = authError
                        if (errorMsg != null) {
                            Text(
                                text = errorMsg,
                                color = FFColors.red,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Submit Button
                        Button(
                            onClick = {
                                val emailTrimmed = email.trim()
                                val pwdTrimmed = password
                                val nameTrimmed = displayName.trim()
                                
                                if (isSignUpMode) {
                                    when {
                                        nameTrimmed.isEmpty() -> {
                                            viewModel.setAuthError("Display name is required.")
                                        }
                                        emailTrimmed.isEmpty() -> {
                                            viewModel.setAuthError("Email address is required.")
                                        }
                                        !emailTrimmed.contains("@") -> {
                                            viewModel.setAuthError("Please enter a valid email address.")
                                        }
                                        pwdTrimmed.isEmpty() -> {
                                            viewModel.setAuthError("Password is required.")
                                        }
                                        pwdTrimmed.length < 6 -> {
                                            viewModel.setAuthError("Password must be at least 6 characters.")
                                        }
                                        else -> {
                                            viewModel.register(emailTrimmed, password, nameTrimmed)
                                        }
                                    }
                                } else {
                                    when {
                                        emailTrimmed.isEmpty() -> {
                                            viewModel.setAuthError("Email address is required.")
                                        }
                                        !emailTrimmed.contains("@") -> {
                                            viewModel.setAuthError("Please enter a valid email address.")
                                        }
                                        pwdTrimmed.isEmpty() -> {
                                            viewModel.setAuthError("Password is required.")
                                        }
                                        pwdTrimmed.length < 4 -> {
                                            viewModel.setAuthError("Password must be at least 4 characters.")
                                        }
                                        else -> {
                                            viewModel.login(emailTrimmed, password)
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_submit_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FFColors.textPrimary,
                                contentColor = if (FFColors.isDark) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = if (FFColors.isDark) Color.Black else Color.White
                                )
                            } else {
                                Text(
                                    text = if (isSignUpMode) "Create Account" else "Sign In",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }


                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
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
        // Subtle ambient glowing spot behind onboarding
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val primaryGlow = if (FFColors.isDark) Color(0x06F97316) else Color(0x03F97316)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryGlow, Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.4f),
                    radius = size.width * 0.8f
                ),
                radius = size.width * 0.8f
            )
        }

        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
        ) {
            // Step counter label & Progress Trackers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STEP $step OF 4",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = FFColors.orange
                )

                val stepTitle = when (step) {
                    1 -> "Welcome"
                    2 -> "Daily Target"
                    3 -> "Companion Apps"
                    else -> "Complete"
                }
                Text(
                    text = stepTitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FFColors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 1..4) {
                    val isCompletedOrCurrent = i <= step
                    val activeColor = if (i == step) FFColors.orange else FFColors.textPrimary
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(
                                color = if (isCompletedOrCurrent) activeColor else FFColors.surfaceAlt,
                                shape = RoundedCornerShape(999.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (scaleIn(
                        initialScale = 0.95f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(400))).togetherWith(
                        scaleOut(
                            targetScale = 0.95f,
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

            // Navigation Buttons
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
                        enabled = step > 1,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Back",
                            color = if (step > 1) FFColors.textSecondary else Color.Transparent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Button(
                        onClick = { step += 1 },
                        modifier = Modifier
                            .width(150.dp)
                            .height(52.dp)
                            .testTag("onboarding_next_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FFColors.textPrimary,
                            contentColor = if (FFColors.isDark) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Next", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 14.sp,
                            color = if (FFColors.isDark) Color.Black else Color.White
                        )
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
        // Premium Minimalist Zen Breathing Loop Visual
        AnimatedEntrance(delayMillis = 50) {
            Box(
                modifier = Modifier
                    .size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Interactive concentric rings representing flows/bounds
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Outermost ambient aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(FFColors.orange.copy(alpha = 0.15f), Color.Transparent),
                            radius = size.width * 0.5f
                        )
                    )
                    // Mid rings
                    drawCircle(
                        color = FFColors.orange.copy(alpha = 0.3f),
                        radius = size.width * 0.35f,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = FFColors.orange.copy(alpha = 0.6f),
                        radius = size.width * 0.25f,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    // Breathing center focal point
                    drawCircle(
                        color = FFColors.orange,
                        radius = size.width * 0.08f
                    )
                }

                Icon(
                    imageVector = Icons.Default.AllInclusive,
                    contentDescription = "Zen Infinite Icon",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedEntrance(delayMillis = 150) {
            Text(
                text = "Welcome to FocusFlow",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = FFColors.textPrimary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedEntrance(delayMillis = 250) {
            Text(
                text = "Reclaim your mental space. Create distraction-free boundaries, schedule focused sessions, and selectively allow essential communication to cultivate deep concentration.",
                fontSize = 14.sp,
                color = FFColors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
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
        AnimatedEntrance(delayMillis = 50) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Define Your Daily Goal",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = FFColors.textPrimary
                )

                Text(
                    text = "Select your target hours of hyper-focused work per day",
                    fontSize = 13.sp,
                    color = FFColors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Custom hours display
        val h = minutes / 60
        val m = minutes % 60
        val displayStr = if (h > 0) "${h}h ${m}m" else "${m}m"

        // Glow selection card
        AnimatedEntrance(delayMillis = 150) {
            GlassmorphicCard(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    IconButton(
                        onClick = { if (minutes > 15) onMinutesChange(minutes - 15) },
                        modifier = Modifier
                            .size(44.dp)
                            .background(FFColors.surfaceAlt, CircleShape)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrement goal", tint = FFColors.textPrimary)
                    }

                    Text(
                        text = displayStr,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Light,
                        color = FFColors.textPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    IconButton(
                        onClick = { if (minutes < 960) onMinutesChange(minutes + 15) },
                        modifier = Modifier
                            .size(44.dp)
                            .background(FFColors.surfaceAlt, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increment goal", tint = FFColors.textPrimary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Preset Selector Chips
        AnimatedEntrance(delayMillis = 250) {
            Column(
                modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth()
            ) {
                Text(
                    text = "RECOMMENDED PRESETS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = FFColors.textMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val presets = listOf(60, 120, 240, 480)
                    presets.forEach { preset ->
                        val label = when (preset) {
                            60 -> "1 Hour"
                            120 -> "2 Hours"
                            240 -> "4 Hours"
                            else -> "8 Hours"
                        }
                        val isActive = preset == minutes
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .background(
                                    color = if (isActive) FFColors.orangeBg else FFColors.surface,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isActive) FFColors.orange else FFColors.borderSubtle,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onMinutesChange(preset) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isActive) FFColors.orange else FFColors.textPrimary,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
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
        AnimatedEntrance(delayMillis = 50) {
            Column {
                Text(
                    text = "Companion Essentials",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = FFColors.textPrimary
                )

                Text(
                    text = "Select core companion apps always permitted to bypass concentration blocks.",
                    fontSize = 13.sp,
                    color = FFColors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                )
            }
        }

        // Search text bar
        AnimatedEntrance(delayMillis = 150) {
            OutlinedTextField(
                value = searchFilter,
                onValueChange = { searchFilter = it },
                placeholder = { Text("Search system apps...", color = FFColors.textDisabled) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FFColors.orange,
                    unfocusedBorderColor = FFColors.borderSubtle,
                    focusedTextColor = FFColors.textPrimary,
                    unfocusedTextColor = FFColors.textPrimary,
                    focusedContainerColor = FFColors.surface,
                    unfocusedContainerColor = FFColors.surface
                ),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        // Grid of real or mock apps
        AnimatedEntrance(delayMillis = 250) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(appsToDisplay) { app ->
                    val isSel = selected.contains(app.androidPackage)
                    Box(
                        modifier = Modifier
                            .height(88.dp)
                            .background(
                                color = if (isSel) {
                                    if (FFColors.isDark) Color(0x1AF97316) else Color(0x0DF97316)
                                } else FFColors.surface,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                1.dp,
                                if (isSel) FFColors.orange else FFColors.borderSubtle,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { onToggle(app.androidPackage) }
                            .padding(8.dp),
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

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = app.name,
                                fontSize = 11.sp,
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
        AnimatedEntrance(delayMillis = 50) {
            Box(
                modifier = Modifier
                    .size(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(FFColors.green.copy(alpha = 0.2f), Color.Transparent),
                            radius = size.width * 0.5f
                        )
                    )
                    drawCircle(
                        color = FFColors.green.copy(alpha = 0.4f),
                        radius = size.width * 0.35f,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawCircle(
                        color = FFColors.green,
                        radius = size.width * 0.22f
                    )
                }
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedEntrance(delayMillis = 150) {
            Text(
                text = "You're All Set!",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = FFColors.textPrimary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedEntrance(delayMillis = 250) {
            Text(
                text = "Your custom focus workspace is fully initialized and calibrated. Tap below to launch your focus control dashboard.",
                fontSize = 14.sp,
                color = FFColors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        AnimatedEntrance(delayMillis = 350) {
            Button(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("get_started_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FFColors.textPrimary,
                    contentColor = if (FFColors.isDark) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Get Started", 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 14.sp,
                    color = if (FFColors.isDark) Color.Black else Color.White
                )
            }
        }
    }
}


// ==========================================
// 3. HOME SCREEN TAB WORKSPACE
// ==========================================
@Composable
fun HomeScreen(
    viewModel: FocusViewModel,
    onStartFocusClick: () -> Unit,
    onOpenHistoryClick: () -> Unit
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val prevWeekMinutes by viewModel.prevWeekMinutes.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedPackages.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAppDrawer by remember { mutableStateOf(false) }
    var showGoalSettingsPanel by remember { mutableStateOf(false) }

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
        val formatTime = SimpleDateFormat("h:mm", Locale.getDefault())
        val formatDate = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        while (true) {
            val now = Date()
            timeString = formatTime.format(now)
            dateString = formatDate.format(now).uppercase()
            delay(1000)
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Decimals of screen dimensions for responsive sizing
    val responsiveHSpacing = (screenWidth.value * 0.06f).coerceIn(16f, 32f).dp
    val responsiveVSpacing = (screenHeight.value * 0.035f).coerceIn(20f, 36f).dp
    val smallSpacer = (screenHeight.value * 0.015f).coerceIn(8f, 20f).dp
    val mediumSpacer = (screenHeight.value * 0.026f).coerceIn(16f, 28f).dp
    val largeSpacer = (screenHeight.value * 0.042f).coerceIn(28f, 54f).dp

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
            .background(Color.Transparent),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = responsiveHSpacing, vertical = responsiveVSpacing)
        ) {
            
            // Live Block & History Trigger
            AnimatedEntrance(delayMillis = 50) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
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
                    }

                    // Elegant card button to view focus history log
                    Button(
                        onClick = onOpenHistoryClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FFColors.surface,
                            contentColor = FFColors.orange
                        ),
                        border = BorderStroke(1.dp, FFColors.borderSubtle),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("view_history_logs_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Focus History Log",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Log History",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Greeting Block
            val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                in 0..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                else -> "Good evening"
            }
            val name = user?.displayName ?: "Explorer"

            AnimatedEntrance(delayMillis = 150) {
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
            }

            // PINNED FAVORITES ROW (SYSTEM HOME LAUNCHER)
            AnimatedEntrance(delayMillis = 250) {
                if (favoriteApps.isNotEmpty()) {
                    Column {
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
            }

            // Today's Focus Card Progress Block
            AnimatedEntrance(delayMillis = 350) {
                DailyGoalProgressCard(
                    user = user,
                    stats = stats,
                    onUpdateGoal = { viewModel.updateDailyGoal(it) },
                    baseScale = baseScale,
                    responsiveHSpacing = responsiveHSpacing,
                    responsiveVSpacing = responsiveVSpacing,
                    smallSpacer = smallSpacer,
                    mediumSpacer = mediumSpacer,
                    onOpenSettings = { showGoalSettingsPanel = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (showGoalSettingsPanel) {
                Dialog(
                    onDismissRequest = { showGoalSettingsPanel = false }
                ) {
                    GoalSettingsPanel(
                        user = user,
                        stats = stats,
                        onDismiss = { showGoalSettingsPanel = false },
                        onApply = { newGoal ->
                            viewModel.updateDailyGoal(newGoal)
                            showGoalSettingsPanel = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(mediumSpacer))

            // Large white button to start focus mode
            AnimatedEntrance(delayMillis = 450) {
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
            }

        Spacer(modifier = Modifier.height(largeSpacer))

        // Activity Section Header and Streak Badge
        AnimatedEntrance(delayMillis = 550) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = "CONCENTRATION HISTORIC",
                        fontSize = sectionHeaderFontSize,
                        color = FFColors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onOpenHistoryClick,
                        modifier = Modifier
                            .size(28.dp)
                            .background(FFColors.surface, CircleShape)
                            .testTag("concentration_historic_log_icon")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "View complete history logs",
                            tint = FFColors.orange,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

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
        }

        Spacer(modifier = Modifier.height(smallSpacer))

        // Recharts-inspired Interactive Data Visualization Component (displays trends over time)
        AnimatedEntrance(delayMillis = 650) {
            RechartsStyledVisualization(
                stats = stats,
                baseScale = baseScale,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(mediumSpacer))

        // Comparative focus context card comparing current week vs previous week
        AnimatedEntrance(delayMillis = 750) {
            WeeklyComparisonCard(
                currentMinutes = stats.weekMinutes,
                previousMinutes = prevWeekMinutes,
                baseScale = baseScale,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(largeSpacer))

        // 3-stat Grid Row (Equal Width and Height Cards)
        AnimatedEntrance(delayMillis = 850) {
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
        }

        Spacer(modifier = Modifier.height(largeSpacer))

        // Open App Drawer Button
        AnimatedEntrance(delayMillis = 950) {
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
                        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                            .padding(vertical = 5.dp, horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            AppIconImage(
                                                packageName = app.packageName,
                                                modifier = Modifier.size(56.dp)
                                            )

                                            Spacer(modifier = Modifier.width(12.dp))

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
            .glassmorphic(shape = RoundedCornerShape(24.dp))
            .border(1.dp, GlassmorphismStyle.getGlassBorderBrush(), RoundedCornerShape(24.dp))
            .background(GlassmorphismStyle.getGlassBackgroundColor(), RoundedCornerShape(24.dp))
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


@Composable
fun WeeklyComparisonCard(
    currentMinutes: Int,
    previousMinutes: Int,
    baseScale: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val currentHours = currentMinutes / 60.0

    val percentChange = if (previousMinutes > 0) {
        ((currentMinutes - previousMinutes).toFloat() / previousMinutes * 100).roundToInt()
    } else {
        if (currentMinutes > 0) 100 else 0
    }

    GlassmorphicCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding((16 * baseScale).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "WEEKLY COMPARISON",
                    fontSize = (10 * baseScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = FFColors.textSecondary,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                // Narrative summary text
                val comparisonText = when {
                    currentMinutes > previousMinutes -> {
                        val diff = ((currentMinutes - previousMinutes) / 60.0)
                        if (diff >= 0.1) {
                            "You focused ${String.format(Locale.US, "%.1f", diff)}h more than last week. Outstanding progress!"
                        } else {
                            "You're slightly ahead of last week's focus time. Keep it up!"
                        }
                    }
                    currentMinutes < previousMinutes -> {
                        val diff = ((previousMinutes - currentMinutes) / 60.0)
                        if (diff >= 0.1) {
                            "You're ${String.format(Locale.US, "%.1f", diff)}h behind last week. Plan a focus session today!"
                        } else {
                            "Almost matching last week's pace. A quick focus block will get you there!"
                        }
                    }
                    currentMinutes > 0 -> {
                        "You've perfectly matched last week's focus duration of ${String.format(Locale.US, "%.1f", currentHours)} hours!"
                    }
                    else -> {
                        "Start your first focus session of the week to begin tracking trends."
                    }
                }

                Text(
                    text = comparisonText,
                    fontSize = (13 * baseScale).sp,
                    fontWeight = FontWeight.Medium,
                    color = FFColors.textPrimary,
                    lineHeight = (18 * baseScale).sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Percentage Badge & Trend Graphic
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                val trendColor = if (percentChange > 0) {
                    Color(0xFF4ADE80) // positive bright green
                } else if (percentChange < 0) {
                    Color(0xFFF87171) // negative soft red
                } else {
                    FFColors.textSecondary
                }

                val trendIcon = if (percentChange > 0) "▲" else if (percentChange < 0) "▼" else "•"
                val trendSign = if (percentChange > 0) "+" else ""

                Box(
                    modifier = Modifier
                        .background(trendColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = (10 * baseScale).dp, vertical = (6 * baseScale).dp)
                ) {
                    Text(
                        text = "$trendIcon $trendSign$percentChange%",
                        color = trendColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = (12 * baseScale).sp
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "vs. last week",
                    fontSize = (10 * baseScale).sp,
                    color = FFColors.textSecondary
                )
            }
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
    val responsiveVSpacing = (screenHeight.value * 0.035f).coerceIn(20f, 36f).dp
    val smallSpacer = (screenHeight.value * 0.015f).coerceIn(8f, 20f).dp
    val mediumSpacer = (screenHeight.value * 0.026f).coerceIn(16f, 28f).dp
    val largeSpacer = (screenHeight.value * 0.042f).coerceIn(28f, 54f).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = responsiveHSpacing, vertical = responsiveVSpacing)
        ) {
            AnimatedEntrance(delayMillis = 50) {
                Column {
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
                }
            }

        // Card 1: Hour/Min/Sec Column Selector
        AnimatedEntrance(delayMillis = 150) {
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp)
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
                            onValueChange = { durationHrs = it },
                            onIncrement = { if (durationHrs < 23) durationHrs += 1 else durationHrs = 0 },
                            onDecrement = { if (durationHrs > 0) durationHrs -= 1 else durationHrs = 23 },
                            maxValue = 23
                        )

                        Text(":", fontSize = (36 * baseScale).sp, style = TextStyle(color = FFColors.textSecondary), modifier = Modifier.padding(horizontal = (8 * baseScale).dp))

                        // Minutes column
                        DurationPickerColumn(
                            value = durationMins,
                            onValueChange = { durationMins = it },
                            onIncrement = { if (durationMins < 59) durationMins += 1 else durationMins = 0 },
                            onDecrement = { if (durationMins > 0) durationMins -= 1 else durationMins = 59 },
                            maxValue = 59
                        )

                        Text(":", fontSize = (36 * baseScale).sp, style = TextStyle(color = FFColors.textSecondary), modifier = Modifier.padding(horizontal = (8 * baseScale).dp))

                        // Seconds column
                        DurationPickerColumn(
                            value = durationSecs,
                            onValueChange = { durationSecs = it },
                            onIncrement = { if (durationSecs < 59) durationSecs += 1 else durationSecs = 0 },
                            onDecrement = { if (durationSecs > 0) durationSecs -= 1 else durationSecs = 59 },
                            maxValue = 59
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(smallSpacer))

        // Card 2: Interoperable Intention Text Field
        AnimatedEntrance(delayMillis = 250) {
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp)
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
        }

        Spacer(modifier = Modifier.height(smallSpacer))

        // Card 3: Essentials (Allowed Apps grid snapshot up to 7 items + "+N" overflow)
        AnimatedEntrance(delayMillis = 350) {
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp)
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
        }

        Spacer(modifier = Modifier.height(mediumSpacer))

        // "Enter Focus" Start Button
        val totalSeconds = (durationHrs * 3600) + (durationMins * 60) + durationSecs
        val canStart = totalSeconds > 0 && intentionText.isNotBlank()

        AnimatedEntrance(delayMillis = 450) {
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
                        columns = GridCells.Adaptive(minSize = 80.dp),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredApps) { app ->
                            val isChosen = selectedAppsSet.contains(app.androidPackage)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .height(112.dp)
                                    .background(
                                        color = if (isChosen) {
                                            if (FFColors.isDark) Color(0xFF2E251E) else Color(0xFFFFF7ED)
                                        } else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isChosen) FFColors.orange else FFColors.borderSubtle,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.toggleAppSessionSelection(app.androidPackage) }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(64.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (installedApps.isNotEmpty()) {
                                        AppIconImage(
                                            packageName = app.androidPackage,
                                            modifier = Modifier.size(64.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = AppLibrary.getIcon(app.iconName),
                                            contentDescription = app.name,
                                            tint = if (isChosen) FFColors.orange else FFColors.textSecondary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = app.name,
                                    fontSize = 11.sp,
                                    color = if (isChosen) FFColors.textPrimary else FFColors.textSecondary,
                                    maxLines = 1,
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
fun DurationPickerColumn(
    value: Int,
    onValueChange: (Int) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    maxValue: Int = 59
) {
    var isEditing by remember { mutableStateOf(false) }
    var textValue by remember(value) { mutableStateOf(value.toString()) }

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

        if (isEditing) {
            BasicTextField(
                value = textValue,
                onValueChange = { newVal ->
                    val filtered = newVal.filter { it.isDigit() }.take(2)
                    textValue = filtered
                    val parsed = filtered.toIntOrNull() ?: 0
                    if (parsed <= maxValue) {
                        onValueChange(parsed)
                    }
                },
                textStyle = TextStyle(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                    color = FFColors.textPrimary,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { isEditing = false }
                ),
                modifier = Modifier
                    .width(80.dp)
                    .padding(vertical = 4.dp),
                singleLine = true,
                cursorBrush = SolidColor(FFColors.textPrimary)
            )

            LaunchedEffect(isEditing) {
                if (!isEditing && textValue.isEmpty()) {
                    onValueChange(0)
                }
            }
        } else {
            Text(
                text = String.format(Locale.getDefault(), "%02d", value),
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                color = FFColors.textPrimary,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable { isEditing = true }
            )
        }

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
    val stats by viewModel.stats.collectAsStateWithLifecycle()
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
    val responsiveVSpacing = (screenHeight.value * 0.035f).coerceIn(20f, 36f).dp
    val smallSpacer = (screenHeight.value * 0.015f).coerceIn(8f, 20f).dp
    val mediumSpacer = (screenHeight.value * 0.026f).coerceIn(16f, 28f).dp
    val largeSpacer = (screenHeight.value * 0.042f).coerceIn(28f, 54f).dp

    // Clean, robust Material 3 adaptive measurements for Focus Schedule UI elements
    val schedCardPadding = 16.dp
    val schedTimelineBarHeight = 8.dp
    val schedOuterSpacing = 16.dp
    val schedHeaderTitleSize = 11.sp
    val schedHeaderSubtitleSize = 16.sp
    val schedSectionHeaderSize = 10.sp
    val schedDayAbbrSize = 13.sp
    val schedTotalHoursSize = 12.sp
    val schedItemTitleSize = 15.sp
    val schedItemSubSize = 12.sp

    val schedIconBoxSize = 36.dp
    val schedIconSize = 18.dp
    val schedActionIconBoxSize = 36.dp
    val schedActionIconSize = 16.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
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
            AnimatedEntrance(delayMillis = 50) {
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
            }

            Spacer(modifier = Modifier.height(smallSpacer))

            // --- GOAL ROW CARD ---
            AnimatedEntrance(delayMillis = 150) {
                RowCardItem(
                    label = "Daily Concentration Goal",
                    value = "${(user?.dailyGoal ?: 120) / 60} hours",
                    onClick = {
                        editGoalVal = user?.dailyGoal ?: 120
                        showGoalModal = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(smallSpacer))

            // --- WEEKLY SCHEDULES CARD ---
            AnimatedEntrance(delayMillis = 250) {
                var selectedDayFilter by remember { mutableStateOf("All") }
                var selectedTaskFilter by remember { mutableStateOf("All") }

                val scheduleList = remember(user?.scheduleJson) {
                    Converters().toScheduleList(user?.scheduleJson)
                }

                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Column(modifier = Modifier.padding(schedCardPadding)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "FOCUS SCHEDULER",
                                    fontSize = schedSectionHeaderSize,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.3.sp,
                                    color = FFColors.textMuted
                                )
                                Text(
                                    text = "State-Based Deep Work Calendar",
                                    fontSize = schedHeaderSubtitleSize,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FFColors.textPrimary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                modifier = Modifier
                                    .background(FFColors.orangeBg, RoundedCornerShape(12.dp))
                                    .clickable {
                                        editingSchedule = null
                                        showScheduleModal = true
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Block",
                                        tint = FFColors.orange,
                                        modifier = Modifier.size(schedActionIconSize)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Add Block",
                                        fontSize = schedTotalHoursSize,
                                        fontWeight = FontWeight.Bold,
                                        color = FFColors.orange
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(schedOuterSpacing))

                        // --- WEEKLY INTERACTIVE TIMELINE OVERVIEW ---
                        Text(
                            text = "WEEK AT A GLANCE (CLICK DAY TO FILTER)",
                            fontSize = schedSectionHeaderSize,
                            fontWeight = FontWeight.Bold,
                            color = FFColors.textMuted,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(FFColors.surface, RoundedCornerShape(20.dp))
                                .border(1.dp, FFColors.borderSubtle, RoundedCornerShape(20.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            weekdays.forEach { dayName ->
                                val dayBlocks = scheduleList.filter { it.day.equals(dayName, ignoreCase = true) }
                                val totalMin = dayBlocks.sumOf { 
                                    (timeToMinutes(it.endTime) - timeToMinutes(it.startTime)).coerceAtLeast(0)
                                }
                                val isSelectedDay = selectedDayFilter.equals(dayName, ignoreCase = true)
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedDayFilter = if (isSelectedDay) "All" else dayName
                                        }
                                        .background(if (isSelectedDay) FFColors.surfaceAlt else Color.Transparent)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Day Abbreviation
                                    Text(
                                        text = dayName.take(3),
                                        fontSize = schedDayAbbrSize,
                                        fontWeight = if (isSelectedDay) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelectedDay) FFColors.orange else FFColors.textPrimary,
                                        modifier = Modifier.width(36.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Interactive Proportional Bar
                                    BoxWithConstraints(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(schedTimelineBarHeight)
                                            .background(FFColors.surfaceAlt, RoundedCornerShape(4.dp))
                                    ) {
                                        val widthPx = constraints.maxWidth.toFloat()
                                        val density = androidx.compose.ui.platform.LocalDensity.current
                                        
                                        dayBlocks.forEach { block ->
                                            val startMin = timeToMinutes(block.startTime)
                                            val endMin = timeToMinutes(block.endTime)
                                            val duration = (endMin - startMin).coerceAtLeast(30)
                                            
                                            val startPercent = startMin.toFloat() / 1440f
                                            val widthPercent = duration.toFloat() / 1440f
                                            val color = getTaskColor(block.task)
                                            
                                            val blockWidthDp = with(density) { (widthPercent * widthPx).toDp() }
                                            val blockOffsetDp = with(density) { (startPercent * widthPx).toDp() }
                                            
                                            Box(
                                                modifier = Modifier
                                                    .height(schedTimelineBarHeight)
                                                    .width(blockWidthDp)
                                                    .offset(x = blockOffsetDp)
                                                    .background(color, RoundedCornerShape(4.dp))
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Total Focus Hours Text
                                    Text(
                                        text = if (totalMin > 0) {
                                            val h = totalMin / 60
                                            val m = totalMin % 60
                                            if (h > 0) "${h}h" else "${m}m"
                                        } else "—",
                                        fontSize = schedTotalHoursSize,
                                        fontWeight = FontWeight.Bold,
                                        color = if (totalMin > 0) FFColors.textPrimary else FFColors.textDisabled,
                                        modifier = Modifier.width(32.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(schedOuterSpacing))

                        // --- DOUBLE TIER FILTER CHIPS ---
                        Text(
                            text = "FILTER SCHEDULE STATE",
                            fontSize = schedSectionHeaderSize,
                            fontWeight = FontWeight.Bold,
                            color = FFColors.textMuted,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        // 1. Day Filter Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val dayFilters = listOf("All") + weekdays
                            dayFilters.forEach { f ->
                                val active = selectedDayFilter == f
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (active) FFColors.orange else FFColors.surface,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (active) Color.Transparent else FFColors.borderSubtle,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedDayFilter = f }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = f,
                                        fontSize = schedTotalHoursSize,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color.White else FFColors.textPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 2. Task Filter Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val taskFilters = listOf("All", "Deep Work", "Coding", "Reading", "Writing", "Creative", "Meditation")
                            taskFilters.forEach { f ->
                                val active = selectedTaskFilter == f
                                val filterColor = if (f == "All") FFColors.textPrimary else getTaskColor(f)
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (active) filterColor.copy(alpha = 0.15f) else FFColors.surface,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (active) filterColor else FFColors.borderSubtle,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedTaskFilter = f }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (f != "All") {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(getTaskColor(f), CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = f,
                                            fontSize = schedTotalHoursSize,
                                            fontWeight = FontWeight.Bold,
                                            color = if (active) filterColor else FFColors.textPrimary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(schedOuterSpacing))

                        // --- RENDER SCHEDULE LIST STATE ---
                        val filteredList = remember(scheduleList, selectedDayFilter, selectedTaskFilter) {
                            scheduleList.filter { entry ->
                                val matchesDay = selectedDayFilter == "All" || entry.day.equals(selectedDayFilter, ignoreCase = true)
                                val matchesTask = selectedTaskFilter == "All" || {
                                    if (selectedTaskFilter == "Deep Work") {
                                        entry.task.contains("Deep Work", ignoreCase = true) || entry.task.isEmpty()
                                    } else {
                                        entry.task.contains(selectedTaskFilter, ignoreCase = true)
                                    }
                                }()
                                matchesDay && matchesTask
                            }
                        }

                        if (filteredList.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Empty",
                                    tint = FFColors.textDisabled,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No focus blocks match current state filter.",
                                    color = FFColors.textDisabled,
                                    fontSize = schedTotalHoursSize,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                filteredList.forEach { schedule ->
                                    val blockColor = getTaskColor(schedule.task)
                                    val blockIcon = getTaskIcon(schedule.task)
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(FFColors.surface, RoundedCornerShape(16.dp))
                                            .border(BorderStroke(1.dp, blockColor.copy(alpha = 0.3f)), RoundedCornerShape(16.dp))
                                            .clickable {
                                                editingSchedule = schedule
                                                showScheduleModal = true
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // Task Representing Icon
                                            Box(
                                                modifier = Modifier
                                                    .size(schedIconBoxSize)
                                                    .background(blockColor.copy(alpha = 0.15f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = blockIcon,
                                                    contentDescription = schedule.task,
                                                    tint = blockColor,
                                                    modifier = Modifier.size(schedIconSize)
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.width(8.dp))

                                            Column {
                                                Text(
                                                    text = schedule.task,
                                                    color = FFColors.textPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = schedItemTitleSize
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(top = 1.dp)
                                                ) {
                                                    Text(
                                                        text = schedule.day,
                                                        color = FFColors.orange,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = schedItemSubSize
                                                    )
                                                    Text(
                                                        text = " • ",
                                                        color = FFColors.textDisabled,
                                                        fontSize = schedItemSubSize
                                                    )
                                                    Text(
                                                        text = "${schedule.startTime} - ${schedule.endTime}",
                                                        color = FFColors.textSecondary,
                                                        fontSize = schedItemSubSize
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    editingSchedule = schedule
                                                    showScheduleModal = true
                                                },
                                                modifier = Modifier.size(schedActionIconBoxSize)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Block",
                                                    tint = FFColors.textSecondary,
                                                    modifier = Modifier.size(schedActionIconSize)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(2.dp))
                                            IconButton(
                                                onClick = { viewModel.deleteScheduleBlock(schedule) },
                                                modifier = Modifier.size(schedActionIconBoxSize)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = FFColors.red,
                                                    modifier = Modifier.size(schedActionIconSize)
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

            Spacer(modifier = Modifier.height(smallSpacer))

            // --- SETTINGS TOGGLES CARD ---
            AnimatedEntrance(delayMillis = 350) {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp)
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

                        Divider(
                            color = FFColors.borderSubtle,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )

                        Text(
                            text = "BROWSER NOTIFICATIONS",
                            fontSize = (10 * baseScale).sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.3.sp,
                            color = FFColors.textMuted,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(FFColors.surfaceAlt, RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Laptop,
                                contentDescription = "Browser Companion",
                                tint = FFColors.blue,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Desktop Browser Alerts",
                                    fontSize = 14.sp,
                                    color = FFColors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "To receive real-time alerts on your computer screen when a session starts, completes, pauses, or cancels:\n\n1. Open http://localhost:8082 in your computer's browser.\n2. Click 'Enable Browser Notifications'.\n3. Keep that tab open to listen to your session in real time.",
                                    fontSize = 12.sp,
                                    color = FFColors.textSecondary,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(smallSpacer))

            // --- DANGER ZONE CARD ---
            AnimatedEntrance(delayMillis = 450) {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp)
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

    // Daily Goal Step Modal (Premium Settings Panel)
    if (showGoalModal) {
        Dialog(
            onDismissRequest = { showGoalModal = false }
        ) {
            GoalSettingsPanel(
                user = user,
                stats = stats,
                onDismiss = { showGoalModal = false },
                onApply = { newGoal ->
                    viewModel.updateDailyGoal(newGoal)
                    showGoalModal = false
                }
            )
        }
    }

    // Focus Schedule ADD/EDIT Modal
    if (showScheduleModal) {
        val isEditing = editingSchedule != null
        val initialDay = editingSchedule?.day ?: "Monday"
        val initialTask = editingSchedule?.task ?: "Deep Work"

        val startParsed = remember(editingSchedule) {
            parseTimeString(editingSchedule?.startTime ?: "09:00 AM")
        }
        val endParsed = remember(editingSchedule) {
            parseTimeString(editingSchedule?.endTime ?: "05:00 PM")
        }

        var selectedDay by remember(editingSchedule) { mutableStateOf(initialDay) }
        var selectedTask by remember(editingSchedule) { mutableStateOf(initialTask) }

        var startHour by remember(editingSchedule) { mutableStateOf(startParsed.first) }
        var startMinute by remember(editingSchedule) { mutableStateOf(startParsed.second) }
        var startAmPm by remember(editingSchedule) { mutableStateOf(if (startParsed.third) "PM" else "AM") }

        var endHour by remember(editingSchedule) { mutableStateOf(endParsed.first) }
        var endMinute by remember(editingSchedule) { mutableStateOf(endParsed.second) }
        var endAmPm by remember(editingSchedule) { mutableStateOf(if (endParsed.third) "PM" else "AM") }

        // State-Based Real-time Overlap Conflict Checker
        val conflictBlock = remember(selectedDay, startHour, startMinute, startAmPm, endHour, endMinute, endAmPm, user?.scheduleJson) {
            val list = Converters().toScheduleList(user?.scheduleJson)
            val finalStartH = startHour.padStart(2, '0').ifEmpty { "12" }
            val finalStartM = startMinute.padStart(2, '0').ifEmpty { "00" }
            val finalEndH = endHour.padStart(2, '0').ifEmpty { "12" }
            val finalEndM = endMinute.padStart(2, '0').ifEmpty { "00" }
            
            val startMin = timeToMinutes("$finalStartH:$finalStartM $startAmPm")
            val endMin = timeToMinutes("$finalEndH:$finalEndM $endAmPm")
            
            if (startMin >= endMin || startHour.isEmpty() || endHour.isEmpty()) {
                null
            } else {
                list.find { block ->
                    // Skip checking self in edit mode
                    val isCurrentEditing = isEditing && 
                                           editingSchedule?.day == block.day && 
                                           editingSchedule?.startTime == block.startTime && 
                                           editingSchedule?.endTime == block.endTime
                    
                    if (isCurrentEditing) {
                        false
                    } else {
                        block.day.equals(selectedDay, ignoreCase = true) && {
                            val bStart = timeToMinutes(block.startTime)
                            val bEnd = timeToMinutes(block.endTime)
                            startMin < bEnd && endMin > bStart
                        }()
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = {
                showScheduleModal = false
                editingSchedule = null
            },
            containerColor = FFColors.surface,
            title = {
                Text(
                    text = if (isEditing) "Edit Focus Block" else "Schedule Focus Block",
                    color = FFColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                                        color = if (active) FFColors.orange else FFColors.surfaceAlt,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedDay = day }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = day,
                                    fontSize = 11.sp,
                                    color = if (active) Color.White else FFColors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text("FOCUS TASK CATEGORY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FFColors.textMuted, letterSpacing = 1.2.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val presets = listOf(
                        Pair("Deep Work", Color(0xFFF97316)),
                        Pair("Coding / Dev", Color(0xFF3B82F6)),
                        Pair("Reading / Study", Color(0xFFA855F7)),
                        Pair("Writing / Docs", Color(0xFFEAB308)),
                        Pair("Creative / Art", Color(0xFFEC4899)),
                        Pair("Meditation / Zen", Color(0xFF22C55E))
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { (pName, pColor) ->
                            val active = selectedTask == pName
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (active) pColor.copy(alpha = 0.15f) else FFColors.surfaceAlt,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (active) pColor else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedTask = pName }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(pColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = pName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FFColors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    OutlinedTextField(
                        value = selectedTask,
                        onValueChange = { selectedTask = it },
                        label = { Text("CUSTOM FOCUS TASK NAME", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = FFColors.textMuted) },
                        placeholder = { Text("e.g. Design review, Sync call", color = FFColors.textDisabled) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FFColors.orange,
                            unfocusedBorderColor = FFColors.borderSubtle,
                            focusedTextColor = FFColors.textPrimary,
                            unfocusedTextColor = FFColors.textPrimary,
                            focusedLabelColor = FFColors.orange,
                            unfocusedLabelColor = FFColors.textMuted
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

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

                    // Show overlap warning if conflict exists
                    if (conflictBlock != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(FFColors.red.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .border(1.dp, FFColors.red.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Conflict Warning",
                                    tint = FFColors.red,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Conflict: Overlaps with \"${conflictBlock.task}\" (${conflictBlock.startTime} - ${conflictBlock.endTime})",
                                    color = FFColors.red,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                        val taskToSave = selectedTask.trim().ifEmpty { "Deep Work" }

                        showScheduleModal = false
                        if (isEditing) {
                            viewModel.editScheduleBlock(editingSchedule!!, ScheduleEntry(selectedDay, finalStart, finalEnd, taskToSave))
                        } else {
                            viewModel.addScheduleBlock(ScheduleEntry(selectedDay, finalStart, finalEnd, taskToSave))
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

fun timeToMinutes(timeStr: String): Int {
    return try {
        val clean = timeStr.trim().uppercase()
        val isPm = clean.endsWith("PM")
        val isAm = clean.endsWith("AM")
        val timePart = clean.replace("AM", "").replace("PM", "").trim()
        val parts = timePart.split(":")
        var hour = parts[0].toIntOrNull() ?: 12
        val minute = if (parts.size > 1) {
            parts[1].filter { it.isDigit() }.toIntOrNull() ?: 0
        } else 0
        
        if (isPm && hour != 12) hour += 12
        if (isAm && hour == 12) hour = 0
        
        hour * 60 + minute
    } catch (e: Exception) {
        0
    }
}

fun getTaskColor(taskName: String): Color {
    return when (taskName.lowercase()) {
        "coding", "coding / dev", "coding & dev", "development", "dev" -> Color(0xFF3B82F6) // blue
        "reading", "reading / study", "reading & study", "study", "research" -> Color(0xFFA855F7) // purple
        "writing", "writing / docs", "writing & docs" -> Color(0xFFEAB308) // yellow/amber
        "creative", "creative / art", "design" -> Color(0xFFEC4899) // pink
        "meditation", "meditation / zen", "zen", "breathwork" -> Color(0xFF22C55E) // green
        else -> Color(0xFFF97316) // orange for Deep Work
    }
}

fun getTaskIcon(taskName: String): ImageVector {
    return when (taskName.lowercase()) {
        "coding", "coding / dev", "coding & dev", "development", "dev" -> Icons.Default.Code
        "reading", "reading / study", "reading & study", "study", "research" -> Icons.Default.Book
        "writing", "writing / docs", "writing & docs" -> Icons.Default.Edit
        "creative", "creative / art", "design" -> Icons.Default.Palette
        "meditation", "meditation / zen", "zen", "breathwork" -> Icons.Default.Favorite
        else -> Icons.Default.Work
    }
}
