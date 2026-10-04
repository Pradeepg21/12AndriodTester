package com.satya.a12andriodtester.ui.request

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.satya.a12andriodtester.data.Repository
import com.satya.a12andriodtester.data.RequestForm
import com.satya.a12andriodtester.data.RequestStatus
import com.satya.a12andriodtester.data.Session
import com.satya.a12andriodtester.data.TestRequest
import com.satya.a12andriodtester.ui.Routes
import com.satya.a12andriodtester.ui.components.GradientHeader
import com.satya.a12andriodtester.ui.components.SectionCard
import com.satya.a12andriodtester.ui.rememberActionRunner
import com.satya.a12andriodtester.ui.theme.Indigo
import com.satya.a12andriodtester.ui.theme.Pink
import com.satya.a12andriodtester.ui.theme.Teal
import kotlinx.coroutines.flow.first

private val packageRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
private val phoneRegex = Regex("^\\+[0-9]{10,15}$")

fun normalizePhone(raw: String) = raw.filter { it.isDigit() || it == '+' }
fun isValidWhatsApp(raw: String) = phoneRegex.matches(normalizePhone(raw))
fun isValidPackage(raw: String) = packageRegex.matches(raw.trim())
fun isValidUrl(raw: String) = raw.trim().let { it.startsWith("https://") && android.util.Patterns.WEB_URL.matcher(it).matches() }

/** New request form, also used to edit/resubmit — SPEC.md §5.5. */
@Composable
fun RequestFormScreen(nav: NavController, session: Session, requestId: String?) {
    val runner = rememberActionRunner()
    var existing by remember { mutableStateOf<TestRequest?>(null) }
    var form by rememberSaveable(stateSaver = formSaver) { mutableStateOf(RequestForm(contactEmail = session.email)) }
    var loaded by remember { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(requestId != null) }

    LaunchedEffect(requestId) {
        if (loaded) return@LaunchedEffect
        if (requestId != null) {
            Repository.request(requestId).first()?.let {
                existing = it
                if (form.appName.isBlank()) form = RequestForm.from(it)
            }
        } else if (form.fullName.isBlank()) {
            val profile = Repository.user(session.uid).first()
            form = form.copy(
                fullName = profile?.name?.ifBlank { session.displayName } ?: session.displayName,
                whatsapp = profile?.whatsapp.orEmpty(),
            )
        }
        loaded = true
    }

    val errors = mapOf(
        "fullName" to (form.fullName.isBlank()),
        "whatsapp" to !isValidWhatsApp(form.whatsapp),
        "contactEmail" to !android.util.Patterns.EMAIL_ADDRESS.matcher(form.contactEmail.trim()).matches(),
        "appName" to form.appName.isBlank(),
        "packageName" to !isValidPackage(form.packageName),
        "optInLink" to !isValidUrl(form.optInLink),
    )
    var showErrors by remember { mutableStateOf(false) }
    val resubmit = existing?.status == RequestStatus.NEEDS_INFO

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GradientHeader(
            title = if (requestId == null) "New Request" else if (resubmit) "Update & Resubmit" else "Edit Request",
            subtitle = "Closed testing details",
            onBack = { nav.popBackStack() },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (resubmit && existing?.statusNote?.isNotBlank() == true) {
                SectionCard(title = "What we need from you", accent = Pink) {
                    Text(existing!!.statusNote)
                }
            }
            SectionCard(title = "Your details", icon = Icons.Default.Person, accent = Indigo) {
                Field("Full name", form.fullName, Icons.Default.Badge, showErrors && errors["fullName"] == true) {
                    form = form.copy(fullName = it)
                }
                Field(
                    "WhatsApp number (with country code)", form.whatsapp, Icons.Default.Phone,
                    showErrors && errors["whatsapp"] == true, KeyboardType.Phone,
                    help = "e.g. +919876543210 — we will send the 12 tester emails here",
                ) { form = form.copy(whatsapp = it) }
                Field(
                    "Contact email", form.contactEmail, Icons.Default.Email,
                    showErrors && errors["contactEmail"] == true, KeyboardType.Email,
                ) { form = form.copy(contactEmail = it) }
            }
            SectionCard(title = "Your app", icon = Icons.Default.Apps, accent = Teal) {
                Field("App name", form.appName, Icons.Default.Apps, showErrors && errors["appName"] == true) {
                    form = form.copy(appName = it)
                }
                Field(
                    "Package name", form.packageName, Icons.Default.Apps,
                    showErrors && errors["packageName"] == true, KeyboardType.Uri,
                    help = "e.g. com.example.myapp",
                ) { form = form.copy(packageName = it) }
                Field(
                    "Closed testing opt-in link", form.optInLink, Icons.Default.Link,
                    showErrors && errors["optInLink"] == true, KeyboardType.Uri,
                    help = "Play Console → Closed testing → Testers → copy the join link (https://…)",
                ) { form = form.copy(optInLink = it) }
                Field("App category (optional)", form.category, Icons.Default.Category, false) {
                    form = form.copy(category = it)
                }
                Field("Notes for our team (optional)", form.notes, Icons.Default.Notes, false, singleLine = false) {
                    form = form.copy(notes = it)
                }
            }

            if (requestId == null) {
                Row(
                    Modifier.fillMaxWidth().clickable { consent = !consent },
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(consent, { consent = it })
                    Text(
                        "I agree to the terms. I understand a refund can be requested after payment only until testing starts; once testing begins, refunds are not possible.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            Button(
                onClick = {
                    showErrors = true
                    if (errors.values.any { it } || !consent) return@Button
                    val clean = form.copy(whatsapp = normalizePhone(form.whatsapp))
                    val req = existing
                    if (req == null) {
                        runner.run("Request submitted") {
                            val id = Repository.createRequest(clean)
                            nav.popBackStack()
                            nav.navigate(Routes.request(id))
                        }
                    } else {
                        runner.run(if (resubmit) "Resubmitted" else "Saved", after = { nav.popBackStack() }) {
                            Repository.updateDetails(req, clean)
                        }
                    }
                },
                enabled = !runner.busy.value && loaded,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
            ) {
                if (runner.busy.value) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (requestId == null) "Submit & Continue to Payment" else if (resubmit) "Resubmit" else "Save changes")
            }
            if (showErrors && !consent) {
                Text("Please accept the terms to continue.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    icon: ImageVector,
    error: Boolean,
    keyboard: KeyboardType = KeyboardType.Text,
    help: String? = null,
    singleLine: Boolean = true,
    noSpaces: Boolean = keyboard == KeyboardType.Email || keyboard == KeyboardType.Uri,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        // Emails, package names and links never contain spaces; some keyboards auto-insert one after "."
        onValueChange = { onChange(if (noSpaces) it.filterNot(Char::isWhitespace) else it) },
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        isError = error,
        supportingText = when {
            error -> ({ Text(help ?: "Required") })
            help != null -> ({ Text(help) })
            else -> null
        },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = !noSpaces),
        modifier = Modifier.fillMaxWidth(),
    )
}

private val formSaver = androidx.compose.runtime.saveable.listSaver<RequestForm, String>(
    save = { listOf(it.fullName, it.whatsapp, it.contactEmail, it.appName, it.packageName, it.optInLink, it.category, it.notes) },
    restore = { l -> RequestForm(l[0], l[1], l[2], l[3], l[4], l[5], l[6], l[7]) },
)
