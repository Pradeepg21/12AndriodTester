# Project Rules

- `SPEC.md` is the source of truth. Read it before every change and keep it in sync with any behaviour change.
- Every code change must bump the version in `app/build.gradle.kts` (`versionCode` +1, `versionName` per semver), update the version in the `SPEC.md` header, and add a row to the Change Log in `SPEC.md` §15. The new `versionCode` must be higher than **Last versionCode uploaded to Play** in `SPEC.md` §13 — Play rejects reused codes.
- Internal notes (`SPEC.md` §0) live in git-ignored `SPEC.internal.md` — read it too. Never put its content into user-facing UI, strings, store listing, or any committed file (the GitHub repo is public).
