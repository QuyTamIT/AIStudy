package com.aistudy.aistudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aistudy.aistudy.ui.shell.MainShell
import com.aistudy.aistudy.ui.theme.AIStudyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIStudyTheme {
                MainShell()
            }
        }
    }
}
