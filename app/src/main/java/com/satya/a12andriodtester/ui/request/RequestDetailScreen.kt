package com.satya.a12andriodtester.ui.request

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.satya.a12andriodtester.data.Repository
import com.satya.a12andriodtester.data.RequestStatus
import com.satya.a12andriodtester.data.RequestStatus.*
import com.satya.a12andriodtester.data.Session
import com.satya.a12andriodtester.data.TestRequest
import com.satya.a12andriodtester.ui.Routes
import com.satya.a12andriodtester.ui.components.ConfirmDialog
import com.satya.a12andriodtester.ui.components.GradientHeader
import com.satya.a12andriodtester.ui.components.InfoRow
import com.satya.a12andriodtester.ui.components.Loading
import com.satya.a12andriodtester.ui.components.SectionCard
import com.satya.a12andriodtester.ui.components.StatusChip
import com.satya.a12andriodtester.ui.components.TextInputDialog
import com.satya.a12andriodtester.ui.components.WorkflowStepper
import com.satya.a12andriodtester.ui.components.pretty
import com.satya.a12andriodtester.ui.openUrl
import com.satya.a12andriodtester.ui.openWhatsApp
import com.satya.a12andriodtester.ui.rememberActionRunner
import com.satya.a12andriodtester.ui.theme.Amber
import com.satya.a12andriodtester.ui.theme.Emerald
import com.satya.a12andriodtester.ui.theme.Indigo
import com.satya.a12andriodtester.ui.theme.Pink
import com.satya.a12andriodtester.ui.theme.Rose
import com.satya.a12andriodtester.ui.theme.Sky
import com.satya.a12andriodtester.ui.theme.Slate
import com.satya.a12andriodtester.ui.theme.Teal
import com.satya.a12andriodtester.ui.theme.Violet
import com.satya.a12andriodtester.ui.theme.statusColor

private val finalStatuses = setOf(COMPLETED, REFUNDED, CANCELLED, REJECTED)

/** Dialogs opened from action buttons. */
private enum class DialogKind { CANCEL, REFUND, CONFIRM_EMAILS, REJECT_PAYMENT, NEEDS_INFO, HOLD, REJECT, APPROVE_REFUND, DECLINE_REFUND, COMPLETE }

@Composable
fun RequestDetailScreen(nav: NavController, session: Session, requestId: String) {
    val req by remember(requestId) { Repository.request(requestId) }.collectAsStateWithLifecycle(null)
    val admin = session.isAdmin

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val r = req
        GradientHeader(
            title = r?.appName ?: "Request",
            subtitle = r?.packageName,
            onBack = { nav.popBackStack() },
            actions = {
                if (r != null) {
                    Box {
                        IconButton(onClick = { nav.navigate(Routes.chat(r.id)) }) {
                            Icon(Icons.AutoMirrored.Filled.Chat, "Chat", tint = Color.White)
                        }
                        if (r.unreadFor(admin)) Badge(Modifier.align(Alignment.TopEnd).padding(6.dp), containerColor = Rose)
                    }
                }
            },
        )
        if (r == null) {
            Loading()
            return
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusCard(r, admin)
            if (admin) AdminActions(r) else UserActions(nav, r)
            ChatShortcut(r, admin) { nav.navigate(Routes.chat(r.id)) }
            SectionCard(title = "Progress", icon = Icons.Default.Timeline, accent = Violet) {
                WorkflowStepper(r.effectiveStep, r.status)
            }
            ReportCard(r)
            DetailsCard(r, admin)
            PaymentCard(r, admin)
            RefundCard(r)
            if (admin) AdminNotesCard(r)
            HistoryCard(r)
        }
    }
}

@Composable
private fun StatusCard(r: TestRequest, admin: Boolean) {
    val color = statusColor(r.status)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(r.status)
            Spacer(Modifier.weight(1f))
            r.testingDay?.let { Text("Day $it of 14", color = color, fontWeight = FontWeight.Bold) }
        }
        Text(if (admin) r.status.adminHint else r.status.userHint, style = MaterialTheme.typography.bodyLarge)
        if (r.statusNote.isNotBlank() && r.status !in setOf(PAYMENT_PENDING, PAYMENT_SUBMITTED)) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.1f)).padding(12.dp),
            ) { Text("Note: ${r.statusNote}", style = MaterialTheme.typography.bodyMedium) }
        }
        if (r.testingDay != null) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { r.testingDay!! / 14f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = color,
                trackColor = color.copy(alpha = 0.15f),
            )
        }
    }
}

