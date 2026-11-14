package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AdwaApp
import com.example.ui.theme.AdwaCyberTheme
import com.example.viewmodel.AdwaViewModel

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AdwaViewModel = viewModel()
            val ui by vm.ui.collectAsStateWithLifecycle()
            val windowSizeClass = calculateWindowSizeClass(this)
            val isDark = ui.darkModeOverride ?: isSystemInDarkTheme()

            AdwaCyberTheme(darkTheme = isDark) {
                AdwaApp(vm = vm, widthSizeClass = windowSizeClass.widthSizeClass)
            }
        }
    }
}
