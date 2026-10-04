# Twelve Testers — Product Specification

| | |
|---|---|
| **Spec version** | 1.2.2 |
| **App version at this spec** | versionName `1.2.2` / versionCode `7` |
| **Package** | `com.satya.a12andriodtester` |
| **Last updated** | 2026-10-04 |
| **Status** | Approved v1 scope — remaining questions in §14 |

> **This file is the source of truth.** Every feature or code change must be checked against this spec first.
> If a change alters behaviour, update this spec in the same change. Every code change bumps the app version (see §13).

---

## 0. Internal Notes

Confidential business notes live in **`SPEC.internal.md`** (local only, git-ignored — the repo is public). Read it together with this spec; never put its content into the app UI, strings, store listing or committed files.

---

## 1. Overview

An Android app where a developer (User) submits a closed-testing request, pays, receives 12 tester emails, adds them to their Play Console closed test, and tracks progress until completion — when they receive a final report with production-readiness suggestions. Admins manage all requests and users.

## 2. Tech Stack

| Area | Choice |
|---|---|
| Platform | Android, Kotlin, minSdk 26, target/compileSdk 37 |
| UI | Jetpack Compose + Material 3, colourful professional theme (see §11) |
| Auth | Firebase Authentication — **Google Sign-In** and **Email/Password** only |
| Database | Cloud Firestore |
| Files (reports, payment screenshots, UPI QR) | Firebase Storage |
| Push notifications | Firebase Cloud Messaging (FCM) |
| Email notifications | Cloud Functions (nodemailer via Gmail SMTP) → support email |
| Server logic | Cloud Functions for Firebase (Node 22, TypeScript, region `asia-south1`) — needs **Blaze** plan |
| Backend | Firebase only (no other server) |

**Firebase project:** a **new dedicated project** (not `satya-digital` or `satya-splitwise`) so users, data and rules stay separate. Blaze is enabled per project, but the **same Cloud Billing account** that `satya-splitwise` uses can be linked to the new project — no new card needed. Set a budget alert (e.g. ₹100) on that billing account. Setup steps: `FIREBASE_SETUP.md`.

## 3. Roles

| Role | Description |
|---|---|
| **User** | Any signed-up developer. Sees only their own requests, chats, and reports. |
| **Admin** | One or more accounts. Sees **everything a user sees, for all users**, plus admin actions, and gets a different (admin) home screen. |

- Role is a Firebase Auth **custom claim** `admin: true`, set only by Cloud Functions, mirrored to `users/{uid}.role` for display. Enforced by Firestore/Storage Security Rules — never trusted from the client.
- **Bootstrap:** emails listed in the function parameter `BOOTSTRAP_ADMIN_EMAILS` become admin automatically on first sign-in.
- **Grant / revoke admin:** an admin can promote or demote any other user from the Users screen (cannot demote themselves).
- **Impersonation ("Login as user"):** an admin can sign in as any non-admin user to see exactly what they see and act on their behalf.
  - A callable function verifies the caller is admin and returns a Firebase **custom token** for the target user carrying the claim `impersonatedBy: <adminUid>`.
  - While impersonating, a red banner is always visible: "Viewing as <name> — Exit". Exit calls a function that verifies `impersonatedBy` is still an admin and signs the admin back in.
  - Start/stop of impersonation and every change made while impersonating are audited with the admin's uid.

## 4. Request Workflow (Status Flow)

Steps are shown as a **progress stepper at the top of the request screen** (and a compact progress bar on every request card).

| # | Status | Who moves it | Description |
|---|---|---|---|
| 1 | `SUBMITTED` | User | Request form submitted (§5.5). Moves straight to step 2. |
| 2 | `PAYMENT_PENDING` | System | **Scan & Pay** step: app shows the UPI QR code, UPI ID and amount ₹499. |
| 3 | `PAYMENT_SUBMITTED` | User | **Upload screenshot** step: user uploads the payment screenshot (+ optional UTR). Admin is notified. |
| 4 | `PAYMENT_VERIFIED` | Admin | Admin verifies the payment. If not received → back to `PAYMENT_PENDING` with a reason. |
| 5 | `INTERNAL_VERIFICATION` | Admin | Admin checks app details / opt-in link. |
| 6 | `EMAILS_SHARED` | Admin | 12 tester emails sent to the user's WhatsApp. |
| 7 | `EMAILS_ADDED` | **User confirms** | User confirms they added all 12 emails to the closed test. |
| 8 | `TESTING_IN_PROGRESS` | Admin | Testers opted in. Shows **Day X of 14**. **Refund no longer possible from here.** |
| 9 | `REPORT_READY` | Admin | Admin uploads the final report (PDF). |
| 10 | `COMPLETED` | Admin | Both user and admin see "Completed"; request moves to Completed. |

