package com.satya.a12andriodtester

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.satya.a12andriodtester.data.AuthRepository
import com.satya.a12andriodtester.data.SessionState
import com.satya.a12andriodtester.ui.AppRoot
import com.satya.a12andriodtester.ui.theme.TesterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash shown on every cold start — SPEC.md §5.1.
        val splash = installSplashScreen()
        val start = SystemClock.elapsedRealtime()
        splash.setKeepOnScreenCondition {
            SystemClock.elapsedRealtime() - start < 1200 || AuthRepository.state.value is SessionState.Loading
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val openRequestId = intent?.getStringExtra("requestId")
        setContent {
            TesterTheme {
                val state = AuthRepository.state.collectAsStateWithLifecycle()
                AppRoot(state.value, openRequestId)
            }
        }
    }
}
