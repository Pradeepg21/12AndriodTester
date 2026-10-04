// Seeds a demo user + sample requests into the live project for store screenshots.
// Usage (project root, after any `firebase` command so the CLI token is fresh):
//   DEMO_PASSWORD=... node store-listing/src/seed_demo.mjs          create / refresh demo data
//   node store-listing/src/seed_demo.mjs --delete remove demo requests (user doc + auth user too)
import { readFileSync } from "node:fs";
import { homedir } from "node:os";

const PROJECT = "satya-andriodtester";
const DEMO_EMAIL = "demo.developer@example.com";
const DEMO_PASSWORD = process.env.DEMO_PASSWORD; // kept out of the public repo
if (!DEMO_PASSWORD) throw new Error("set DEMO_PASSWORD (see SPEC.internal.md)");
const DEMO_NAME = "Riya Sharma";
const DEMO_WHATSAPP = "+919800000000";

const token = JSON.parse(readFileSync(`${homedir()}/.config/configstore/firebase-tools.json`, "utf8")).tokens.access_token;
const apiKey = JSON.parse(readFileSync("app/google-services.json", "utf8")).client[0].api_key[0].current_key;
const DOCS = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents`;
const auth = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };

const DAY = 86_400_000;
const ago = (days, hours = 0) => new Date(Date.now() - days * DAY - hours * 3_600_000);

// ---- Firestore REST value encoding
function enc(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (v instanceof Date) return { timestampValue: v.toISOString() };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (typeof v === "string") return { stringValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(enc) } };
  return { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, enc(x)])) } };
}
async function call(method, url, body, headers = auth) {
  const res = await fetch(url, { method, headers, body: body && JSON.stringify(body) });
  const json = await res.json().catch(() => ({}));
  if (!res.ok && !(method === "DELETE" && res.status === 404)) throw new Error(`${method} ${url}: ${res.status} ${JSON.stringify(json)}`);
  return json;
}
const put = (path, data) => call("PATCH", `${DOCS}/${path}`, { fields: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, enc(v)])) });

// ---- Demo auth user (email/password)
async function demoUid() {
  const idt = `https://identitytoolkit.googleapis.com/v1/accounts`;
  const h = { "Content-Type": "application/json" };
  try {
    const r = await call("POST", `${idt}:signInWithPassword?key=${apiKey}`, { email: DEMO_EMAIL, password: DEMO_PASSWORD, returnSecureToken: true }, h);
    return r.localId;
  } catch {
    const r = await call("POST", `${idt}:signUp?key=${apiKey}`, { email: DEMO_EMAIL, password: DEMO_PASSWORD, displayName: DEMO_NAME, returnSecureToken: true }, h);
    await call("POST", `${idt}:update?key=${apiKey}`, { idToken: r.idToken, displayName: DEMO_NAME, returnSecureToken: false }, h);
    return r.localId;
  }
}

const FLOW = ["SUBMITTED", "PAYMENT_PENDING", "PAYMENT_SUBMITTED", "PAYMENT_VERIFIED", "INTERNAL_VERIFICATION",
  "EMAILS_SHARED", "EMAILS_ADDED", "TESTING_IN_PROGRESS", "REPORT_READY", "COMPLETED"];
const NOTES = {
  PAYMENT_VERIFIED: "Payment received — thank you!",
  INTERNAL_VERIFICATION: "Checking app details and opt-in link",
  EMAILS_SHARED: "Tester emails sent",
  TESTING_IN_PROGRESS: "All 12 testers opted in",
  REPORT_READY: "Final report uploaded",
  COMPLETED: "Closed testing completed",
};
const USER_STEPS = new Set(["SUBMITTED", "PAYMENT_SUBMITTED", "EMAILS_ADDED"]);

function history(uid, upTo, startDaysAgo) {
  const n = FLOW.indexOf(upTo) + 1;
  return FLOW.slice(0, n).map((status, i) => {
    const user = USER_STEPS.has(status) || status === "PAYMENT_PENDING";
    return { status, by: user ? uid : "demo-admin", byRole: user ? "user" : "admin", note: NOTES[status] ?? "",
      at: ago(Math.max(startDaysAgo - i * (startDaysAgo / Math.max(n, 1)), 0.05)) };
  });
}

