package com.satya.a12andriodtester.data

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class RequestForm(
    val fullName: String = "",
    val whatsapp: String = "",
    val contactEmail: String = "",
    val appName: String = "",
    val packageName: String = "",
    val optInLink: String = "",
    val category: String = "",
    val notes: String = "",
) {
    fun toMap() = mapOf(
        "fullName" to fullName.trim(),
        "whatsapp" to whatsapp.trim(),
        "contactEmail" to contactEmail.trim(),
        "appName" to appName.trim(),
        "packageName" to packageName.trim(),
        "optInLink" to optInLink.trim(),
        "category" to category.trim(),
        "notes" to notes.trim(),
    )

    companion object {
        fun from(r: TestRequest) = RequestForm(
            r.fullName, r.whatsapp, r.contactEmail, r.appName, r.packageName, r.optInLink, r.category, r.notes,
        )
    }
}

object Repository {
    private val db get() = FirebaseFirestore.getInstance()
    private val storage get() = FirebaseStorage.getInstance()
    private val functions get() = FirebaseFunctions.getInstance(FUNCTIONS_REGION)
    private val requests get() = db.collection("requests")

    private fun session() = AuthRepository.current ?: error("Not signed in")

    // ---------- Requests ----------

    fun myRequests(uid: String): Flow<List<TestRequest>?> =
        requests.whereEqualTo("userId", uid).live().map { snap ->
            snap?.documents?.map(TestRequest::from)?.sortedByDescending { it.createdAt }
        }

    fun allRequests(): Flow<List<TestRequest>?> =
        requests.orderBy("createdAt", Query.Direction.DESCENDING).live().map { snap ->
            snap?.documents?.map(TestRequest::from)
        }

    fun request(id: String): Flow<TestRequest?> =
        requests.document(id).live().map { snap -> snap?.takeIf { it.exists() }?.let(TestRequest::from) }

    private fun event(status: RequestStatus, note: String = ""): Map<String, Any?> {
        val s = session()
        return mapOf(
            "status" to status.name,
            "by" to s.uid,
            "byRole" to s.role,
            "note" to note,
            // serverTimestamp() is not allowed inside arrays.
            "at" to Timestamp.now(),
        )
    }

    private fun actorFields(): Map<String, Any?> {
        val s = session()
        return mapOf(
            "updatedAt" to FieldValue.serverTimestamp(),
            "updatedBy" to s.uid,
            "impersonatedBy" to s.impersonatedBy,
        )
    }

