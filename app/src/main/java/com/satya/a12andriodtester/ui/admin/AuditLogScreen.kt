package com.satya.a12andriodtester.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.satya.a12andriodtester.data.Repository
import com.satya.a12andriodtester.ui.components.GradientHeader
import com.satya.a12andriodtester.ui.components.Loading
import com.satya.a12andriodtester.ui.components.pretty
import com.satya.a12andriodtester.ui.theme.Rose
import com.satya.a12andriodtester.ui.theme.Violet

@Composable
fun AuditLogScreen(nav: NavController) {
    val entries by remember { Repository.auditLog() }.collectAsStateWithLifecycle(null)
    val users by remember { Repository.allUsers() }.collectAsStateWithLifecycle(null)
    var query by rememberSaveable { mutableStateOf("") }
    val names = users.orEmpty().associate { it.uid to it.name.ifBlank { it.email } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GradientHeader(title = "Audit Log", subtitle = "Latest 300 events", onBack = { nav.popBackStack() })
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("Filter by action, request ID, user") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        val list = entries
        if (list == null) {
            Loading()
            return
        }
        val q = query.trim().lowercase()
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                list.filter {
                    q.isBlank() || listOf(it.action, it.entityId, it.summary, it.actorId, names[it.actorId].orEmpty())
                        .any { f -> f.lowercase().contains(q) }
                },
                key = { it.id },
            ) { e ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            Text(e.action, fontWeight = FontWeight.Bold, color = Violet, modifier = Modifier.weight(1f))
                            Text(e.timestamp.pretty(), style = MaterialTheme.typography.labelSmall)
                        }
                        if (e.summary.isNotBlank()) Text(e.summary, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${e.entityType}/${e.entityId} • by ${names[e.actorId] ?: e.actorId.ifBlank { "system" }} (${e.actorRole.ifBlank { "system" }}) • ${e.source}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        if (e.impersonatedBy.isNotBlank()) {
                            Text(
                                "Impersonated by ${names[e.impersonatedBy] ?: e.impersonatedBy}",
                                style = MaterialTheme.typography.labelSmall, color = Rose,
                            )
                        }
                    }
                }
            }
        }
    }
}