function request(uid, { id, appName, pkg, category, status, startDaysAgo, extra = {} }) {
  const paid = FLOW.indexOf(status) >= FLOW.indexOf("PAYMENT_SUBMITTED");
  return [`requests/${id}`, {
    userId: uid, fullName: DEMO_NAME, whatsapp: DEMO_WHATSAPP, contactEmail: DEMO_EMAIL,
    appName, packageName: pkg, optInLink: `https://play.google.com/apps/testing/${pkg}`,
    category, notes: "", status, resumeStatus: null, statusNote: NOTES[status] ?? "",
    statusHistory: history(uid, status, startDaysAgo),
    payment: paid ? { amount: 499, utr: "DEMO" + id.length, screenshotUrl: "", submittedAt: ago(startDaysAgo - 0.2), rejectReason: "" } : { amount: 0 },
    createdAt: ago(startDaysAgo), updatedAt: ago(0, 2), updatedBy: uid, impersonatedBy: null,
    ...extra,
  }];
}

async function seed() {
  const uid = await demoUid();
  await put(`users/${uid}`, { name: DEMO_NAME, email: DEMO_EMAIL, whatsapp: DEMO_WHATSAPP, role: "user", photoUrl: "",
    fcmTokens: [], createdAt: ago(20), lastLoginAt: ago(0, 1), deleted: false });

  const reqs = [
    request(uid, { id: "demo-fittrack", appName: "FitTrack – Workout Log", pkg: "com.riyasharma.fittrack", category: "Health & Fitness",
      status: "TESTING_IN_PROGRESS", startDaysAgo: 12,
      extra: { testingStartAt: ago(8, 3), lastMessageAt: ago(0, 3), lastMessageBy: "admin", userLastReadAt: ago(0, 5), adminLastReadAt: ago(0, 3) } }),
    request(uid, { id: "demo-budgetbuddy", appName: "Budget Buddy", pkg: "com.riyasharma.budgetbuddy", category: "Finance",
      status: "COMPLETED", startDaysAgo: 19,
      extra: { testingStartAt: ago(16), reportUrl: "https://example.com/sample-report.pdf", reportUploadedAt: ago(1), completedAt: ago(1) } }),
    request(uid, { id: "demo-recipebox", appName: "Recipe Box", pkg: "com.riyasharma.recipebox", category: "Food & Drink",
      status: "EMAILS_SHARED", startDaysAgo: 2 }),
    request(uid, { id: "demo-quicknotes", appName: "QuickNotes", pkg: "com.riyasharma.quicknotes", category: "Productivity",
      status: "PAYMENT_PENDING", startDaysAgo: 0.1 }),
  ];
  for (const [path, data] of reqs) await put(path, data);

  const chat = [
    ["user", "Hi! I've added all 12 emails to my closed test track. Anything else I need to do?", 7],
    ["admin", "Perfect, thanks Riya. All 12 testers have opted in — your 14-day window is running. Keep the closed test active and don't remove anyone.", 6.8],
    ["user", "Got it. I pushed a small bug-fix build today, is that okay?", 0.3],
    ["admin", "Yes, updates are fine and actually help. Testers will pick it up automatically. We'll include it in your final report.", 0.12],
  ];
  for (const [i, [role, text, days]] of chat.entries()) {
    await put(`requests/demo-fittrack/messages/demo-${i}`, {
      senderId: role === "user" ? uid : "demo-admin", senderRole: role, senderName: role === "user" ? DEMO_NAME : "Support team",
      text, createdAt: ago(days), impersonatedBy: null });
  }
  console.log(`seeded demo user ${uid} (${DEMO_EMAIL} / ${DEMO_PASSWORD}) with ${reqs.length} requests`);
}

async function remove() {
  for (const id of ["demo-fittrack", "demo-budgetbuddy", "demo-recipebox", "demo-quicknotes"]) {
    for (let i = 0; i < 4; i++) await call("DELETE", `${DOCS}/requests/${id}/messages/demo-${i}`);
    await call("DELETE", `${DOCS}/requests/${id}`);
  }
  console.log("demo requests deleted — delete the demo user in Firebase Console → Authentication if no longer needed");
}

await (process.argv.includes("--delete") ? remove() : seed());
