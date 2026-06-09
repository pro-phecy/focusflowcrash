package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.launcher.LauncherScreen
import com.example.launcher.FocusViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
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
                }
            }
        }
    }
}
