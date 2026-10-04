package com.satya.a12andriodtester.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.satya.a12andriodtester.data.AuthRepository
import com.satya.a12andriodtester.data.Repository
import com.satya.a12andriodtester.data.Session
import com.satya.a12andriodtester.ui.components.ConfirmDialog
import com.satya.a12andriodtester.ui.components.GradientHeader
import com.satya.a12andriodtester.ui.components.InfoRow
import com.satya.a12andriodtester.ui.components.SectionCard
import com.satya.a12andriodtester.ui.rememberActionRunner
import com.satya.a12andriodtester.ui.request.isValidWhatsApp
import com.satya.a12andriodtester.ui.request.normalizePhone
import com.satya.a12andriodtester.ui.theme.Indigo
import com.satya.a12andriodtester.ui.theme.Rose

@Composable
fun ProfileScreen(nav: NavController, session: Session) {
    val profile by remember(session.uid) { Repository.user(session.uid) }.collectAsStateWithLifecycle(null)
    val runner = rememberActionRunner()
    var name by rememberSaveable { mutableStateOf("") }
    var whatsapp by rememberSaveable { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        val p = profile ?: return@LaunchedEffect
        if (!loaded) {
            if (name.isBlank()) name = p.name
            if (whatsapp.isBlank()) whatsapp = p.whatsapp
            loaded = true
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GradientHeader(title = "Profile", subtitle = session.email, onBack = { nav.popBackStack() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(title = "Your details", icon = Icons.Default.Person, accent = Indigo) {
                InfoRow("Email", session.email)
                InfoRow("Role", if (session.isAdmin) "Admin" else "User")
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    whatsapp, { whatsapp = it },
                    label = { Text("WhatsApp number (with country code)") },
                    isError = whatsapp.isNotBlank() && !isValidWhatsApp(whatsapp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { runner.run("Profile saved") { Repository.updateMyProfile(name, normalizePhone(whatsapp)) } },
                    enabled = loaded && name.isNotBlank() && (whatsapp.isBlank() || isValidWhatsApp(whatsapp)) && !runner.busy.value,
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Save") }
            }

            OutlinedButton(
                onClick = { runner.run { AuthRepository.signOut() } },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Text("  Sign out")
            }
            if (session.impersonatedBy == null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Icon(Icons.Default.DeleteForever, null, tint = Rose)
                    Text("  Delete my account", color = Rose)
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            "Delete account?",
            "Your sign-in will be removed permanently. Records of your requests are kept for audit and support.",
            "Delete",
            onDismiss = { confirmDelete = false },
        ) {
            confirmDelete = false
            runner.run("Account deleted") { AuthRepository.deleteMyAccount() }
        }
    }
}
