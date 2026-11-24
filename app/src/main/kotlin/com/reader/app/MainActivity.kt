package com.reader.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.reader.app.novel.ui.NovelApp
import com.reader.app.ui.theme.NovelReaderTheme
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pendingCrash = installCrashHandler(this)
        val activity = this
        enableEdgeToEdge()
        setContent {
            NovelReaderTheme {
                var crashText by remember { mutableStateOf(pendingCrash) }
                NovelApp()
                val text = crashText
                if (text != null) {
                    AlertDialog(
                        onDismissRequest = {
                            crashText = null
                            clearCrashLog(activity)
                        },
                        title = { Text("上次崩溃信息") },
                        text = {
                            Text(
                                text = text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    copyToClipboard(activity, text)
                                    crashText = null
                                    clearCrashLog(activity)
                                }
                            ) { Text("复制并关闭") }
                        }
                    )
                }
            }
        }
    }
}

/** 安装全局崩溃捕获：把堆栈写入 filesDir/crash.log，下次启动时读取并提示。 */
private fun installCrashHandler(context: Context): String? {
    val crashFile = File(context.filesDir, "crash.log")
    val pending = runCatching { crashFile.readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    val default = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching { crashFile.writeText(stackTraceOf(throwable)) }
        default?.uncaughtException(thread, throwable)
    }
    return pending
}

private fun stackTraceOf(throwable: Throwable): String {
    val sw = StringWriter()
    throwable.printStackTrace(PrintWriter(sw))
    return sw.toString()
}

private fun clearCrashLog(context: Context) {
    runCatching { File(context.filesDir, "crash.log").delete() }
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("crash", text))
    }
}
