/**
 * Cloud Functions for Twelve Testers — see SPEC.md §3 (roles, impersonation),
 * §8 (server-side audit) and §9 (push + support email notifications).
 */
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore, DocumentData } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { setGlobalOptions, logger } from "firebase-functions/v2";
import { onDocumentCreated, onDocumentUpdated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { HttpsError, onCall, CallableRequest } from "firebase-functions/v2/https";
import { defineSecret, defineString } from "firebase-functions/params";
import * as nodemailer from "nodemailer";

initializeApp();
const db = getFirestore();

// Keep in sync with REGION in the Android app (data/Repository.kt & AuthRepository.kt).
const REGION = "asia-south1";
setGlobalOptions({ region: REGION, maxInstances: 10 });

const SUPPORT_EMAIL = defineString("SUPPORT_EMAIL", { default: "supportandroidtester@gmail.com" });
const SMTP_USER = defineString("SMTP_USER", { default: "supportandroidtester@gmail.com" });
const SMTP_PASSWORD = defineSecret("SMTP_PASSWORD");
const BOOTSTRAP_ADMIN_EMAILS = defineString("BOOTSTRAP_ADMIN_EMAILS", {
  description: "Comma-separated emails that become admin on first sign-in",
});

const STATUS_LABELS: Record<string, string> = {
  SUBMITTED: "Request submitted",
  PAYMENT_PENDING: "Payment pending",
  PAYMENT_SUBMITTED: "Payment submitted",
  PAYMENT_VERIFIED: "Payment verified",
  INTERNAL_VERIFICATION: "Internal verification",
  EMAILS_SHARED: "Tester emails shared",
  EMAILS_ADDED: "Emails added",
  TESTING_IN_PROGRESS: "Testing in progress",
  REPORT_READY: "Report ready",
  COMPLETED: "Completed",
  NEEDS_INFO: "Needs information",
  ON_HOLD: "On hold",
  REFUND_REQUESTED: "Refund requested",
  REFUNDED: "Refunded",
  CANCELLED: "Cancelled",
  REJECTED: "Rejected",
};
const label = (s?: string) => (s ? STATUS_LABELS[s] ?? s : "—");

// Fields that change on every chat message / read; not worth a separate audit entry.
const CHAT_FIELDS = new Set(["lastMessageAt", "lastMessageBy", "userLastReadAt", "adminLastReadAt", "updatedAt"]);
const NOISY_USER_FIELDS = new Set(["lastLoginAt", "fcmTokens"]);

// ---------------------------------------------------------------- helpers

async function audit(entry: {
  actorId?: string | null;
  actorRole?: string;
  impersonatedBy?: string | null;
  action: string;
  entityType: string;
  entityId: string;
  summary: string;
  before?: unknown;
  after?: unknown;
}) {
  await db.collection("auditLogs").add({
    actorId: entry.actorId ?? "",
    actorRole: entry.actorRole ?? "system",
    impersonatedBy: entry.impersonatedBy ?? null,
    action: entry.action,
    entityType: entry.entityType,
    entityId: entry.entityId,
    summary: entry.summary,
    before: entry.before ?? null,
    after: entry.after ?? null,
    timestamp: FieldValue.serverTimestamp(),
    source: "server",
  });
}

async function roleOf(uid?: string | null): Promise<string> {
  if (!uid) return "system";
  const snap = await db.collection("users").doc(uid).get();
  return snap.get("role") ?? "user";
}

function escapeHtml(s: unknown): string {
  return String(s ?? "").replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!,
  );
}

function requestTable(id: string, r: DocumentData): string {
  const rows: [string, unknown][] = [
    ["Request ID", id],
    ["Status", label(r.status)],
    ["Name", r.fullName],
    ["Email", r.contactEmail],
    ["WhatsApp", r.whatsapp],
    ["App", r.appName],
    ["Package", r.packageName],
    ["Opt-in link", r.optInLink],
    ["Note", r.statusNote],
  ];
  return (
    "<table cellpadding='6' style='border-collapse:collapse;font-family:sans-serif;font-size:14px'>" +
    rows
      .filter(([, v]) => v !== undefined && v !== null && v !== "")
      .map(([k, v]) => `<tr><td style='color:#64748b'>${k}</td><td><b>${escapeHtml(v)}</b></td></tr>`)
      .join("") +
    "</table>"
  );
}

async function emailSupport(subject: string, html: string) {
  const password = SMTP_PASSWORD.value();
  if (!password) {
    logger.warn("SMTP_PASSWORD not set; skipping email", { subject });
    return;
  }
  try {
    const transport = nodemailer.createTransport({
      service: "gmail",
      auth: { user: SMTP_USER.value(), pass: password },
    });
    await transport.sendMail({
      from: `Twelve Testers <${SMTP_USER.value()}>`,
      to: SUPPORT_EMAIL.value(),
      subject: `[12AT] ${subject}`,
      html,
    });
  } catch (e) {
    logger.error("Email failed", e);
  }
}