// ---------------- User actions ----------------

@Composable
private fun UserActions(nav: NavController, r: TestRequest) {
    val runner = rememberActionRunner()
    var dialog by remember { mutableStateOf<DialogKind?>(null) }
    val hasAny = r.status in setOf(SUBMITTED, PAYMENT_PENDING, EMAILS_SHARED, NEEDS_INFO) || r.refundAllowed

    if (hasAny) {
        SectionCard(title = "What to do next", icon = Icons.Default.TouchApp, accent = Indigo) {
            when (r.status) {
                SUBMITTED, PAYMENT_PENDING -> {
                    PrimaryButton("Pay now", Indigo) { nav.navigate(Routes.pay(r.id)) }
                    OutlinedButton(onClick = { nav.navigate(Routes.edit(r.id)) }, modifier = Modifier.fillMaxWidth()) { Text("Edit details") }
                    OutlinedButton(onClick = { dialog = DialogKind.CANCEL }, modifier = Modifier.fillMaxWidth()) { Text("Cancel request") }
                }
                EMAILS_SHARED -> {
                    Text(
                        "Check your WhatsApp (${r.whatsapp}) for the 12 tester emails. Add all of them to your closed test in Play Console, save, then confirm here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PrimaryButton("I have added all 12 emails", Emerald) { dialog = DialogKind.CONFIRM_EMAILS }
                }
                NEEDS_INFO -> PrimaryButton("Edit & resubmit", Pink) { nav.navigate(Routes.edit(r.id)) }
                else -> Unit
            }
            if (r.refundAllowed) {
                Text(
                    "Refund is available until testing starts. Once testing begins, refunds are not possible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { dialog = DialogKind.REFUND }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Replay, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Request refund")
                }
            }
        }
    }

    when (dialog) {
        DialogKind.CANCEL -> ConfirmDialog(
            "Cancel request?", "This request will be closed. You can submit a new one anytime.", "Cancel request",
            onDismiss = { dialog = null },
        ) { dialog = null; runner.run("Request cancelled") { Repository.cancel(r) } }
        DialogKind.CONFIRM_EMAILS -> ConfirmDialog(
            "Confirm emails added",
            "Please confirm all 12 tester emails are added to your closed test and the changes are saved in Play Console.",
            "Yes, all added",
            onDismiss = { dialog = null },
        ) { dialog = null; runner.run("Thanks! We'll start testing soon.") { Repository.confirmEmailsAdded(r) } }
        DialogKind.REFUND -> TextInputDialog(
            "Request refund", "Reason", "Submit",
            message = "Our team will review your request. Refunds are not possible after testing starts.",
            onDismiss = { dialog = null },
        ) { dialog = null; runner.run("Refund requested") { Repository.requestRefund(r, it) } }
        else -> Unit
    }
}

// ---------------- Admin actions ----------------

