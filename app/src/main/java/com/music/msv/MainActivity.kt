package com.music.msv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.msv.facer.FaceLog
import com.music.msv.ui.screen.ViewerScreen
import com.music.msv.ui.theme.MSVTheme
import com.music.msv.viewmodel.ViewerViewModel

class MainActivity : ComponentActivity() {

    private val shareIntentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FaceLog.init(this)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        shareIntentState.value = intent
        setContent {
            val viewModel: ViewerViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            val shareIntent by shareIntentState

            LaunchedEffect(shareIntent) {
                shareIntent?.let { viewModel.handleShareIntent(it) }
            }

            MSVTheme(forceDark = uiState.isDarkTheme) {
                ViewerScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shareIntentState.value = intent
    }
}
