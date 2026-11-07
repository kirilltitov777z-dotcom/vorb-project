package com.vorb.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vorb.vpn.ui.VorbApp
import com.vorb.vpn.ui.theme.VorbTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: VorbViewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            VorbTheme(
                themeMode = state.themeMode,
                dynamicColor = state.dynamicColor,
            ) {
                VorbApp(viewModel)
            }
        }
    }
}
