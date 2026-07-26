package com.kira.stockscope.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Shown once, right after a crash, with the stack trace as copyable text. */
@Composable
fun CrashReportDialog(crashLog: String) {
    var visible by remember { mutableStateOf(true) }
    if (!visible) return

    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { visible = false },
        title = { Text("StockScope crashed last time") },
        text = {
            Column {
                Text(
                    "Copy this and share it so the crash can be fixed:",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.size(8.dp))
                Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(
                            crashLog,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(crashLog))
                visible = false
            }) { Text("Copy & close") }
        },
        dismissButton = {
            TextButton(onClick = { visible = false }) { Text("Dismiss") }
        }
    )
}