**Side states:**
- `NEEDS_INFO` — admin asks for corrections (with a message); user edits details and resubmits → returns to the step it was in.
- `ON_HOLD` — paused by admin (with a reason); admin resumes → returns to the step it was in.
- `REFUND_REQUESTED` — raised by the user (with reason) — see §4.2.
- `REFUNDED` — admin has returned the money (refund reference recorded). Final.
- `CANCELLED` — user cancelled before paying. Final.
- `REJECTED` — admin cannot serve the request (reason given). Final.

Rules:
- Each status transition is validated (only allowed next states; only the permitted role) in the app **and** in Security Rules.
- Every transition writes an audit record (§8) and triggers notifications (§9).
- Status history with timestamps and notes is displayed on the request screen.

### 4.1 Request Categories (tabs)
Users can have **multiple requests active at the same time**. Requests are grouped into tabs (user and admin):

| Tab | Statuses |
|---|---|
| **New** | `SUBMITTED`, `PAYMENT_PENDING`, `PAYMENT_SUBMITTED`, and `NEEDS_INFO`/`ON_HOLD` paused from these |
| **In Progress** | `PAYMENT_VERIFIED` → `REPORT_READY`, and `NEEDS_INFO`/`ON_HOLD` paused from these |
| **Completed** | `COMPLETED` |
| **Refunds & Closed** *(added)* | `REFUND_REQUESTED`, `REFUNDED`, `CANCELLED`, `REJECTED` |

### 4.2 Refund Rules
- A refund can be requested only **after payment** and **before testing starts**: statuses `PAYMENT_SUBMITTED`, `PAYMENT_VERIFIED`, `INTERNAL_VERIFICATION`, `EMAILS_SHARED`, `EMAILS_ADDED` (and `ON_HOLD`/`NEEDS_INFO` paused from one of these).
- Once the request reaches `TESTING_IN_PROGRESS`, **no refund is possible**. This note is shown on the payment screen, on the request screen, in Help/FAQ, and in the consent text of the request form.
- Admin either marks `REFUNDED` (enters refund reference) or **declines** (with reason) → request returns to the step it was in.
- Before payment the user can simply **Cancel** the request.

## 5. Screens

### 5.1 Splash Screen
- Shown **every time** the app is launched from a closed state.
- App icon on brand background, held ~1.2 s, using the Android SplashScreen API.

### 5.2 Welcome / Onboarding
- Shown on first launch (before login).
- Explains the full plan: what the service is, the steps, timelines (14-day testing), what the user must do, reply times, refunds, and the final report.
- First page leads with the core promise: **"Production-ready in 14–15 days"** — not a tester swap; we provide 12 dedicated testers.
- Swipeable pages; "Skip" and "Get Started".
- Always re-openable from **Help** (§5.9).

### 5.3 Login / Sign-up
- Google Sign-In (Credential Manager), Email/Password (sign up, sign in, forgot password, verification email sent on sign up).

### 5.4 Home / Main Screen (User)
- Gradient header with greeting and a "How it works" mini stepper.
- Tabs per §4.1; each request card shows app name, package, status chip, progress and "what to do next".
- Floating "New Request" button. Entry points: Help, Profile.

### 5.5 Submit Request Form
Fields (all required unless noted):
- Full name
- **WhatsApp number** (with country code, validated) — 12 tester emails are sent here
- Contact email (prefilled from auth)
- App name
- Package name (e.g. `com.example.app`, validated format)
- Closed testing opt-in link (validated URL)
- App category *(optional)*
- Notes *(optional)*
- Consent checkbox: terms & refund policy (§4.2)

Email, package name and opt-in link fields strip whitespace as you type (keyboards can auto-insert a space after "."); autocorrect is off for them. Same for the login email field.

The same form is used to **edit and resubmit** when the request is in `NEEDS_INFO`.

### 5.6 Payment (manual UPI, two steps)
- **Step A — Scan & Pay:** shows the UPI QR image, UPI ID, payee name and amount ₹499 (all from `config/app`, editable by admin in Admin Settings). Button "Pay with UPI app" opens a `upi://pay` intent as a shortcut. Refund note (§4.2) shown. Button "I have paid → Next".
- **Step B — Upload screenshot:** pick the screenshot from gallery, optional UTR / transaction ID, submit → `PAYMENT_SUBMITTED`; admin is notified (push + email).
- Admin views the screenshot and taps **Verify payment** or **Payment not received** (reason).

