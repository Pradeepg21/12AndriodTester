package com.satya.a12andriodtester.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.satya.a12andriodtester.data.RequestCategory
import com.satya.a12andriodtester.data.Repository
import com.satya.a12andriodtester.data.Session
import com.satya.a12andriodtester.ui.Routes
import com.satya.a12andriodtester.ui.components.GradientHeader
import com.satya.a12andriodtester.ui.components.Loading
import com.satya.a12andriodtester.ui.theme.Indigo

@Composable
fun HomeScreen(nav: NavController, session: Session) {
    val requests by remember(session.uid) { Repository.myRequests(session.uid) }.collectAsStateWithLifecycle(null)
    var tab by rememberSaveable { mutableStateOf(RequestCategory.NEW) }
    val firstName = session.displayName.substringBefore(' ').ifBlank { "there" }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.NEW_REQUEST) },
                containerColor = Indigo,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New Request") },
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            GradientHeader(
                title = "Hi, $firstName 👋",
                subtitle = "Track your closed testing requests",
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.HELP) }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, "Help", tint = Color.White)
                    }
                    IconButton(onClick = { nav.navigate(Routes.PROFILE) }) {
                        Icon(Icons.Default.AccountCircle, "Profile", tint = Color.White)
                    }
                },
            ) {
                val list = requests.orEmpty()
                val actionCount = list.count { needsAction(it, admin = false) }
                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    contentColor = Color.White,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(bottom = 12.dp, top = 4.dp),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Stat("${list.size}", "Requests")
                        Stat("${list.count { it.tab == RequestCategory.IN_PROGRESS }}", "In progress")
                        Stat("${list.count { it.tab == RequestCategory.COMPLETED }}", "Completed")
                        Stat("$actionCount", "Action needed")
                    }
                }
            }

            val list = requests
            if (list == null) {
                Loading()
            } else {
                val counts = list.groupingBy { it.tab }.eachCount()
                CategoryTabs(tab, counts) { tab = it }
                Spacer(Modifier.height(4.dp))
                RequestListContent(
                    requests = list.filter { it.tab == tab },
                    admin = false,
                    emptyTitle = when (tab) {
                        RequestCategory.NEW -> "No new requests"
                        RequestCategory.IN_PROGRESS -> "Nothing in progress yet"
                        RequestCategory.COMPLETED -> "No completed apps yet"
                        RequestCategory.CLOSED -> "No refunds or closed requests"
                    },
                    emptyMessage = if (tab == RequestCategory.NEW) "Tap “New Request” to start closed testing for your app."
                    else "Requests will appear here as they move forward.",
                    onOpen = { nav.navigate(Routes.request(it.id)) },
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(Modifier.background(Color.Transparent)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
