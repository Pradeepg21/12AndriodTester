package com.satya.a12andriodtester.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class Session(
    val uid: String,
    val email: String,
    val displayName: String,
    val isAdmin: Boolean,
    /** Admin uid when an admin is signed in as this user — SPEC.md §3. */
    val impersonatedBy: String?,
) {
    val role get() = if (isAdmin) "admin" else "user"
}

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val session: Session) : SessionState
}

object AuthRepository {
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private val functions get() = FirebaseFunctions.getInstance(FUNCTIONS_REGION)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state

    val current: Session? get() = (state.value as? SessionState.SignedIn)?.session

    private var started = false

    fun start() {
        if (started) return
        started = true
        auth.addAuthStateListener { fa ->
            val user = fa.currentUser
            if (user == null) {
                _state.value = SessionState.SignedOut
            } else {
                scope.launch { loadSession(user, forceRefresh = false) }
            }
        }
    }

    private suspend fun loadSession(user: FirebaseUser, forceRefresh: Boolean) {
        val claims = runCatching { user.getIdToken(forceRefresh).await().claims }.getOrDefault(emptyMap())
        val session = Session(
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = user.displayName.orEmpty(),
            isAdmin = claims["admin"] == true,
            impersonatedBy = claims["impersonatedBy"] as? String,
        )
        runCatching { ensureProfile(user, session) }
        _state.value = SessionState.SignedIn(session)
        if (session.impersonatedBy == null) registerPushToken(session.uid)
    }

    /** Re-reads custom claims, e.g. right after the server grants the admin role on first sign-in. */
    suspend fun refreshClaims() {
        auth.currentUser?.let { loadSession(it, forceRefresh = true) }
    }

    private suspend fun ensureProfile(user: FirebaseUser, session: Session) {
        val ref = db.collection("users").document(user.uid)
        val snap = ref.get().await()
        if (!snap.exists()) {
            ref.set(
                mapOf(
                    "name" to user.displayName.orEmpty(),
                    "email" to user.email.orEmpty(),
                    "whatsapp" to "",
                    "role" to "user",
                    "photoUrl" to (user.photoUrl?.toString() ?: ""),
                    "fcmTokens" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "deleted" to false,
                )
            ).await()
        } else if (session.impersonatedBy == null) {
            ref.update("lastLoginAt", FieldValue.serverTimestamp()).await()
        }
    }

    private fun registerPushToken(uid: String) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            db.collection("users").document(uid).update("fcmTokens", FieldValue.arrayUnion(token))
        }
    }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        AuditLogger.log("login", "user", auth.currentUser?.uid.orEmpty(), "Email sign-in")
    }

    suspend fun signUpWithEmail(name: String, email: String, password: String) {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: return
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        runCatching { user.sendEmailVerification().await() }
        loadSession(user, forceRefresh = true)
        db.collection("users").document(user.uid).update("name", name.trim())
        AuditLogger.log("signup", "user", user.uid, "Email sign-up")
    }

    suspend fun signInWithGoogle(idToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        AuditLogger.log("login", "user", auth.currentUser?.uid.orEmpty(), "Google sign-in")
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun signOut() {
        val s = current
        if (s != null && s.impersonatedBy == null) {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                db.collection("users").document(s.uid).update("fcmTokens", FieldValue.arrayRemove(token)).await()
            }
            AuditLogger.log("logout", "user", s.uid, "Signed out")
        }
        auth.signOut()
    }

    /** Admin: sign in as [targetUid]. The server verifies the caller is admin and audits it. */
    suspend fun impersonate(targetUid: String) {
        val result = functions.getHttpsCallable("impersonateUser").call(mapOf("uid" to targetUid)).await()
        val token = (result.getData() as Map<*, *>)["token"] as String
        auth.signInWithCustomToken(token).await()
    }

    /** Return from impersonation to the original admin account. */
    suspend fun endImpersonation() {
        val result = functions.getHttpsCallable("endImpersonation").call().await()
        val token = (result.getData() as Map<*, *>)["token"] as String
        auth.signInWithCustomToken(token).await()
    }

    suspend fun deleteMyAccount() {
        functions.getHttpsCallable("deleteMyAccount").call().await()
        auth.signOut()
    }
}
