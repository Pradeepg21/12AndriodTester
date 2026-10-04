package com.satya.a12andriodtester.ui.request

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.satya.a12andriodtester.data.AppConfig
import com.satya.a12andriodtester.data.Repository
import com.satya.a12andriodtester.data.RequestStatus
import com.satya.a12andriodtester.data.TestRequest
import com.satya.a12andriodtester.ui.components.GradientHeader
import com.satya.a12andriodtester.ui.components.Loading
import com.satya.a12andriodtester.ui.components.SectionCard
import com.satya.a12andriodtester.ui.rememberActionRunner
import com.satya.a12andriodtester.ui.theme.Amber
import com.satya.a12andriodtester.ui.theme.BrandGradient
import com.satya.a12andriodtester.ui.theme.Emerald
import com.satya.a12andriodtester.ui.theme.Indigo
import com.satya.a12andriodtester.ui.theme.Pink
import com.satya.a12andriodtester.ui.theme.Sky
import kotlinx.coroutines.launch

/** Manual UPI payment in two steps — SPEC.md §5.6. */
@Composable
fun PaymentScreen(nav: NavController, requestId: String) {
    val req by remember(requestId) { Repository.request(requestId) }.collectAsStateWithLifecycle(null)
    val config by remember { Repository.config() }.collectAsStateWithLifecycle(AppConfig())
    var step by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GradientHeader(
            title = if (step == 0) "Step 1 · Scan & Pay" else "Step 2 · Upload Screenshot",
            subtitle = req?.appName,
            onBack = { if (step == 1) step = 0 else nav.popBackStack() },
        ) {
            Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { i ->
                    Box(
                        Modifier.weight(1f).height(6.dp).clip(CircleShape)
                            .background(if (i <= step) Color.White else Color.White.copy(alpha = 0.3f)),
                    )
                }
            }
        }
        val r = req
        when {
            r == null -> Loading()
            r.status != RequestStatus.PAYMENT_PENDING -> Column(Modifier.padding(16.dp)) {
                SectionCard(title = "Payment already submitted", icon = Icons.Default.Receipt, accent = Emerald) {
                    Text("Current status: ${r.status.label}")
                }
            }
            step == 0 -> ScanAndPay(r, config) { step = 1 }
            else -> UploadScreenshot(r, config) { nav.popBackStack() }
        }
    }
}

@Composable
private fun ScanAndPay(req: TestRequest, config: AppConfig, onNext: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (req.payment.rejectReason.isNotBlank()) {
            SectionCard(title = "Previous payment not verified", icon = Icons.Default.Info, accent = Pink) {
                Text(req.payment.rejectReason)
            }
        }
        SectionCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Amount to pay", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("₹${config.amount}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Indigo)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(3.dp, BrandGradient, RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (config.qrImageUrl.isNotBlank()) {
                        AsyncImage(
                            model = config.qrImageUrl, contentDescription = "UPI QR code",
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.QrCode2, null, tint = Color.Gray, modifier = Modifier.size(80.dp))
                            Text("QR code coming soon", color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (config.upiId.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("UPI ID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(config.upiId, style = MaterialTheme.typography.titleMedium)
                            if (config.payeeName.isNotBlank()) Text(config.payeeName, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("UPI ID", config.upiId))) }
                            Toast.makeText(context, "UPI ID copied", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy UPI ID") }
                    }
                }
            }
        }

        if (config.upiId.isNotBlank()) {
            OutlinedButton(
                onClick = {
                    val uri = Uri.Builder().scheme("upi").authority("pay")
                        .appendQueryParameter("pa", config.upiId)
                        .appendQueryParameter("pn", config.payeeName.ifBlank { "Twelve Testers" })
                        .appendQueryParameter("am", config.amount.toString())
                        .appendQueryParameter("cu", "INR")
                        .appendQueryParameter("tn", "12AT ${req.id.take(6)}")
                        .build()
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        .onFailure { Toast.makeText(context, "No UPI app found. Please scan the QR.", Toast.LENGTH_SHORT).show() }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Pay with a UPI app on this phone") }
        }

        SectionCard(title = "Before you pay", icon = Icons.Default.Info, accent = Amber) {
            Text("• Pay exactly ₹${config.amount} and take a screenshot of the success screen.", style = MaterialTheme.typography.bodyMedium)
            Text("• You can request a refund after payment until testing starts. Once testing begins, refunds are not possible.", style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Indigo),
        ) { Text("I have paid → Upload screenshot") }
    }
}

@Composable
private fun UploadScreenshot(req: TestRequest, config: AppConfig, onDone: () -> Unit) {
    val runner = rememberActionRunner()
    var image by rememberSaveable { mutableStateOf<String?>(null) }
    var utr by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) image = uri.toString()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(title = "Payment screenshot", icon = Icons.Default.AddPhotoAlternate, accent = Sky) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    AsyncImage(image, "Selected screenshot", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddPhotoAlternate, null, tint = Sky, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Tap to choose the screenshot")
                    }
                }
            }
            if (image != null) {
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Choose a different image") }
            }
        }
        OutlinedTextField(
            utr, { utr = it },
            label = { Text("UTR / Transaction ID (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Our team will verify your payment and move your request to the next step. You will be notified.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Button(
            onClick = {
                val uri = image?.toUri() ?: return@Button
                runner.run("Payment submitted for verification", after = onDone) {
                    Repository.submitPayment(req, uri, utr, config.amount)
                }
            },
            enabled = image != null && !runner.busy.value,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald),
        ) {
            if (runner.busy.value) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Submit payment")
        }
    }
}
