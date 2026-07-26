package com.kira.stockscope

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kira.stockscope.ui.common.CrashReportDialog
import com.kira.stockscope.ui.nav.StockScopeNavHost
import com.kira.stockscope.ui.theme.StockScopeTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val crashLog = readAndClearLastCrash()
        setContent {
            StockScopeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StockScopeNavHost(container)
                }
                if (crashLog != null) {
                    CrashReportDialog(crashLog)
                }
            }
        }
    }

    private fun readAndClearLastCrash(): String? {
        val file = File(filesDir, StockScopeApplication.CRASH_LOG_FILE)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull()
        file.delete()
        return text
    }
}