async function tokensFor(uids: string[]): Promise<Map<string, string[]>> {
  const out = new Map<string, string[]>();
  await Promise.all(
    uids.map(async (uid) => {
      const snap = await db.collection("users").doc(uid).get();
      out.set(uid, (snap.get("fcmTokens") as string[] | undefined) ?? []);
    }),
  );
  return out;
}

async function adminUids(): Promise<string[]> {
  const snap = await db.collection("users").where("role", "==", "admin").get();
  return snap.docs.map((d: { id: string }) => d.id);
}

/** Sends a push to the given users and prunes invalid tokens. */
async function push(uids: string[], title: string, body: string, data: Record<string, string> = {}) {
  const unique = [...new Set(uids)].filter(Boolean);
  if (unique.length === 0) return;
  const byUser = await tokensFor(unique);
  for (const [uid, tokens] of byUser) {
    if (tokens.length === 0) continue;
    try {
      const res = await getMessaging().sendEachForMulticast({
        tokens,
        notification: { title, body },
        data: { title, body, ...data },
        android: { priority: "high", notification: { channelId: "updates" } },
      });
      const bad = res.responses
        .map((r, i) => (!r.success && r.error?.code?.includes("registration-token") ? tokens[i] : null))
        .filter((t): t is string => t !== null);
      if (bad.length) {
        await db.collection("users").doc(uid).update({ fcmTokens: FieldValue.arrayRemove(...bad) });
      }
    } catch (e) {
      logger.error("Push failed", e);
    }
  }
}

function userMessageFor(status: string, r: DocumentData): string {
  switch (status) {
    case "PAYMENT_PENDING":
      return r.payment?.rejectReason ? `Payment not verified: ${r.payment.rejectReason}` : "Please complete the payment.";
    case "PAYMENT_VERIFIED":
      return "Payment received. We are reviewing your app details.";
    case "EMAILS_SHARED":
      return "We sent 12 tester emails to your WhatsApp. Add them and confirm in the app.";
    case "TESTING_IN_PROGRESS":
      return "Testing has started. Keep your closed test running for 14 days.";
    case "REPORT_READY":
      return "Your final report is ready.";
    case "COMPLETED":
      return "Closed testing completed! Your report is in the Completed tab.";
    case "NEEDS_INFO":
      return `We need a few corrections: ${r.statusNote ?? ""}`;
    case "REFUNDED":
      return "Your refund has been processed.";
    default:
      return `Status: ${label(status)}${r.statusNote ? ` — ${r.statusNote}` : ""}`;
  }
}

function requireAdmin(req: CallableRequest): string {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in required");
  if (req.auth.token.admin !== true) throw new HttpsError("permission-denied", "Admin only");
  return req.auth.uid;
}

// ---------------------------------------------------------------- users

export const onUserCreated = onDocumentCreated({ document: "users/{uid}", secrets: [SMTP_PASSWORD] }, async (event) => {
  const uid = event.params.uid;
  const u = event.data?.data() ?? {};
  const bootstrap = BOOTSTRAP_ADMIN_EMAILS.value()
    .split(",")
    .map((e) => e.trim().toLowerCase())
    .filter(Boolean);
  const email = String(u.email ?? "").toLowerCase();

  if (email && bootstrap.includes(email)) {
    const user = await getAuth().getUser(uid);
    await getAuth().setCustomUserClaims(uid, { ...(user.customClaims ?? {}), admin: true });
    await db.collection("users").doc(uid).update({ role: "admin" });
    await audit({ action: "role_bootstrap_admin", entityType: "user", entityId: uid, summary: `${email} became admin (bootstrap)` });
  }

  await audit({
    actorId: uid, actorRole: "user", action: "user_created", entityType: "user", entityId: uid,
    summary: `New user ${u.name ?? ""} <${u.email ?? ""}>`,
  });
  await emailSupport(
    `New user: ${u.name || u.email}`,
    `<p>A new user signed up.</p><p>Name: <b>${escapeHtml(u.name)}</b><br>Email: <b>${escapeHtml(u.email)}</b></p>`,
  );
});

