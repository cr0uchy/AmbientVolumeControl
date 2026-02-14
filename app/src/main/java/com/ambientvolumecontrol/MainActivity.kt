package com.ambientvolumecontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ambientvolumecontrol.ui.screens.MainScreen
import com.ambientvolumecontrol.ui.theme.AmbientVolumeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmbientVolumeTheme {
                MainScreen()
            }
        }
    }
}
