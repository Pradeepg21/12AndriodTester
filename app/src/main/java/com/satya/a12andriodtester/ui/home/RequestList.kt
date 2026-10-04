package com.satya.a12andriodtester.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.satya.a12andriodtester.data.RequestCategory
import com.satya.a12andriodtester.data.RequestStatus
import com.satya.a12andriodtester.data.TestRequest
import com.satya.a12andriodtester.ui.components.EmptyState
import com.satya.a12andriodtester.ui.components.StatusChip
import com.satya.a12andriodtester.ui.components.StepProgress
import com.satya.a12andriodtester.ui.components.pretty
import com.satya.a12andriodtester.ui.theme.Rose
import com.satya.a12andriodtester.ui.theme.categoryColor
import com.satya.a12andriodtester.ui.theme.statusColor

@Composable
fun CategoryTabs(selected: RequestCategory, counts: Map<RequestCategory, Int>, onSelect: (RequestCategory) -> Unit) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selected.ordinal,
        edgePadding = 12.dp,
        containerColor = Color.Transparent,
    ) {
        RequestCategory.entries.forEach { c ->
            Tab(
                selected = c == selected,
                onClick = { onSelect(c) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.label)
                        val n = counts[c] ?: 0
                        if (n > 0) {
                            Spacer(Modifier.width(6.dp))
                            Badge(containerColor = categoryColor(c), contentColor = Color.White) { Text("$n") }
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun RequestListContent(
    requests: List<TestRequest>,
    admin: Boolean,
    emptyTitle: String,
    emptyMessage: String,
    onOpen: (TestRequest) -> Unit,
) {
    if (requests.isEmpty()) {
        EmptyState(Icons.Default.Inbox, emptyTitle, emptyMessage)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(requests, key = { it.id }) { RequestCard(it, admin) { onOpen(it) } }
    }
}

@Composable
fun RequestCard(req: TestRequest, admin: Boolean, onClick: () -> Unit) {
    val color = statusColor(req.status)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Apps, null, tint = color) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(req.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (admin) "${req.fullName} • ${req.packageName}" else req.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (req.unreadFor(admin)) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Rose), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.Chat, "Unread message", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(req.status)
                    Spacer(Modifier.weight(1f))
                    req.testingDay?.let {
                        Text("Day $it of 14", style = MaterialTheme.typography.labelMedium, color = color)
                    }
                }
                StepProgress(req.effectiveStep, color)
                Text(
                    if (admin) req.status.adminHint else req.status.userHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Updated ${req.updatedAt.pretty()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** Highlights statuses where the viewer has something to do. */
fun needsAction(req: TestRequest, admin: Boolean): Boolean = if (admin) {
    req.status in setOf(
        RequestStatus.PAYMENT_SUBMITTED, RequestStatus.PAYMENT_VERIFIED, RequestStatus.INTERNAL_VERIFICATION,
        RequestStatus.EMAILS_ADDED, RequestStatus.REFUND_REQUESTED, RequestStatus.REPORT_READY,
    )
} else {
    req.status in setOf(RequestStatus.PAYMENT_PENDING, RequestStatus.EMAILS_SHARED, RequestStatus.NEEDS_INFO)
}
