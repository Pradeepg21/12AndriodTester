# Firebase Setup — Twelve Testers

One-time steps to connect the app to a new Firebase project (SPEC.md §2).

## 1. Create the project
1. Firebase Console → **Add project** (e.g. `satya-12-tester`). Analytics on.
2. **Upgrade to Blaze** → choose the **existing billing account** used by `satya-splitwise`.
   Then Google Cloud Console → Billing → Budgets → add a budget alert (e.g. ₹100).

## 2. Add the Android app
1. Project settings → Add app → Android, package `com.satya.a12andriodtester`.
2. Add your **SHA-1** and **SHA-256** (needed for Google Sign-In):
   `./gradlew signingReport` (debug) — add the Play App Signing SHA later for release.
3. Download `google-services.json` → put it in `app/` (it is git-ignored).
   Do this **after** enabling Google sign-in (step 3) so the web client ID is included.

## 3. Authentication
Authentication → Sign-in method → enable **Email/Password** and **Google**.

## 4. Firestore & Storage
1. Firestore → Create database → **Production mode**, location **asia-south1 (Mumbai)**.
2. Storage → Get started → same location.
3. The region `asia-south1` must match `REGION` in `functions/src/index.ts` and `FUNCTIONS_REGION` in `app/.../data/FirebaseFlows.kt`.

## 5. Support email (Gmail App Password)
1. Sign in to `supportandroidtester@gmail.com` → Google Account → Security → turn on **2-Step Verification**.
2. Security → **App passwords** → create one named "12AT Functions" → copy the 16-character password.

## 6. Deploy backend
```bash
npm install -g firebase-tools
firebase login
# put your project ID in .firebaserc (replace REPLACE-WITH-YOUR-PROJECT-ID)
cd functions && npm install && cd ..
firebase functions:secrets:set SMTP_PASSWORD      # paste the Gmail app password
firebase deploy --only firestore:rules,storage,functions
```
On first deploy the CLI asks for:
- `BOOTSTRAP_ADMIN_EMAILS` → your admin email(s), comma-separated (they become admin on first sign-in)
- `SUPPORT_EMAIL` / `SMTP_USER` → press Enter to keep `supportandroidtester@gmail.com`

When asked to grant Storage rules access to Firestore (cross-service rules), answer **Yes**.

### Impersonation permission
"Login as user" creates custom tokens, which needs the functions' service account to sign tokens:
Google Cloud Console → IAM → find `<project-number>-compute@developer.gserviceaccount.com` → add role
**Service Account Token Creator**.

## 7. First run
1. Run the app, sign in with the bootstrap admin email → the admin dashboard appears
   (the role is picked up automatically within a few seconds).
2. Admin → **Settings**: upload the UPI QR image, set UPI ID, payee name, amount (499), reply hours (4).
3. Sign in with a second (user) account and submit a test request end-to-end.