@Composable
private fun AdminActions(r: TestRequest) {
    val runner = rememberActionRunner()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<DialogKind?>(null) }
    val reportPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runner.run("Report uploaded") { Repository.uploadReport(r, uri) }
    }

    SectionCard(title = "Admin actions", icon = Icons.Default.AdminPanelSettings, accent = Violet) {
        when (r.status) {
            PAYMENT_SUBMITTED -> {
                PrimaryButton("Verify payment", Emerald) { runner.run("Payment verified") { Repository.adminAdvance(r, PAYMENT_VERIFIED, "Payment verified") } }
                OutlinedButton(onClick = { dialog = DialogKind.REJECT_PAYMENT }, modifier = Modifier.fillMaxWidth()) { Text("Payment not received") }
            }
            EMAILS_SHARED -> {
                Text("Waiting for the user to add the 12 emails.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = { runner.run("Marked as added") { Repository.adminAdvance(r, EMAILS_ADDED, "Marked as added by admin") } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Mark emails added (on behalf of user)") }
            }
            TESTING_IN_PROGRESS, REPORT_READY -> {
                PrimaryButton(if (r.reportUrl.isBlank()) "Upload final report (PDF)" else "Replace report (PDF)", Teal) {
                    reportPicker.launch("application/pdf")
                }
                if (r.status == REPORT_READY) PrimaryButton("Mark completed", Emerald) { dialog = DialogKind.COMPLETE }
            }
            REFUND_REQUESTED -> {
                PrimaryButton("Approve refund", Emerald) { dialog = DialogKind.APPROVE_REFUND }
                OutlinedButton(onClick = { dialog = DialogKind.DECLINE_REFUND }, modifier = Modifier.fillMaxWidth()) { Text("Decline refund") }
            }
            NEEDS_INFO, ON_HOLD -> PrimaryButton("Resume (${r.resumeStatus?.label ?: "—"})", Indigo) {
                runner.run("Resumed") { Repository.resume(r) }
            }
            else -> Unit
        }
        RequestStatus.adminNext[r.status]?.let { next ->
            PrimaryButton("Move to: ${next.label}", statusColor(next)) {
                runner.run("Moved to ${next.label}") { Repository.adminAdvance(r, next) }
            }
        }
        if (r.status == INTERNAL_VERIFICATION || r.status == EMAILS_SHARED) {
            OutlinedButton(onClick = { context.openWhatsApp(r.whatsapp) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open WhatsApp chat (${r.whatsapp})")
            }
        }
        if (r.status !in finalStatuses && r.status !in setOf(NEEDS_INFO, ON_HOLD, REFUND_REQUESTED)) {
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { dialog = DialogKind.NEEDS_INFO }, modifier = Modifier.weight(1f)) { Text("Needs info") }
                OutlinedButton(onClick = { dialog = DialogKind.HOLD }, modifier = Modifier.weight(1f)) { Text("Hold") }
                OutlinedButton(onClick = { dialog = DialogKind.REJECT }, modifier = Modifier.weight(1f)) { Text("Reject", color = Rose) }
            }
        }
        if (r.status in finalStatuses) {
            Text("No further actions — this request is closed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    val close = { dialog = null }
    when (dialog) {
        DialogKind.REJECT_PAYMENT -> TextInputDialog("Payment not received", "Reason shown to user", "Send back", onDismiss = close) {
            close(); runner.run("Sent back to payment") { Repository.rejectPayment(r, it) }
        }
        DialogKind.NEEDS_INFO -> TextInputDialog("Needs information", "What should the user fix?", "Send", onDismiss = close) {
            close(); runner.run("User asked for info") { Repository.pause(r, NEEDS_INFO, it) }
        }
        DialogKind.HOLD -> TextInputDialog("Put on hold", "Reason", "Hold", onDismiss = close) {
            close(); runner.run("On hold") { Repository.pause(r, ON_HOLD, it) }
        }
        DialogKind.REJECT -> TextInputDialog("Reject request", "Reason shown to user", "Reject", onDismiss = close) {
            close(); runner.run("Rejected") { Repository.reject(r, it) }
        }
        DialogKind.APPROVE_REFUND -> TextInputDialog("Approve refund", "Refund reference / UTR", "Mark refunded", onDismiss = close) {
            close(); runner.run("Marked refunded") { Repository.approveRefund(r, it) }
        }
        DialogKind.DECLINE_REFUND -> TextInputDialog("Decline refund", "Reason shown to user", "Decline", onDismiss = close) {
            close(); runner.run("Refund declined") { Repository.declineRefund(r, it) }
        }
        DialogKind.COMPLETE -> ConfirmDialog(
            "Mark completed?", "The user will see this request as completed with the final report.", "Complete",
            onDismiss = close,
        ) { close(); runner.run("Completed 🎉") { Repository.adminAdvance(r, COMPLETED, "Closed testing completed") } }
        else -> Unit
    }
}

@Composable
private fun PrimaryButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) { Text(text) }
}

// ---------------- Info cards ----------------

