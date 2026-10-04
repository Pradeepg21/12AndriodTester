package com.satya.a12andriodtester.data

/** Request categories shown as tabs — SPEC.md §4.1. */
enum class RequestCategory(val label: String) {
    NEW("New"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    CLOSED("Refunds & Closed"),
}

/** Request workflow — SPEC.md §4. [step] is the 1-based position in the main flow, 0 for side states. */
enum class RequestStatus(val step: Int, val label: String, val userHint: String, val adminHint: String) {
    SUBMITTED(1, "Request submitted", "Your request has been submitted.", "New request."),
    PAYMENT_PENDING(2, "Payment pending", "Scan the UPI QR and pay to continue.", "Waiting for the user to pay."),
    PAYMENT_SUBMITTED(3, "Payment submitted", "We are verifying your payment.", "Verify the payment screenshot."),
    PAYMENT_VERIFIED(4, "Payment verified", "Payment received. We are reviewing your app details.", "Start internal verification."),
    INTERNAL_VERIFICATION(5, "Internal verification", "We are verifying your app details.", "Verify details, then share tester emails on WhatsApp."),
    EMAILS_SHARED(6, "Tester emails shared", "We sent 12 tester emails to your WhatsApp. Add them to your closed test and confirm.", "Waiting for the user to add the emails."),
    EMAILS_ADDED(7, "Emails added", "Thanks! Our testers will opt in shortly.", "User added the emails. Start testing."),
    TESTING_IN_PROGRESS(8, "Testing in progress", "Testers are active. Keep your closed test running for 14 days.", "Testing running. Upload the report when done."),
    REPORT_READY(9, "Report ready", "Your final report is ready.", "Report uploaded. Mark the request completed."),
    COMPLETED(10, "Completed", "Closed testing completed. Download your report anytime.", "Completed."),

    NEEDS_INFO(0, "Needs information", "We need a few corrections. Edit your request and resubmit.", "Waiting for the user to update details."),
    ON_HOLD(0, "On hold", "Your request is on hold.", "On hold. Resume when ready."),
    REFUND_REQUESTED(0, "Refund requested", "Your refund request is being reviewed.", "Approve or decline the refund."),
    REFUNDED(0, "Refunded", "Your payment has been refunded.", "Refunded."),
    CANCELLED(0, "Cancelled", "This request was cancelled.", "Cancelled by user."),
    REJECTED(0, "Rejected", "This request could not be accepted.", "Rejected.");

    val isMainFlow get() = step > 0

    companion object {
        val mainFlow = entries.filter { it.isMainFlow }

        fun from(value: String?): RequestStatus =
            entries.firstOrNull { it.name == value } ?: SUBMITTED

        /** Statuses from which a refund may be requested — SPEC.md §4.2. */
        val refundable = setOf(PAYMENT_SUBMITTED, PAYMENT_VERIFIED, INTERNAL_VERIFICATION, EMAILS_SHARED, EMAILS_ADDED)

        /** Statuses from which the user may cancel (before paying). */
        val cancellable = setOf(SUBMITTED, PAYMENT_PENDING)

        /** Admin "move to next step" transitions in the main flow. */
        val adminNext = mapOf(
            PAYMENT_SUBMITTED to PAYMENT_VERIFIED,
            PAYMENT_VERIFIED to INTERNAL_VERIFICATION,
            INTERNAL_VERIFICATION to EMAILS_SHARED,
            EMAILS_ADDED to TESTING_IN_PROGRESS,
        )
    }
}

fun categoryOf(status: RequestStatus, resumeStatus: RequestStatus?): RequestCategory = when (status) {
    RequestStatus.COMPLETED -> RequestCategory.COMPLETED
    RequestStatus.REFUND_REQUESTED, RequestStatus.REFUNDED,
    RequestStatus.CANCELLED, RequestStatus.REJECTED -> RequestCategory.CLOSED
    RequestStatus.NEEDS_INFO, RequestStatus.ON_HOLD ->
        categoryOf(resumeStatus ?: RequestStatus.SUBMITTED, null)
    else -> if (status.step <= RequestStatus.PAYMENT_SUBMITTED.step) RequestCategory.NEW else RequestCategory.IN_PROGRESS
}

/** Whether the request (possibly paused) can still be refunded. */
fun isRefundable(status: RequestStatus, resumeStatus: RequestStatus?): Boolean =
    status in RequestStatus.refundable ||
        ((status == RequestStatus.NEEDS_INFO || status == RequestStatus.ON_HOLD) && resumeStatus in RequestStatus.refundable)
