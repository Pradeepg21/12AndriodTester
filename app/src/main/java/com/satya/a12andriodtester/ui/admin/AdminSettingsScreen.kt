package com.satya.a12andriodtester.ui.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.satya.a12andriodtester.data.AppConfig
import com.satya.a12andriodtester.data.Repository
import com.satya.a12andriodtester.ui.components.GradientHeader
import com.satya.a12andriodtester.ui.components.SectionCard
import com.satya.a12andriodtester.ui.rememberActionRunner
import com.satya.a12andriodtester.ui.theme.Amber
import com.satya.a12andriodtester.ui.theme.Indigo
import com.satya.a12andriodtester.ui.theme.Teal
import kotlinx.coroutines.flow.first

/** Payment and support settings stored in config/app. */
@Composable
fun AdminSettingsScreen(nav: NavController) {
    val runner = rememberActionRunner()
    var config by remember { mutableStateOf<AppConfig?>(null) }
    var upiId by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var replyHours by remember { mutableStateOf("") }
    var supportEmail by remember { mutableStateOf("") }
    var newQr by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) newQr = it }

    LaunchedEffect(Unit) {
        val c = Repository.config().first()
        config = c
        upiId = c.upiId; payee = c.payeeName; amount = c.amount.toString()
        replyHours = c.replyHours.toString(); supportEmail = c.supportEmail
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GradientHeader(title = "Settings", subtitle = "Payment & support", onBack = { nav.popBackStack() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(title = "UPI QR code", icon = Icons.Default.QrCode2, accent = Teal) {
                Box(
                    Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center,
                ) {
                    val model: Any? = newQr ?: config?.qrImageUrl?.takeIf { it.isNotBlank() }
                    if (model != null) AsyncImage(model, "UPI QR", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
                    else Text("No QR uploaded", color = Color.Gray)
                }
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (config?.qrImageUrl.isNullOrBlank() && newQr == null) "Upload QR image" else "Replace QR image") }
            }
            SectionCard(title = "Payment details", icon = Icons.Default.Tune, accent = Amber) {
                OutlinedTextField(upiId, { upiId = it }, label = { Text("UPI ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(payee, { payee = it }, label = { Text("Payee name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    amount, { amount = it.filter(Char::isDigit) }, label = { Text("Amount (₹)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    replyHours, { replyHours = it.filter(Char::isDigit) }, label = { Text("Chat reply time (hours)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(supportEmail, { supportEmail = it }, label = { Text("Support email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Button(
                onClick = {
                    val c = config ?: return@Button
                    runner.run("Settings saved", after = { newQr = null }) {
                        Repository.saveConfig(
                            c.copy(
                                upiId = upiId, payeeName = payee,
                                amount = amount.toLongOrNull() ?: 499,
                                replyHours = replyHours.toLongOrNull() ?: 4,
                                supportEmail = supportEmail,
                            ),
                            newQr,
                        )
                        config = Repository.config().first()
                    }
                },
                enabled = config != null && !runner.busy.value,
                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp),
            ) { Text("Save settings") }
        }
    }
}
