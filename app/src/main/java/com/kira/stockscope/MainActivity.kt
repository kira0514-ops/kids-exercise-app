package com.kira.stockscope

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kira.stockscope.ui.nav.StockScopeNavHost
import com.kira.stockscope.ui.theme.StockScopeTheme

class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StockScopeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StockScopeNavHost(container)
                }
            }
        }
    }
}