export const onUserUpdated = onDocumentUpdated("users/{uid}", async (event) => {
  const before = event.data?.before.data() ?? {};
  const after = event.data?.after.data() ?? {};
  const changedKeys = Object.keys({ ...before, ...after }).filter(
    (k) => JSON.stringify(before[k]) !== JSON.stringify(after[k]) && !NOISY_USER_FIELDS.has(k),
  );
  if (changedKeys.length === 0) return;
  const pick = (o: DocumentData) => Object.fromEntries(changedKeys.map((k) => [k, o[k] ?? null]));
  await audit({
    actorId: event.params.uid, actorRole: after.role ?? "user", action: "user_updated",
    entityType: "user", entityId: event.params.uid,
    summary: `Changed: ${changedKeys.join(", ")}`, before: pick(before), after: pick(after),
  });
});

// ---------------------------------------------------------------- requests

export const onRequestWritten = onDocumentWritten(
  { document: "requests/{id}", secrets: [SMTP_PASSWORD] },
  async (event) => {
    const id = event.params.id;
    const before = event.data?.before.exists ? event.data.before.data()! : null;
    const after = event.data?.after.exists ? event.data.after.data()! : null;
    if (!after) return;

    const actorId: string = after.updatedBy ?? after.userId;
    const actorRole = await roleOf(actorId);
    const impersonatedBy: string | null = after.impersonatedBy ?? null;
    const admins = await adminUids();

    // Created
    if (!before) {
      await audit({
        actorId, actorRole, impersonatedBy, action: "request_created", entityType: "request", entityId: id,
        summary: `${after.appName} (${after.packageName})`, after,
      });
      await push(admins, "New request", `${after.fullName} • ${after.appName}`, { requestId: id });
      await emailSupport(`New request: ${after.appName}`, `<p>A new closed testing request was submitted.</p>${requestTable(id, after)}`);
      return;
    }

    const changedKeys = Object.keys({ ...before, ...after }).filter(
      (k) => JSON.stringify(before[k]) !== JSON.stringify(after[k]),
    );
    const meaningful = changedKeys.filter((k) => !CHAT_FIELDS.has(k) && k !== "updatedBy" && k !== "impersonatedBy");
    if (meaningful.length === 0) return;

    const pick = (o: DocumentData) => Object.fromEntries(meaningful.map((k) => [k, o[k] ?? null]));

    if (before.status !== after.status) {
      const summary = `${label(before.status)} → ${label(after.status)}${after.statusNote ? ` (${after.statusNote})` : ""}`;
      await audit({
        actorId, actorRole, impersonatedBy, action: "status_changed", entityType: "request", entityId: id,
        summary, before: pick(before), after: pick(after),
      });

      // Push: user hears about admin changes; admins hear about everything.
      if (actorRole === "admin") {
        await push([after.userId], `${after.appName}: ${label(after.status)}`, userMessageFor(after.status, after), { requestId: id });
      }
      await push(
        admins.filter((a) => a !== actorId),
        `${after.appName}: ${label(after.status)}`,
        `${after.fullName} • ${summary}`,
        { requestId: id },
      );
      await emailSupport(
        `${label(after.status)} — ${after.appName} (${after.fullName})`,
        `<p>Status changed by <b>${escapeHtml(actorRole)}</b>${impersonatedBy ? " (admin impersonating user)" : ""}: ${escapeHtml(summary)}</p>${requestTable(id, after)}` +
          (after.status === "PAYMENT_SUBMITTED" && after.payment?.screenshotUrl
            ? `<p><a href="${escapeHtml(after.payment.screenshotUrl)}">View payment screenshot</a>${after.payment?.utr ? ` • UTR: ${escapeHtml(after.payment.utr)}` : ""}</p>`
            : "") +
          (after.status === "REFUND_REQUESTED" ? `<p>Refund reason: <b>${escapeHtml(after.refund?.reason)}</b></p>` : ""),
      );
    } else {
      await audit({
        actorId, actorRole, impersonatedBy, action: "request_updated", entityType: "request", entityId: id,
        summary: `Changed: ${meaningful.join(", ")}`, before: pick(before), after: pick(after),
      });
      await emailSupport(
        `Request updated — ${after.appName}`,
        `<p>Fields changed: ${escapeHtml(meaningful.join(", "))}</p>${requestTable(id, after)}`,
      );
    }
  },
);

export const onMessageCreated = onDocumentCreated(
  { document: "requests/{id}/messages/{mid}", secrets: [SMTP_PASSWORD] },
  async (event) => {
    const id = event.params.id;
    const m = event.data?.data();
    if (!m) return;
    const reqSnap = await db.collection("requests").doc(id).get();
    const r = reqSnap.data() ?? {};

    await audit({
      actorId: m.senderId, actorRole: m.senderRole, impersonatedBy: m.impersonatedBy ?? null,
      action: "chat_message", entityType: "request", entityId: id,
      summary: String(m.text ?? "").slice(0, 200),
    });

    if (m.senderRole === "admin") {
      await push([r.userId], `Reply on ${r.appName ?? "your request"}`, String(m.text ?? ""), { requestId: id });
    } else {
      await push(await adminUids(), `Message from ${r.fullName ?? m.senderName}`, String(m.text ?? ""), { requestId: id });
    }
    await emailSupport(
      `Chat (${m.senderRole}) — ${r.appName ?? id}`,
      `<p><b>${escapeHtml(m.senderRole === "admin" ? "Admin" : m.senderName)}:</b> ${escapeHtml(m.text)}</p>${requestTable(id, r)}`,
    );
  },
);