@Composable
private fun ChatShortcut(r: TestRequest, admin: Boolean, onClick: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onClick), title = "Chat with ${if (admin) "user" else "support"}", icon = Icons.AutoMirrored.Filled.Chat, accent = Sky) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (r.unreadFor(admin)) "You have a new message" else "Ask anything about this request",
                modifier = Modifier.weight(1f),
                fontWeight = if (r.unreadFor(admin)) FontWeight.Bold else FontWeight.Normal,
            )
            Icon(Icons.AutoMirrored.Filled.Send, null, tint = Sky)
        }
    }
}

@Composable
private fun ReportCard(r: TestRequest) {
    if (r.reportUrl.isBlank()) return
    val context = LocalContext.current
    SectionCard(title = "Final report", icon = Icons.Default.Description, accent = Teal) {
        Text("Uploaded ${r.reportUploadedAt.pretty()}", style = MaterialTheme.typography.bodySmall)
        Text("Includes testing summary, issues found and suggestions to get production-ready.", style = MaterialTheme.typography.bodyMedium)
        PrimaryButton("View / download report", Teal) { context.openUrl(r.reportUrl) }
    }
}

@Composable
private fun DetailsCard(r: TestRequest, admin: Boolean) {
    val context = LocalContext.current
    SectionCard(title = "Request details", icon = Icons.Default.Apps, accent = Indigo) {
        InfoRow("Full name", r.fullName)
        InfoRow("WhatsApp", r.whatsapp)
        InfoRow("Contact email", r.contactEmail)
        InfoRow("App name", r.appName)
        InfoRow("Package name", r.packageName)
        if (r.optInLink.isNotBlank()) {
            Column(Modifier.clickable { context.openUrl(r.optInLink) }) {
                Text("Opt-in link", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(r.optInLink, color = Indigo, style = MaterialTheme.typography.bodyMedium)
            }
        }
        InfoRow("Category", r.category)
        InfoRow("Notes", r.notes)
        InfoRow("Created", r.createdAt.pretty())
        if (admin) InfoRow("Request ID", r.id)
    }
}

@Composable
private fun PaymentCard(r: TestRequest, admin: Boolean) {
    if (r.payment.screenshotUrl.isBlank()) return
    val context = LocalContext.current
    SectionCard(title = "Payment", icon = Icons.Default.Payments, accent = Amber) {
        InfoRow("Amount", "₹${r.payment.amount}")
        InfoRow("UTR / Transaction ID", r.payment.utr)
        InfoRow("Submitted", r.payment.submittedAt.pretty())
        if (r.payment.verifiedAt != null) InfoRow("Verified", r.payment.verifiedAt.pretty())
        if (admin || r.status == PAYMENT_SUBMITTED) {
            AsyncImage(
                r.payment.screenshotUrl, "Payment screenshot",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { context.openUrl(r.payment.screenshotUrl) },
            )
        }
    }
}

@Composable
private fun RefundCard(r: TestRequest) {
    val refund = r.refund ?: return
    SectionCard(title = "Refund", icon = Icons.Default.Replay, accent = Pink) {
        InfoRow("Reason", refund.reason)
        InfoRow("Requested", refund.requestedAt.pretty())
        InfoRow("Refund reference", refund.reference)
        InfoRow("Declined because", refund.declineReason)
        if (refund.processedAt != null) InfoRow("Processed", refund.processedAt.pretty())
    }
}

@Composable
private fun AdminNotesCard(r: TestRequest) {
    val notes by remember(r.id) { Repository.adminNotes(r.id) }.collectAsStateWithLifecycle(null)
    val runner = rememberActionRunner()
    var text by remember { mutableStateOf("") }
    SectionCard(title = "Internal notes (admin only)", icon = Icons.Default.StickyNote2, accent = Slate) {
        notes.orEmpty().forEach {
            Column {
                Text(it.text, style = MaterialTheme.typography.bodyMedium)
                Text(it.createdAt.pretty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, label = { Text("Add note") }, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { val t = text; runner.run(after = { text = "" }) { Repository.addAdminNote(r.id, t) } },
                enabled = text.isNotBlank(),
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Add note") }
        }
    }
}

@Composable
private fun HistoryCard(r: TestRequest) {
    SectionCard(title = "History", icon = Icons.Default.History, accent = Slate) {
        r.history.asReversed().forEach { e ->
            Row {
                Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(statusColor(e.status)))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(e.status.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${e.at.pretty()} • by ${e.byRole}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
