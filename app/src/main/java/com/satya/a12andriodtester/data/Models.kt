package com.satya.a12andriodtester.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val whatsapp: String = "",
    val role: String = "user",
    val photoUrl: String = "",
    val createdAt: Date? = null,
    val lastLoginAt: Date? = null,
    val deleted: Boolean = false,
) {
    val isAdmin get() = role == "admin"

    companion object {
        fun from(doc: DocumentSnapshot) = UserProfile(
            uid = doc.id,
            name = doc.getString("name").orEmpty(),
            email = doc.getString("email").orEmpty(),
            whatsapp = doc.getString("whatsapp").orEmpty(),
            role = doc.getString("role") ?: "user",
            photoUrl = doc.getString("photoUrl").orEmpty(),
            createdAt = doc.getTimestamp("createdAt")?.toDate(),
            lastLoginAt = doc.getTimestamp("lastLoginAt")?.toDate(),
            deleted = doc.getBoolean("deleted") ?: false,
        )
    }
}

data class StatusEvent(
    val status: RequestStatus,
    val by: String,
    val byRole: String,
    val note: String,
    val at: Date?,
)

data class Payment(
    val amount: Long = 0,
    val utr: String = "",
    val screenshotUrl: String = "",
    val submittedAt: Date? = null,
    val verifiedAt: Date? = null,
    val rejectReason: String = "",
)

data class Refund(
    val reason: String = "",
    val requestedAt: Date? = null,
    val fromStatus: RequestStatus? = null,
    val reference: String = "",
    val processedAt: Date? = null,
    val declineReason: String = "",
)

data class TestRequest(
    val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val whatsapp: String = "",
    val contactEmail: String = "",
    val appName: String = "",
    val packageName: String = "",
    val optInLink: String = "",
    val category: String = "",
    val notes: String = "",
    val status: RequestStatus = RequestStatus.SUBMITTED,
    val resumeStatus: RequestStatus? = null,
    val statusNote: String = "",
    val history: List<StatusEvent> = emptyList(),
    val payment: Payment = Payment(),
    val refund: Refund? = null,
    val testingStartAt: Date? = null,
    val reportUrl: String = "",
    val reportUploadedAt: Date? = null,
    val lastMessageAt: Date? = null,
    val lastMessageBy: String = "",
    val userLastReadAt: Date? = null,
    val adminLastReadAt: Date? = null,
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
    val completedAt: Date? = null,
) {
    val tab get() = categoryOf(status, resumeStatus)
    val refundAllowed get() = isRefundable(status, resumeStatus)

    /** The main-flow step to highlight in the stepper (paused requests show where they paused). */
    val effectiveStep: Int
        get() = if (status.isMainFlow) status.step
        else history.lastOrNull { it.status.isMainFlow }?.status?.step ?: 0

    fun unreadFor(admin: Boolean): Boolean {
        val last = lastMessageAt ?: return false
        if (lastMessageBy == (if (admin) "admin" else "user")) return false
        val read = if (admin) adminLastReadAt else userLastReadAt
        return read == null || read.before(last)
    }

    /** Day X of 14 while testing. */
    val testingDay: Int?
        get() {
            val start = testingStartAt ?: return null
            if (status != RequestStatus.TESTING_IN_PROGRESS) return null
            val days = ((System.currentTimeMillis() - start.time) / 86_400_000L).toInt() + 1
            return days.coerceIn(1, 14)
        }

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return listOf(fullName, appName, packageName, contactEmail, whatsapp, id)
            .any { it.lowercase().contains(q) }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(doc: DocumentSnapshot): TestRequest {
            val pay = doc.get("payment") as? Map<String, Any?> ?: emptyMap()
            val ref = doc.get("refund") as? Map<String, Any?>
            val hist = (doc.get("statusHistory") as? List<Map<String, Any?>>).orEmpty().map {
                StatusEvent(
                    status = RequestStatus.from(it["status"] as? String),
                    by = it["by"] as? String ?: "",
                    byRole = it["byRole"] as? String ?: "",
                    note = it["note"] as? String ?: "",
                    at = (it["at"] as? Timestamp)?.toDate(),
                )
            }
            return TestRequest(
                id = doc.id,
                userId = doc.getString("userId").orEmpty(),
                fullName = doc.getString("fullName").orEmpty(),
                whatsapp = doc.getString("whatsapp").orEmpty(),
                contactEmail = doc.getString("contactEmail").orEmpty(),
                appName = doc.getString("appName").orEmpty(),
                packageName = doc.getString("packageName").orEmpty(),
                optInLink = doc.getString("optInLink").orEmpty(),
                category = doc.getString("category").orEmpty(),
                notes = doc.getString("notes").orEmpty(),
                status = RequestStatus.from(doc.getString("status")),
                resumeStatus = doc.getString("resumeStatus")?.let { RequestStatus.from(it) },
                statusNote = doc.getString("statusNote").orEmpty(),
                history = hist,
                payment = Payment(
                    amount = (pay["amount"] as? Number)?.toLong() ?: 0,
                    utr = pay["utr"] as? String ?: "",
                    screenshotUrl = pay["screenshotUrl"] as? String ?: "",
                    submittedAt = (pay["submittedAt"] as? Timestamp)?.toDate(),
                    verifiedAt = (pay["verifiedAt"] as? Timestamp)?.toDate(),
                    rejectReason = pay["rejectReason"] as? String ?: "",
                ),
                refund = ref?.let {
                    Refund(
                        reason = it["reason"] as? String ?: "",
                        requestedAt = (it["requestedAt"] as? Timestamp)?.toDate(),
                        fromStatus = (it["fromStatus"] as? String)?.let(RequestStatus::from),
                        reference = it["reference"] as? String ?: "",
                        processedAt = (it["processedAt"] as? Timestamp)?.toDate(),
                        declineReason = it["declineReason"] as? String ?: "",
                    )
                },
                testingStartAt = doc.getTimestamp("testingStartAt")?.toDate(),
                reportUrl = doc.getString("reportUrl").orEmpty(),
                reportUploadedAt = doc.getTimestamp("reportUploadedAt")?.toDate(),
                lastMessageAt = doc.getTimestamp("lastMessageAt")?.toDate(),
                lastMessageBy = doc.getString("lastMessageBy").orEmpty(),
                userLastReadAt = doc.getTimestamp("userLastReadAt")?.toDate(),
                adminLastReadAt = doc.getTimestamp("adminLastReadAt")?.toDate(),
                createdAt = doc.getTimestamp("createdAt")?.toDate(),
                updatedAt = doc.getTimestamp("updatedAt")?.toDate(),
                completedAt = doc.getTimestamp("completedAt")?.toDate(),
            )
        }
    }
}