// ---------------------------------------------------------------- callables

/** Admin signs in as a user. Returns a custom token carrying `impersonatedBy`. */
export const impersonateUser = onCall({ secrets: [SMTP_PASSWORD] }, async (req) => {
  const adminUid = requireAdmin(req);
  const target = String(req.data?.uid ?? "");
  if (!target) throw new HttpsError("invalid-argument", "uid required");
  if (target === adminUid) throw new HttpsError("invalid-argument", "Cannot impersonate yourself");

  const targetUser = await getAuth().getUser(target);
  if (targetUser.customClaims?.admin === true) {
    throw new HttpsError("permission-denied", "Cannot impersonate another admin");
  }
  const token = await getAuth().createCustomToken(target, { impersonatedBy: adminUid });
  await audit({
    actorId: adminUid, actorRole: "admin", action: "impersonation_started", entityType: "user", entityId: target,
    summary: `Admin signed in as ${targetUser.email ?? target}`,
  });
  await emailSupport(
    `Impersonation started: ${targetUser.email ?? target}`,
    `<p>Admin <b>${escapeHtml(req.auth?.token.email ?? adminUid)}</b> signed in as <b>${escapeHtml(targetUser.email ?? target)}</b>.</p>`,
  );
  return { token };
});

/** Returns the admin to their own account. Only valid from an impersonation session. */
export const endImpersonation = onCall(async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in required");
  const adminUid = req.auth.token.impersonatedBy as string | undefined;
  if (!adminUid) throw new HttpsError("failed-precondition", "Not impersonating");
  const adminUser = await getAuth().getUser(adminUid);
  if (adminUser.customClaims?.admin !== true) throw new HttpsError("permission-denied", "No longer an admin");

  const token = await getAuth().createCustomToken(adminUid);
  await audit({
    actorId: adminUid, actorRole: "admin", action: "impersonation_ended", entityType: "user", entityId: req.auth.uid,
    summary: `Admin returned from ${req.auth.token.email ?? req.auth.uid}`,
  });
  return { token };
});

/** Grant or revoke admin. */
export const setAdminRole = onCall({ secrets: [SMTP_PASSWORD] }, async (req) => {
  const adminUid = requireAdmin(req);
  const target = String(req.data?.uid ?? "");
  const makeAdmin = req.data?.admin === true;
  if (!target) throw new HttpsError("invalid-argument", "uid required");
  if (target === adminUid && !makeAdmin) throw new HttpsError("invalid-argument", "You cannot remove your own admin access");

  const user = await getAuth().getUser(target);
  const claims = { ...(user.customClaims ?? {}) } as Record<string, unknown>;
  if (makeAdmin) claims.admin = true;
  else delete claims.admin;
  await getAuth().setCustomUserClaims(target, claims);
  await db.collection("users").doc(target).update({ role: makeAdmin ? "admin" : "user" });

  await audit({
    actorId: adminUid, actorRole: "admin", action: makeAdmin ? "role_granted_admin" : "role_revoked_admin",
    entityType: "user", entityId: target, summary: `${user.email ?? target} ${makeAdmin ? "is now admin" : "is no longer admin"}`,
  });
  await emailSupport(
    `Role change: ${user.email ?? target}`,
    `<p>${escapeHtml(user.email ?? target)} ${makeAdmin ? "was made admin" : "had admin removed"} by ${escapeHtml(req.auth?.token.email ?? adminUid)}.</p>`,
  );
  return { ok: true };
});

/** Deletes the caller's auth account; profile is marked deleted and requests are kept for audit. */
export const deleteMyAccount = onCall({ secrets: [SMTP_PASSWORD] }, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in required");
  if (req.auth.token.impersonatedBy) throw new HttpsError("permission-denied", "Not allowed while impersonating");
  const uid = req.auth.uid;
  await db.collection("users").doc(uid).update({ deleted: true, fcmTokens: [] });
  await getAuth().deleteUser(uid);
  await audit({ actorId: uid, actorRole: "user", action: "account_deleted", entityType: "user", entityId: uid, summary: "User deleted their account" });
  await emailSupport(`Account deleted: ${req.auth.token.email ?? uid}`, `<p>User ${escapeHtml(req.auth.token.email ?? uid)} deleted their account.</p>`);
  return { ok: true };
});