    suspend fun createRequest(form: RequestForm): String {
        val s = session()
        val doc = requests.document()
        val data = form.toMap() + actorFields() + mapOf(
            "userId" to s.uid,
            "status" to RequestStatus.PAYMENT_PENDING.name,
            "resumeStatus" to null,
            "statusNote" to "",
            "statusHistory" to listOf(event(RequestStatus.SUBMITTED), event(RequestStatus.PAYMENT_PENDING)),
            "payment" to mapOf("amount" to 0),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        doc.set(data).await()
        return doc.id
    }

    /** Edits details; when the request is in NEEDS_INFO it is resubmitted to the step it was in. */
    suspend fun updateDetails(req: TestRequest, form: RequestForm) {
        val update = form.toMap().toMutableMap<String, Any?>()
        update += actorFields()
        if (req.status == RequestStatus.NEEDS_INFO) {
            val back = req.resumeStatus ?: RequestStatus.PAYMENT_PENDING
            update["status"] = back.name
            update["resumeStatus"] = null
            update["statusNote"] = "Details updated by user"
            update["statusHistory"] = FieldValue.arrayUnion(event(back, "Resubmitted with updated details"))
        }
        requests.document(req.id).update(update).await()
    }

    /** Generic status change. [extra] carries step-specific fields. */
    suspend fun transition(
        req: TestRequest,
        to: RequestStatus,
        note: String = "",
        extra: Map<String, Any?> = emptyMap(),
    ) {
        val update = mutableMapOf<String, Any?>(
            "status" to to.name,
            "statusNote" to note,
            "statusHistory" to FieldValue.arrayUnion(event(to, note)),
        )
        update += actorFields()
        update += extra
        requests.document(req.id).update(update).await()
    }

    // ----- User actions -----

    suspend fun submitPayment(req: TestRequest, screenshot: Uri, utr: String, amount: Long) {
        val ref = storage.reference.child("payments/${req.userId}/${req.id}/${System.currentTimeMillis()}.jpg")
        ref.putFile(screenshot, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        val url = ref.downloadUrl.await().toString()
        transition(
            req, RequestStatus.PAYMENT_SUBMITTED, "Payment screenshot uploaded",
            mapOf(
                "payment" to mapOf(
                    "amount" to amount,
                    "utr" to utr.trim(),
                    "screenshotUrl" to url,
                    "submittedAt" to Timestamp.now(),
                    "rejectReason" to "",
                )
            ),
        )
    }

    suspend fun confirmEmailsAdded(req: TestRequest) =
        transition(req, RequestStatus.EMAILS_ADDED, "User confirmed all 12 emails were added")

    suspend fun cancel(req: TestRequest) = transition(req, RequestStatus.CANCELLED, "Cancelled by user")

    suspend fun requestRefund(req: TestRequest, reason: String) = transition(
        req, RequestStatus.REFUND_REQUESTED, reason,
        mapOf(
            "refund" to mapOf(
                "reason" to reason.trim(),
                "requestedAt" to Timestamp.now(),
                "fromStatus" to req.status.name,
            )
        ),
    )

    // ----- Admin actions -----

    suspend fun adminAdvance(req: TestRequest, to: RequestStatus, note: String = "") {
        val extra = mutableMapOf<String, Any?>()
        when (to) {
            RequestStatus.PAYMENT_VERIFIED -> {
                extra["payment.verifiedBy"] = session().uid
                extra["payment.verifiedAt"] = Timestamp.now()
            }
            RequestStatus.TESTING_IN_PROGRESS -> extra["testingStartAt"] = FieldValue.serverTimestamp()
            RequestStatus.COMPLETED -> extra["completedAt"] = FieldValue.serverTimestamp()
            else -> Unit
        }
        transition(req, to, note, extra)
    }

    suspend fun rejectPayment(req: TestRequest, reason: String) = transition(
        req, RequestStatus.PAYMENT_PENDING, reason, mapOf("payment.rejectReason" to reason.trim()),
    )

    /** NEEDS_INFO / ON_HOLD remember the step to return to. */
    suspend fun pause(req: TestRequest, to: RequestStatus, reason: String) =
        transition(req, to, reason, mapOf("resumeStatus" to req.status.name))

    suspend fun resume(req: TestRequest) {
        val back = req.resumeStatus ?: return
        transition(req, back, "Resumed", mapOf("resumeStatus" to null))
    }

    suspend fun approveRefund(req: TestRequest, reference: String) = transition(
        req, RequestStatus.REFUNDED, "Refunded. Ref: ${reference.trim()}",
        mapOf("refund.reference" to reference.trim(), "refund.processedAt" to Timestamp.now()),
    )

    suspend fun declineRefund(req: TestRequest, reason: String) {
        val back = req.refund?.fromStatus ?: RequestStatus.PAYMENT_VERIFIED
        transition(
            req, back, "Refund declined: ${reason.trim()}",
            mapOf("refund.declineReason" to reason.trim(), "refund.processedAt" to Timestamp.now()),
        )
    }

    suspend fun reject(req: TestRequest, reason: String) = transition(req, RequestStatus.REJECTED, reason)

    suspend fun uploadReport(req: TestRequest, pdf: Uri) {
        val ref = storage.reference.child("reports/${req.id}/report-${System.currentTimeMillis()}.pdf")
        ref.putFile(pdf, StorageMetadata.Builder().setContentType("application/pdf").build()).await()
        val url = ref.downloadUrl.await().toString()
        transition(
            req, RequestStatus.REPORT_READY, "Final report uploaded",
            mapOf("reportUrl" to url, "reportUploadedAt" to FieldValue.serverTimestamp()),
        )
    }

    // ---------- Chat ----------

    fun messages(requestId: String): Flow<List<ChatMessage>?> =
        requests.document(requestId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING).live()
            .map { snap -> snap?.documents?.map(ChatMessage::from) }

    suspend fun sendMessage(req: TestRequest, text: String) {
        val s = session()
        val batch = db.batch()
        val msg = requests.document(req.id).collection("messages").document()
        batch.set(
            msg, mapOf(
                "senderId" to s.uid,
                "senderRole" to s.role,
                "senderName" to s.displayName.ifBlank { s.email },
                "text" to text.trim(),
                "createdAt" to FieldValue.serverTimestamp(),
                "impersonatedBy" to s.impersonatedBy,
            )
        )
        val readField = if (s.isAdmin) "adminLastReadAt" else "userLastReadAt"
        batch.update(
            requests.document(req.id), mapOf(
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastMessageBy" to s.role,
                readField to FieldValue.serverTimestamp(),
            )
        )
        batch.commit().await()
    }

    fun markRead(req: TestRequest) {
        val s = AuthRepository.current ?: return
        if (s.impersonatedBy != null) return
        if (!req.unreadFor(s.isAdmin)) return
        val field = if (s.isAdmin) "adminLastReadAt" else "userLastReadAt"
        requests.document(req.id).update(field, FieldValue.serverTimestamp())
    }

    // ---------- Admin notes ----------

    fun adminNotes(requestId: String): Flow<List<AdminNote>?> =
        requests.document(requestId).collection("adminNotes")
            .orderBy("createdAt", Query.Direction.DESCENDING).live()
            .map { snap ->
                snap?.documents?.map {
                    AdminNote(it.id, it.getString("text").orEmpty(), it.getString("by").orEmpty(), it.getTimestamp("createdAt")?.toDate())
                }
            }

    suspend fun addAdminNote(requestId: String, text: String) {
        requests.document(requestId).collection("adminNotes").add(
            mapOf("text" to text.trim(), "by" to session().uid, "createdAt" to FieldValue.serverTimestamp())
        ).await()
    }

    // ---------- Users ----------

    fun user(uid: String): Flow<UserProfile?> =
        db.collection("users").document(uid).live().map { it?.takeIf { d -> d.exists() }?.let(UserProfile::from) }

    fun allUsers(): Flow<List<UserProfile>?> =
        db.collection("users").orderBy("createdAt", Query.Direction.DESCENDING).live()
            .map { snap -> snap?.documents?.map(UserProfile::from) }

    suspend fun updateMyProfile(name: String, whatsapp: String) {
        db.collection("users").document(session().uid)
            .update(mapOf("name" to name.trim(), "whatsapp" to whatsapp.trim())).await()
    }

    suspend fun setAdminRole(uid: String, admin: Boolean) {
        functions.getHttpsCallable("setAdminRole").call(mapOf("uid" to uid, "admin" to admin)).await()
    }

    // ---------- Config ----------

    fun config(): Flow<AppConfig> =
        db.collection("config").document("app").live().map { AppConfig.from(it) }

    suspend fun saveConfig(config: AppConfig, newQr: Uri?) {
        var qrUrl = config.qrImageUrl
        if (newQr != null) {
            val ref = storage.reference.child("config/upi-qr-${System.currentTimeMillis()}.jpg")
            ref.putFile(newQr, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
            qrUrl = ref.downloadUrl.await().toString()
        }
        db.collection("config").document("app").set(
            mapOf(
                "upiId" to config.upiId.trim(),
                "payeeName" to config.payeeName.trim(),
                "amount" to config.amount,
                "qrImageUrl" to qrUrl,
                "replyHours" to config.replyHours,
                "supportEmail" to config.supportEmail.trim(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "updatedBy" to session().uid,
            )
        ).await()
    }

    // ---------- Audit ----------

    fun auditLog(limit: Long = 300): Flow<List<AuditEntry>?> =
        db.collection("auditLogs").orderBy("timestamp", Query.Direction.DESCENDING).limit(limit).live()
            .map { snap -> snap?.documents?.map(AuditEntry::from) }
}
