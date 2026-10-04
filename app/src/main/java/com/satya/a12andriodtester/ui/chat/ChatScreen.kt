package com.satya.a12andriodtester.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.satya.a12andriodtester.data.AppConfig
import com.satya.a12andriodtester.data.ChatMessage
import com.satya.a12andriodtester.data.Repository
import com.satya.a12andriodtester.data.Session
import com.satya.a12andriodtester.ui.components.EmptyState
import com.satya.a12andriodtester.ui.components.GradientHeader
import com.satya.a12andriodtester.ui.components.ReplyBanner
import com.satya.a12andriodtester.ui.components.pretty
import com.satya.a12andriodtester.ui.rememberActionRunner
import com.satya.a12andriodtester.ui.theme.CoolGradient
import com.satya.a12andriodtester.ui.theme.Indigo

/** Asynchronous per-request chat — SPEC.md §5.8. */
@Composable
fun ChatScreen(nav: NavController, session: Session, requestId: String) {
    val req by remember(requestId) { Repository.request(requestId) }.collectAsStateWithLifecycle(null)
    val messages by remember(requestId) { Repository.messages(requestId) }.collectAsStateWithLifecycle(null)
    val config by remember { Repository.config() }.collectAsStateWithLifecycle(AppConfig())
    val runner = rememberActionRunner()
    var text by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(req?.lastMessageAt) { req?.let { Repository.markRead(it) } }
    LaunchedEffect(messages?.size) {
        val n = messages?.size ?: 0
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        GradientHeader(
            title = if (session.isAdmin) req?.fullName ?: "Chat" else "Support chat",
            subtitle = req?.appName,
            brush = CoolGradient,
            onBack = { nav.popBackStack() },
        )
        if (!session.isAdmin) {
            Box(Modifier.padding(12.dp)) { ReplyBanner(config.replyHours) }
        }
        val list = messages.orEmpty()
        Box(Modifier.weight(1f)) {
            if (messages != null && list.isEmpty()) {
                EmptyState(Icons.AutoMirrored.Filled.Chat, "No messages yet", "Send a message and our team will get back to you.")
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.id }) { Bubble(it, mine = it.senderRole == session.role) }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                text, { text = it },
                placeholder = { Text("Type a message") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = {
                    val r = req ?: return@FilledIconButton
                    val t = text
                    text = ""
                    runner.run { Repository.sendMessage(r, t) }
                },
                enabled = text.isNotBlank() && req != null,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Indigo),
                modifier = Modifier.padding(start = 8.dp),
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White) }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) Indigo else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!mine) {
                    Text(
                        if (m.senderRole == "admin") "Support team" else m.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = Indigo,
                    )
                }
                Text(m.text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    m.createdAt.pretty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (mine) Color.White else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.6f),
                )
            }
        }
    }
}
