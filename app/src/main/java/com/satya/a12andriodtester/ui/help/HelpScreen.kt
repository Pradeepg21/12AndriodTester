package com.satya.a12andriodtester.ui.help

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.satya.a12andriodtester.BuildConfig
import com.satya.a12andriodtester.data.AppConfig
import com.satya.a12andriodtester.data.Repository
import com.satya.a12andriodtester.ui.Routes
import com.satya.a12andriodtester.ui.components.GradientHeader
import com.satya.a12andriodtester.ui.components.SectionCard
import com.satya.a12andriodtester.ui.emailTo
import com.satya.a12andriodtester.ui.theme.Amber
import com.satya.a12andriodtester.ui.theme.Indigo
import com.satya.a12andriodtester.ui.theme.Sky
import com.satya.a12andriodtester.ui.theme.Slate

private val faqs = listOf(
    "Why do I need 12 testers?" to
        "Google Play requires new personal developer accounts to run a closed test with at least 12 testers who stay opted in for 14 days in a row before you can apply for production access.",
    "How do I pay?" to
        "Open your request and tap “Pay now”. Scan the UPI QR with any UPI app, pay the amount shown, then upload the payment screenshot in the next step.",
    "When will I get the tester emails?" to
        "After your payment and app details are verified, we send 12 tester emails to the WhatsApp number on your request.",
    "What do I do with the emails?" to
        "In Play Console open Testing → Closed testing → Testers, add all 12 emails to your email list, save, and tap “I have added all 12 emails” in the app.",
    "How long does testing take?" to
        "Testing runs for 14 days. Keep your closed test active and do not remove testers during this time. The app shows “Day X of 14”.",
    "Can I get a refund?" to
        "Yes — after payment you can request a refund from the request screen until testing starts. Once testing begins, refunds are not possible.",
    "Can I submit more than one app?" to
        "Yes. Each app is a separate request and you can track all of them from the home screen.",
    "How fast will support reply?" to
        "Use the chat on any request. Our team replies within 4 hours.",
    "Where is my final report?" to
        "Once completed, open the request from the Completed tab and tap “View / download report”. It stays available anytime.",
)

@Composable
fun HelpScreen(nav: NavController) {
    val context = LocalContext.current
    val config by remember { Repository.config() }.collectAsStateWithLifecycle(AppConfig())

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GradientHeader(title = "Help", subtitle = "Guides, FAQ and support", onBack = { nav.popBackStack() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(title = "Getting started guide", icon = Icons.Default.MenuBook, accent = Indigo) {
                Text("See how the whole process works, step by step.")
                Button(
                    onClick = { nav.navigate(Routes.WELCOME) },
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open welcome guide") }
            }
            SectionCard(title = "Frequently asked questions", icon = Icons.Default.QuestionAnswer, accent = Amber) {
                faqs.forEachIndexed { i, (q, a) ->
                    Faq(q, a)
                    if (i < faqs.lastIndex) HorizontalDivider()
                }
            }
            SectionCard(title = "Contact support", icon = Icons.Default.Email, accent = Sky) {
                Text("For anything about a specific request, use the chat on that request — reply within ${config.replyHours} hours.")
                OutlinedButton(
                    onClick = { context.emailTo(config.supportEmail, "Twelve Testers – Support") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(config.supportEmail) }
            }
            SectionCard(title = "About", icon = Icons.Default.Info, accent = Slate) {
                Text("Twelve Testers • Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            }
        }
    }
}

@Composable
private fun Faq(q: String, a: String) {
    var open by rememberSaveable(q) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.animateContentSize().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(q, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (open) Text(a, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}
