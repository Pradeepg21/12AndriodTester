package com.satya.a12andriodtester.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Runs suspend actions with a shared busy flag and a toast on success/failure. */
class ActionRunner(
    private val scope: CoroutineScope,
    private val context: Context,
    val busy: MutableState<Boolean>,
) {
    fun run(success: String? = null, after: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        scope.launch {
            try {
                block()
                success?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                after()
            } catch (e: Exception) {
                Log.e("ActionRunner", "Action failed", e)
                Toast.makeText(context, e.message ?: "Something went wrong", Toast.LENGTH_LONG).show()
            } finally {
                busy.value = false
            }
        }
    }
}

@Composable
fun rememberActionRunner(): ActionRunner {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val busy = remember { mutableStateOf(false) }
    return remember { ActionRunner(scope, context, busy) }
}

fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        .onFailure { Toast.makeText(this, "No app found to open this link", Toast.LENGTH_SHORT).show() }
}

fun Context.emailTo(address: String, subject: String) {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri()).putExtra(Intent.EXTRA_SUBJECT, subject)
    runCatching { startActivity(intent) }
}

fun Context.openWhatsApp(number: String) {
    val digits = number.filter { it.isDigit() }
    openUrl("https://wa.me/$digits")
}
