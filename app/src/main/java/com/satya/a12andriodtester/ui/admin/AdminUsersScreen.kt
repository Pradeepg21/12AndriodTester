package com.satya.a12andriodtester.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.satya.a12andriodtester.data.AuthRepository
import com.satya.a12andriodtester.data.Repository
import com.satya.a12andriodtester.data.Session
import com.satya.a12andriodtester.data.UserProfile
import com.satya.a12andriodtester.ui.components.ConfirmDialog
import com.satya.a12andriodtester.ui.components.GradientHeader
import com.satya.a12andriodtester.ui.components.Loading
import com.satya.a12andriodtester.ui.components.pretty
import com.satya.a12andriodtester.ui.rememberActionRunner
import com.satya.a12andriodtester.ui.theme.BrandGradient
import com.satya.a12andriodtester.ui.theme.Rose
import com.satya.a12andriodtester.ui.theme.Violet

@Composable
fun AdminUsersScreen(nav: NavController, session: Session) {
    val users by remember { Repository.allUsers() }.collectAsStateWithLifecycle(null)
    val requests by remember { Repository.allRequests() }.collectAsStateWithLifecycle(null)
    val runner = rememberActionRunner()
    var query by rememberSaveable { mutableStateOf("") }
    var impersonate by remember { mutableStateOf<UserProfile?>(null) }
    var roleChange by remember { mutableStateOf<UserProfile?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GradientHeader(title = "Users", subtitle = "${users?.size ?: 0} registered", onBack = { nav.popBackStack() })
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("Search name, email, WhatsApp") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        val list = users
        if (list == null) {
            Loading()
            return
        }
        val counts = requests.orEmpty().groupingBy { it.userId }.eachCount()
        val q = query.trim().lowercase()
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list.filter { q.isBlank() || listOf(it.name, it.email, it.whatsapp).any { f -> f.lowercase().contains(q) } }, key = { it.uid }) { u ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
                                Text(u.name.ifBlank { u.email }.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(u.name.ifBlank { "(no name)" }, style = MaterialTheme.typography.titleMedium)
                                Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (u.whatsapp.isNotBlank()) Text(u.whatsapp, style = MaterialTheme.typography.bodySmall)
                            }
                            if (u.isAdmin) Text("ADMIN", color = Violet, style = MaterialTheme.typography.labelMedium)
                            if (u.deleted) Text("DELETED", color = Rose, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            "${counts[u.uid] ?: 0} requests • joined ${u.createdAt.pretty()} • last login ${u.lastLoginAt.pretty()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        if (u.uid != session.uid && !u.deleted) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!u.isAdmin) {
                                    OutlinedButton(onClick = { impersonate = u }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Login, null, modifier = Modifier.size(18.dp))
                                        Text(" Login as user")
                                    }
                                }
                                OutlinedButton(onClick = { roleChange = u }, modifier = Modifier.weight(1f)) {
                                    Text(if (u.isAdmin) "Remove admin" else "Make admin")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    impersonate?.let { u ->
        ConfirmDialog(
            "Login as ${u.name.ifBlank { u.email }}?",
            "You will see the app exactly as this user. Everything you do is recorded in the audit log under your admin account. Tap “Exit” on the red banner to return.",
            "Login as user",
            onDismiss = { impersonate = null },
        ) {
            impersonate = null
            runner.run { AuthRepository.impersonate(u.uid) }
        }
    }
    roleChange?.let { u ->
        ConfirmDialog(
            if (u.isAdmin) "Remove admin access?" else "Make admin?",
            if (u.isAdmin) "${u.email} will become a normal user." else "${u.email} will be able to see and manage all users and requests.",
            "Confirm",
            onDismiss = { roleChange = null },
        ) {
            roleChange = null
            runner.run("Role updated") { Repository.setAdminRole(u.uid, !u.isAdmin) }
        }
    }
}
