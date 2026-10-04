package com.satya.a12andriodtester.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.satya.a12andriodtester.data.RequestStatus
import com.satya.a12andriodtester.data.Repository
import com.satya.a12andriodtester.ui.Routes
import com.satya.a12andriodtester.ui.components.GradientHeader
import com.satya.a12andriodtester.ui.components.Loading
import com.satya.a12andriodtester.ui.home.CategoryTabs
import com.satya.a12andriodtester.ui.home.RequestListContent
import com.satya.a12andriodtester.ui.home.needsAction
import com.satya.a12andriodtester.ui.theme.Amber
import com.satya.a12andriodtester.ui.theme.Pink
import com.satya.a12andriodtester.ui.theme.Rose
import com.satya.a12andriodtester.ui.theme.Sky

/** Admin home — SPEC.md §5.11. */
@Composable
fun AdminHomeScreen(nav: NavController) {
    val requests by remember { Repository.allRequests() }.collectAsStateWithLifecycle(null)
    var tab by rememberSaveable { mutableStateOf(RequestCategory.NEW) }
    var query by rememberSaveable { mutableStateOf("") }
    var actionOnly by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GradientHeader(
            title = "Admin Dashboard",
            subtitle = "Manage all requests",
            actions = {
                IconButton(onClick = { nav.navigate(Routes.USERS) }) { Icon(Icons.Default.Group, "Users", tint = Color.White) }
                IconButton(onClick = { nav.navigate(Routes.AUDIT) }) { Icon(Icons.Default.History, "Audit log", tint = Color.White) }
                IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
                IconButton(onClick = { nav.navigate(Routes.PROFILE) }) { Icon(Icons.Default.AccountCircle, "Profile", tint = Color.White) }
            },
        ) {
            val list = requests.orEmpty()
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tile("Payments to verify", list.count { it.status == RequestStatus.PAYMENT_SUBMITTED }, Sky, Modifier.weight(1f))
                Tile("Refund requests", list.count { it.status == RequestStatus.REFUND_REQUESTED }, Pink, Modifier.weight(1f))
                Tile("Unread chats", list.count { it.unreadFor(true) }, Rose, Modifier.weight(1f))
                Tile(
                    "Action needed", list.count { needsAction(it, true) }, Amber, Modifier.weight(1f)
                        .clickable { actionOnly = !actionOnly },
                    highlighted = actionOnly,
                )
            }
            OutlinedTextField(
                query, { query = it },
                placeholder = { Text("Search name, app, package, email, WhatsApp", color = Color.White.copy(alpha = 0.7f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                    cursorColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }

        val list = requests
        if (list == null) {
            Loading()
            return
        }
        val filtered = list.filter { it.matches(query) && (!actionOnly || needsAction(it, true) || it.unreadFor(true)) }
        CategoryTabs(tab, filtered.groupingBy { it.tab }.eachCount()) { tab = it }
        RequestListContent(
            requests = filtered.filter { it.tab == tab },
            admin = true,
            emptyTitle = "Nothing here",
            emptyMessage = if (query.isNotBlank() || actionOnly) "No requests match your filters." else "Requests will appear here.",
            onOpen = { nav.navigate(Routes.request(it.id)) },
        )
    }
}

@Composable
private fun Tile(label: String, value: Int, color: Color, modifier: Modifier, highlighted: Boolean = false) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (highlighted) Color.White else Color.White.copy(alpha = 0.18f),
        contentColor = if (highlighted) color else Color.White,
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("$value", style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }
}
