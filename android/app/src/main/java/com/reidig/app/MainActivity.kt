package com.reidig.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.reidig.app.ui.ReidigApp
import com.reidig.app.ui.ReidigTheme
import com.reidig.shared.MockRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val repo = remember { MockRepository(clock = System::currentTimeMillis) }
            ReidigTheme { ReidigApp(repo) }
        }
    }
}