### 5.7 Completed Apps
- The **Completed** tab lists the user's completed requests.
- Open any to view/download the **final report** at any time.
- Admin sees the completed list for all users.

### 5.8 Chat (Two-way, asynchronous)
- One chat thread per request (user ↔ admin).
- Not real-time support: banner shows **"You will receive a reply within 4 hours."**
- Text messages.
- Unread indicator on request cards; push notification on new message.

### 5.9 Help
- Re-open welcome/onboarding guide.
- FAQ (incl. refund rule), contact support email, app version.

### 5.10 Profile
- Name, email, WhatsApp number (editable), sign out, delete account (Play policy requirement).

### 5.11 Admin Screens
- **Dashboard:** counts per tab, payments to verify, refund requests.
- **All Requests:** tabs as §4.1, search (name, app, package, email, WhatsApp).
- **Request Detail:** all user-entered data (same as user view) + admin actions: move to next step, verify/reject payment, needs info, hold/resume, approve/decline refund, reject, upload report, mark completed, internal notes.
- **Chats:** each request's chat, with unread indicator on the card.
- **Users:** list of all users with search; per user: **Login as user** (impersonate), **Make admin / Remove admin**.
- **Audit Log:** latest audit entries.
- **Settings:** UPI QR image, UPI ID, payee name, amount, reply hours.

## 6. Final Report
- Uploaded by admin (PDF) at `REPORT_READY`.
- Content: testing summary, crashes/bugs found, UX suggestions, production-readiness checklist, recommended fixes before applying for production.
- Available to user and admin permanently from the request screen.

## 7. Data Model (Firestore)

```
users/{uid}
  name, email, whatsapp, role ("user"|"admin"), photoUrl,
  fcmTokens[], createdAt, lastLoginAt, deleted

requests/{requestId}
  userId, fullName, whatsapp, contactEmail, appName, packageName,
  optInLink, category, notes,
  status, resumeStatus, statusNote,
  statusHistory[] {status, by, byRole, note, at},
  payment {amount, utr, screenshotUrl, submittedAt, verifiedBy, verifiedAt, rejectReason},
  refund {reason, requestedAt, fromStatus, reference, processedAt, declineReason},
  testingStartAt, reportUrl, reportUploadedAt,
  lastMessageAt, lastMessageBy, userLastReadAt, adminLastReadAt,
  updatedBy, impersonatedBy,
  createdAt, updatedAt, completedAt

requests/{requestId}/messages/{messageId}
  senderId, senderRole, senderName, text, createdAt, impersonatedBy

requests/{requestId}/adminNotes/{noteId}      (admin-only)
  text, by, createdAt

auditLogs/{logId}                              (append-only)
  actorId, actorRole, impersonatedBy, action, entityType, entityId,
  before, after, timestamp, appVersion, source ("app"|"server")

config/app                                     (admin-editable, readable by signed-in users)
  upiId, payeeName, amount, qrImageUrl, replyHours, supportEmail
```

## 8. Auditing
- **Everything is audited and saved in Firestore**: sign-up, login, logout, request create/edit, every status change, payment submission/verification, chat messages, report upload, completion, refunds, profile changes, role changes, impersonation, account deletion.
- `auditLogs` is **append-only** (security rules deny update/delete).
- Each entry records actor, role, action, before/after values, timestamp, app version, and `impersonatedBy` when applicable.
- Data changes (requests, messages, users) are audited **server-side** by Cloud Functions triggers so they cannot be skipped; session events (login, logout) are written by the app; impersonation and role changes by functions.

## 9. Notifications

| Event | User (push) | Admin (push) | Support email |
|---|---|---|---|
| New user signed up | | | ✓ |
| New request submitted | | ✓ | ✓ |
| Payment screenshot uploaded | | ✓ | ✓ |
| Any status change | ✓ | ✓ | ✓ |
| User confirms emails added | | ✓ | ✓ |
| Refund requested / processed | ✓ | ✓ | ✓ |
| New chat message | ✓ (from admin) | ✓ (from user) | ✓ |
| Report uploaded / completed | ✓ | ✓ | ✓ |
| Impersonation / role change | | | ✓ |

- Support email **`supportandroidtester@gmail.com`** receives all activity **with user details** (name, email, WhatsApp, app, package, status).
- Sent by Cloud Functions using Gmail SMTP with an **App Password** stored as the secret `SMTP_PASSWORD` (Blaze plan required).

## 10. Security & Privacy
- Firestore/Storage rules: users read/write only their own data; admin reads all; status transitions restricted by role.
- No secrets in the APK. The GitHub repo is **public**: `google-services.json`, signing keys, `functions/.env.*` (admin emails), `SPEC.internal.md` and the demo password are never committed.
- Privacy policy + data safety form required for Play Store.
- Account deletion supported (callable function deletes the auth account, marks profile deleted; request records kept for audit).

