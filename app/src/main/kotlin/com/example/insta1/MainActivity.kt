package com.example.insta1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.insta1.ui.ad.AdMobConfig
import com.example.insta1.ui.main.MainScreen
import com.example.insta1.ui.theme.Insta1Theme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    try {
      AdMobConfig.initialize(this)
    } catch (_: Throwable) {}

    try {
      enableEdgeToEdge()
    } catch (_: Throwable) {}

    setContent {
      Insta1Theme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          MainScreen()
        }
      }
    }
  }
}
