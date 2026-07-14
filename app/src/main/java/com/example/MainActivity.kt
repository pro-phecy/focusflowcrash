package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.launcher.LauncherScreen
import com.example.launcher.FocusViewModel
import com.example.launcher.FocusWebServer
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start Focus Companion Web Server for Browser/Desktop Notifications
        FocusWebServer.start(applicationContext)

        // Schedule periodic background push notifications (using WorkManager) when the app is closed
        try {
            com.example.launcher.NotificationSyncWorker.schedule(applicationContext)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        // Fetch and register FCM registration token for background push notifications
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    if (token != null) {
                        com.example.launcher.FocusFirebaseMessagingService.saveTokenLocally(applicationContext, token)
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        
        // Request POST_NOTIFICATIONS permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        
        // Render behind status and navigation bars seamlessly
        enableEdgeToEdge()
        
        setContent {
            val viewModel: FocusViewModel = viewModel()
            val user by viewModel.user.collectAsStateWithLifecycle()
            val isDarkTheme = user?.darkMode ?: true
            var showSplash by remember { mutableStateOf(true) }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        LauncherScreen(
                            viewModel = viewModel,
                            onToggleStatusBar = { showStatusBar ->
                                try {
                                    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                                    if (showStatusBar) {
                                        windowInsetsController?.show(WindowInsetsCompat.Type.systemBars())
                                        @Suppress("DEPRECATION")
                                        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                                    } else {
                                        windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())
                                        windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                        @Suppress("DEPRECATION")
                                        window.decorView.systemUiVisibility = (
                                            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                                            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                        )
                                    }
                                } catch (e: Throwable) {
                                    e.printStackTrace()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        AnimatedVisibility(
                            visible = showSplash,
                            exit = fadeOut(animationSpec = tween(durationMillis = 600)) + 
                                   slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(durationMillis = 700, easing = LinearOutSlowInEasing))
                        ) {
                            SplashScreen(
                                onDismiss = { showSplash = false }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        FocusWebServer.stop()
    }
}

@Composable
fun SplashScreen(onDismiss: () -> Unit) {
    val scaleAnim = remember { Animatable(0.7f) }
    val alphaAnim = remember { Animatable(0f) }
    val textAlphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scaleAnim.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 1100, easing = LinearOutSlowInEasing)
        )
    }

    LaunchedEffect(Unit) {
        alphaAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
        delay(100)
        textAlphaAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
        delay(1400) // Beautiful lingering dwell duration
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B)), // Sleek Midnight Charcoal Zinc-950
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.focus_icon_fg_1782162046271),
                contentDescription = "Focus Flow Icon",
                modifier = Modifier
                    .size(150.dp)
                    .graphicsLayer(
                        scaleX = scaleAnim.value,
                        scaleY = scaleAnim.value,
                        alpha = alphaAnim.value
                    )
                    .clip(RoundedCornerShape(32.dp))
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(32.dp))
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "FOCUS FLOW",
                fontSize = 26.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFAFAFA),
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .graphicsLayer(alpha = textAlphaAnim.value)
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "YOUR SPACE FOR DEEP WORK",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFF97316), // Premium neon orange branding accent
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .graphicsLayer(alpha = textAlphaAnim.value)
                    .padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Minimal elegant linear load bar
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFF27272A))
                    .graphicsLayer(alpha = textAlphaAnim.value)
            ) {
                var progressWidth by remember { mutableStateOf(0f) }
                LaunchedEffect(Unit) {
                    animate(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 1800, easing = LinearEasing)
                    ) { valValue, _ ->
                        progressWidth = valValue
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressWidth)
                        .background(Color(0xFFF97316))
                )
            }
        }
    }
}
