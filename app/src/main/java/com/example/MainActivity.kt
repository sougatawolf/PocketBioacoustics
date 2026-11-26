package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.RecorderViewModel
import com.example.ui.WaveLoopAppShell
import com.example.ui.theme.WaveLoopTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RecorderViewModel by viewModels {
        RecorderViewModel.Factory(application as WaveLoopApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaveLoopTheme {
                WaveLoopAppShell(viewModel = viewModel)
            }
        }
    }
}
