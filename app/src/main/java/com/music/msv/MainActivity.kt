package com.music.msv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.msv.ui.screen.ViewerScreen
import com.music.msv.ui.theme.MSVTheme
import com.music.msv.viewmodel.ViewerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ViewerViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            MSVTheme(forceDark = uiState.isDarkTheme) {
                ViewerScreen(viewModel = viewModel)
            }
        }
    }
}
