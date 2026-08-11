# Ultra AutoDetector

Native Android app for user-controlled screen capture, template detection, licensed access, and explicit gesture controls.

## Run & Operate

### Android app

- Open `android-app/` in Android Studio with Android SDK platform 35.
- Add Firebase's `google-services.json` at `android-app/app/google-services.json` only for a configured Firebase project.
- Build with `./gradlew assembleDebug` from `android-app/`.
- Replit can validate Gradle configuration, but this container does not include the Android SDK, so APK compilation must run on an Android development machine.

When Firebase is not configured, the app automatically uses local demo mode:

- Regular account: any email and password with at least six characters. It starts as pending.
- Admin demo account: `admin@local.demo` with any password of at least six characters.
- Demo data is local-only and is not production authentication.

- `pnpm --filter @workspace/api-server run dev` — run the API server (port 5000)
- `pnpm run typecheck` — full typecheck across all packages
- `pnpm run build` — typecheck + build all packages
- `pnpm --filter @workspace/api-spec run codegen` — regenerate API hooks and Zod schemas from the OpenAPI spec
- `pnpm --filter @workspace/db run push` — push DB schema changes (dev only)
- Required env: `DATABASE_URL` — Postgres connection string

## Stack

- Android: Kotlin, Jetpack Compose, Android API 26–35
- Cloud boundary: Firebase Authentication, Firestore, and Storage
- Services: MediaProjection foreground capture, explicit AccessibilityService gestures, overlay controls

- pnpm workspaces, Node.js 24, TypeScript 5.9
- API: Express 5
- DB: PostgreSQL + Drizzle ORM
- Validation: Zod (`zod/v4`), `drizzle-zod`
- API codegen: Orval (from OpenAPI spec)
- Build: esbuild (CJS bundle)

## Where things live

- `android-app/app/src/main/java/com/ultra/autodetector/ui/` — Compose screens and state
- `android-app/app/src/main/java/com/ultra/autodetector/data/` — local demo and Firebase repository boundaries
- `android-app/app/src/main/java/com/ultra/autodetector/service/` — capture, gesture, and floating-widget services
- `android-app/app/src/main/java/com/ultra/autodetector/data/Models.kt` — account, license, template, and permission state
- `firebase/firestore.rules` and `firebase/storage.rules` — server-side access controls
- `docs/ultra-auto-detector-blueprint-report.md` — security and platform review of the uploaded blueprint

## Architecture decisions

- Firebase admin authorization is represented by trusted token claims in rules; the client never contains an administrator password.
- Local demo mode keeps the UI usable without cloud credentials and uses separate demo identities.
- Screen capture and overlay services start only after explicit user actions and permissions.
- MediaProjection authorization is held in memory for the current session instead of being serialized as a reusable token.
- License renewals extend from the later of the current expiration and now; admin actions are written to an immutable audit collection.

## Product

Users can sign in or create a pending account, review license status, grant device permissions, start/pause/stop screen detection, and request renewal through Telegram. Trusted administrators can approve/reject users and manage image templates.

## User preferences

- Preserve the native Kotlin Android architecture and avoid migrating the project to another stack.

## Gotchas

- A Firebase Android configuration is optional for local demo mode but required for cloud accounts and template storage.
- Before release, deploy and test both Firebase rules files with authenticated, unauthenticated, regular-user, and admin cases.
- Android SDK platform 35 is required to compile the app; Replit's current container does not provide it.

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details
