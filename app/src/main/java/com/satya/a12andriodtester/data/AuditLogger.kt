package com.satya.a12andriodtester.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.satya.a12andriodtester.BuildConfig

/**
 * Client-side audit entries for session events (SPEC.md §8).
 * Data changes are audited server-side by Cloud Functions triggers.
 */
object AuditLogger {
    fun log(action: String, entityType: String, entityId: String, summary: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val session = AuthRepository.current
        FirebaseFirestore.getInstance().collection("auditLogs").add(
            mapOf(
                "actorId" to uid,
                "actorRole" to (session?.role ?: "user"),
                "impersonatedBy" to session?.impersonatedBy,
                "action" to action,
                "entityType" to entityType,
                "entityId" to entityId,
                "summary" to summary,
                "timestamp" to FieldValue.serverTimestamp(),
                "appVersion" to BuildConfig.VERSION_NAME,
                "source" to "app",
            )
        )
    }
}