data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderRole: String,
    val senderName: String,
    val text: String,
    val createdAt: Date?,
) {
    companion object {
        fun from(doc: DocumentSnapshot) = ChatMessage(
            id = doc.id,
            senderId = doc.getString("senderId").orEmpty(),
            senderRole = doc.getString("senderRole") ?: "user",
            senderName = doc.getString("senderName").orEmpty(),
            text = doc.getString("text").orEmpty(),
            createdAt = doc.getTimestamp("createdAt")?.toDate(),
        )
    }
}

data class AdminNote(val id: String, val text: String, val by: String, val createdAt: Date?)

data class AuditEntry(
    val id: String,
    val actorId: String,
    val actorRole: String,
    val impersonatedBy: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val summary: String,
    val timestamp: Date?,
    val source: String,
) {
    companion object {
        fun from(doc: DocumentSnapshot) = AuditEntry(
            id = doc.id,
            actorId = doc.getString("actorId").orEmpty(),
            actorRole = doc.getString("actorRole").orEmpty(),
            impersonatedBy = doc.getString("impersonatedBy").orEmpty(),
            action = doc.getString("action").orEmpty(),
            entityType = doc.getString("entityType").orEmpty(),
            entityId = doc.getString("entityId").orEmpty(),
            summary = doc.getString("summary").orEmpty(),
            timestamp = doc.getTimestamp("timestamp")?.toDate(),
            source = doc.getString("source").orEmpty(),
        )
    }
}

data class AppConfig(
    val upiId: String = "",
    val payeeName: String = "",
    val amount: Long = 499,
    val qrImageUrl: String = "",
    val replyHours: Long = 4,
    val supportEmail: String = "supportandroidtester@gmail.com",
) {
    companion object {
        fun from(doc: DocumentSnapshot?) = if (doc == null || !doc.exists()) AppConfig() else AppConfig(
            upiId = doc.getString("upiId").orEmpty(),
            payeeName = doc.getString("payeeName").orEmpty(),
            amount = doc.getLong("amount") ?: 499,
            qrImageUrl = doc.getString("qrImageUrl").orEmpty(),
            replyHours = doc.getLong("replyHours") ?: 4,
            supportEmail = doc.getString("supportEmail") ?: "supportandroidtester@gmail.com",
        )
    }
}
