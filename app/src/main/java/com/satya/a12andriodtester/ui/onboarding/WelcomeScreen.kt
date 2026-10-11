package com.satya.a12andriodtester.ui.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.satya.a12andriodtester.ui.theme.Amber
import com.satya.a12andriodtester.ui.theme.Emerald
import com.satya.a12andriodtester.ui.theme.Indigo
import com.satya.a12andriodtester.ui.theme.Pink
import com.satya.a12andriodtester.ui.theme.Sky
import com.satya.a12andriodtester.ui.theme.Teal
import com.satya.a12andriodtester.ui.theme.Violet
import kotlinx.coroutines.launch

private data class Page(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val bullets: List<String>,
    val brush: Brush,
)

private val pages = listOf(
    Page(
        Icons.Default.RocketLaunch,
        "Real testing, real feedback",
        "12 dedicated testers install your app from your closed test, use it on their own devices over 14 days and report the bugs, crashes and usability issues they find. Not a tester swap — you never test other people's apps in return.",
        listOf("12 real testers who actually use your app", "Bugs, crashes and UX feedback", "Full 14-day closed test", "Final report with recommended fixes"),
        Brush.linearGradient(listOf(Indigo, Violet)),
    ),
    Page(
        Icons.Default.Timeline,
        "How it works",
        "Track every step of your request right inside the app.",
        listOf(
            "1. Submit your app details",
            "2. Scan the UPI QR and pay",
            "3. Upload the payment screenshot",
            "4. We verify payment and your details",
            "5. We send 12 tester emails to your WhatsApp",
            "6. Add them to your closed test and confirm",
            "7. Testing runs for 14 days",
            "8. Download your final report",
        ),
        Brush.linearGradient(listOf(Violet, Pink)),
    ),
    Page(
        Icons.Default.Groups,
        "What you need to do",
        "Keep these ready before you start:",
        listOf(
            "Your app uploaded to a Closed testing track",
            "The opt-in link of your closed test",
            "A WhatsApp number to receive tester emails",
            "Keep the closed test active for all 14 days",
        ),
        Brush.linearGradient(listOf(Sky, Teal)),
    ),
    Page(
        Icons.Default.SupportAgent,
        "Support, refunds & report",
        "We are with you at every step.",
        listOf(
            "Chat with us on any request — reply within 4 hours",
            "Refunds can be requested after payment until testing starts. Once testing begins, refunds are not possible.",
            "After completion, your report stays in the Completed tab",
            "Re-open this guide anytime from Help",
        ),
        Brush.linearGradient(listOf(Amber, Pink)),
    ),
)

@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(pager, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                        .background(page.brush)
                        .statusBarsPadding()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(120.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(page.icon, null, tint = Color.White, modifier = Modifier.size(64.dp))
                    }
                }
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(page.title, style = MaterialTheme.typography.headlineSmall)
                    Text(page.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    page.bullets.forEach { b ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.CheckCircle, null, tint = Emerald, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(b, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDone) { Text(if (last) "" else "Skip") }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { i ->
                    Box(
                        Modifier
                            .padding(3.dp)
                            .size(width = if (i == pager.currentPage) 22.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (i == pager.currentPage) Indigo else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Button(
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
            ) { Text(if (last) "Get Started" else "Next", textAlign = TextAlign.Center) }
        }
    }
}
