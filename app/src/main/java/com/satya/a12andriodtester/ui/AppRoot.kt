package com.satya.a12andriodtester.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.satya.a12andriodtester.data.AuthRepository
import com.satya.a12andriodtester.data.Prefs
import com.satya.a12andriodtester.data.Repository
import com.satya.a12andriodtester.data.Session
import com.satya.a12andriodtester.data.SessionState
import com.satya.a12andriodtester.ui.admin.AdminHomeScreen
import com.satya.a12andriodtester.ui.admin.AdminSettingsScreen
import com.satya.a12andriodtester.ui.admin.AdminUsersScreen
import com.satya.a12andriodtester.ui.admin.AuditLogScreen
import com.satya.a12andriodtester.ui.auth.LoginScreen
import com.satya.a12andriodtester.ui.chat.ChatScreen
import com.satya.a12andriodtester.ui.components.Loading
import com.satya.a12andriodtester.ui.help.HelpScreen
import com.satya.a12andriodtester.ui.home.HomeScreen
import com.satya.a12andriodtester.ui.onboarding.WelcomeScreen
import com.satya.a12andriodtester.ui.profile.ProfileScreen
import com.satya.a12andriodtester.ui.request.PaymentScreen
import com.satya.a12andriodtester.ui.request.RequestDetailScreen
import com.satya.a12andriodtester.ui.request.RequestFormScreen
import com.satya.a12andriodtester.ui.theme.Rose

object Routes {
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val NEW_REQUEST = "request/new"
    const val REQUEST = "request/{id}"
    const val EDIT = "request/{id}/edit"
    const val PAY = "request/{id}/pay"
    const val CHAT = "request/{id}/chat"
    const val HELP = "help"
    const val PROFILE = "profile"
    const val USERS = "admin/users"
    const val AUDIT = "admin/audit"
    const val SETTINGS = "admin/settings"

    fun request(id: String) = "request/$id"
    fun edit(id: String) = "request/$id/edit"
    fun pay(id: String) = "request/$id/pay"
    fun chat(id: String) = "request/$id/chat"
}

@Composable
fun AppRoot(state: SessionState, openRequestId: String?) {
    val context = LocalContext.current
    var onboardingDone by rememberSaveable { mutableStateOf(Prefs.onboardingDone(context)) }

    when {
        !onboardingDone -> WelcomeScreen(onDone = {
            Prefs.setOnboardingDone(context)
            onboardingDone = true
        })
        state is SessionState.Loading -> Loading()
        state is SessionState.SignedOut -> LoginScreen()
        state is SessionState.SignedIn -> SignedInRoot(state.session, openRequestId)
    }
}

@Composable
private fun SignedInRoot(session: Session, openRequestId: String?) {
    // Pick up role changes (bootstrap admin, grant/revoke) without re-login.
    val profile by remember(session.uid) { Repository.user(session.uid) }.collectAsStateWithLifecycle(null)
    LaunchedEffect(profile?.role, session.isAdmin) {
        val p = profile ?: return@LaunchedEffect
        if (session.impersonatedBy == null && p.isAdmin != session.isAdmin) AuthRepository.refreshClaims()
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (session.impersonatedBy != null) ImpersonationBanner(session)
        // New NavHost per identity so admin/user back stacks never mix.
        val insets = if (session.impersonatedBy != null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier
        Box(Modifier.weight(1f).then(insets)) {
            key(session.uid, session.isAdmin) {
                val nav = rememberNavController()
                LaunchedEffect(openRequestId) {
                    if (openRequestId != null) nav.navigate(Routes.request(openRequestId))
                }
                AppNavHost(nav, session)
            }
        }
    }
}

@Composable
private fun ImpersonationBanner(session: Session) {
    val runner = rememberActionRunner()
    Surface(color = Rose, contentColor = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.SupervisorAccount, null)
            Spacer(Modifier.width(8.dp))
            Text(
                "Viewing as ${session.displayName.ifBlank { session.email }}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(onClick = { runner.run { AuthRepository.endImpersonation() } }, enabled = !runner.busy.value) {
                Text("Exit", color = Color.White)
            }
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, session: Session) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            if (session.isAdmin) AdminHomeScreen(nav) else HomeScreen(nav, session)
        }
        composable(Routes.WELCOME) { WelcomeScreen(onDone = back) }
        composable(Routes.NEW_REQUEST) { RequestFormScreen(nav, session, requestId = null) }
        composable(Routes.REQUEST) { RequestDetailScreen(nav, session, it.arguments?.getString("id").orEmpty()) }
        composable(Routes.EDIT) { RequestFormScreen(nav, session, it.arguments?.getString("id")) }
        composable(Routes.PAY) { PaymentScreen(nav, it.arguments?.getString("id").orEmpty()) }
        composable(Routes.CHAT) { ChatScreen(nav, session, it.arguments?.getString("id").orEmpty()) }
        composable(Routes.HELP) { HelpScreen(nav) }
        composable(Routes.PROFILE) { ProfileScreen(nav, session) }
        composable(Routes.USERS) { AdminUsersScreen(nav, session) }
        composable(Routes.AUDIT) { AuditLogScreen(nav) }
        composable(Routes.SETTINGS) { AdminSettingsScreen(nav) }
    }
}