## 11. Design Guidelines
- **App name:** **Twelve Testers** (Play title: "Twelve Testers: Closed Testing"). Never use "Android" or "Google" in the app name or icon (Google brand rules).
- **Icon:** 12 white dots in a ring (twelve testers) around a check mark (test passed) on the indigo → violet → pink brand gradient. Single source: `store-listing/src/make_icon.py` generates the launcher, monochrome, notification and Play Store icons.
- **Store listing assets** (icon, feature graphic, phone/tablet screenshots, promo video, listing text) live in `store-listing/`; listing copy in `store-listing/listing.md`.
- **Core message** (listing, feature graphic, video, onboarding): *Not a tester swap — 12 real testers, production-ready in 14–15 days.* Never promise that Google will approve production access.
- Professional, **colourful** Material 3 look: vibrant indigo/violet/teal palette, gradient headers, rounded cards, colour-coded status chips, icons on every step.
- Light and dark theme.
- Edge-to-edge: bottom buttons, the New Request button and list ends always sit above the system navigation bar (3-button or gesture).
- Accessible contrast. v1 is English only.

## 12. Non-Functional
- Offline-tolerant (Firestore cache).
- Analytics via Firebase Analytics. Crashlytics to be added before Play release.

## 13. Versioning & Change Process (MANDATORY)
1. Before any change, read this spec and confirm the change aligns with it.
2. If behaviour changes, update the relevant spec section **and** bump the spec version.
3. **Every code change bumps the app version** in `app/build.gradle.kts`:
   - `versionCode` +1 (always)
   - `versionName` semantic: `MAJOR.MINOR.PATCH` — PATCH for fixes, MINOR for features, MAJOR for breaking/flow changes.
4. Add an entry to the Change Log below.

## 14. Decisions & Open Questions

**Decided (2026-10-02):**
- Admin: role-based, multiple admins allowed, admin home is different, admins see all users and can **impersonate** users (§3).
- Firebase: new dedicated project, linked to the existing Blaze billing account (§2). Owner creates it later; code is ready.
- Payment: manual UPI QR → screenshot upload → admin verification (§5.6).
- Refunds: allowed after payment until testing starts (§4.2). Testers are provided by the service, so tester drop-out is not modelled.
- Multiple active requests per user: yes, grouped into tabs (§4.1).
- Support email: `supportandroidtester@gmail.com`.

**Still open:**
- Google Play policy check on taking this payment outside Play Billing (before publishing).
- Privacy policy / terms URLs.

## 15. Change Log

| Date | Spec ver | App ver (name/code) | Change |
|---|---|---|---|
| 2026-10-02 | 1.0.0 | 1.0 / 1 | Initial spec created from owner requirements. |
| 2026-10-02 | 1.1.0 | 1.1.0 / 2 | Owner decisions applied (admin + impersonation, manual UPI payment, refunds, request tabs, support email). First implementation: Compose UI, Firebase Auth/Firestore/Storage/FCM, Cloud Functions, security rules. |
| 2026-10-03 | 1.1.1 | 1.1.1 / 3 | Release signing via git-ignored `keystore.properties` for Play Console internal testing. Backend deployed to `satya-andriodtester` (functions on firebase-admin 13). |
| 2026-10-03 | 1.1.2 | 1.1.2 / 4 | Failed actions (incl. Google sign-in) now log the full exception to logcat (tag `ActionRunner`) for debugging. Owner's email added to `BOOTSTRAP_ADMIN_EMAILS`. |
| 2026-10-03 | 1.2.0 | 1.2.0 / 5 | Renamed to **Twelve Testers**; new original icon (12-dot ring + check) replacing the Android robot; Play app-signing & upload key SHA-1s registered in Firebase. Store listing assets added under `store-listing/`. |
| 2026-10-03 | 1.2.1 | 1.2.1 / 6 | Whitespace stripped from email / package name / opt-in link fields (keyboard auto-space after "." bug); onboarding page 1 leads with "Production-ready in 14–15 days — not a tester swap". Store listing text, feature graphic, phone + 7"/10" tablet screenshots and a 42 s promo video (landscape + vertical) generated by scripts in `store-listing/src`. |
| 2026-10-04 | 1.2.2 | 1.2.2 / 7 | Fix: bottom buttons hidden by the system navigation bar — New Request FAB now sits above it; Submit / Pay / Save buttons no longer squashed (inset padding moved outside their fixed height); request, user and audit lists pad their last item above the bar. |
