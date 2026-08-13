# Ultra AutoDetector

Native Kotlin Android app for offline user-controlled screen capture, local
template detection, license handling, and explicit gesture controls.

## Run & Operate

### Android app

- Open `android-app/` in Android Studio with Android SDK platform 35.
- The app uses a private on-device Room/SQLite database for accounts and
  approval notices. Detection templates are permanent app assets, not database
  records or synchronized uploads.
- Build with `./gradlew assembleDebug` from `android-app/` on a machine with Android SDK platform 35.
- Replit can validate Gradle configuration, but this container does not include the Android SDK, so APK compilation must run on an Android development machine.

The app currently uses local database mode:

- Regular accounts are created as pending and require admin approval.
- Accounts, licenses, and session state are stored in the device-local Room
  database. Local mode is not production authentication.

- `pnpm --filter @workspace/api-server run dev` — run the API server (port 5000)
- `pnpm run typecheck` — full typecheck across all packages
- `pnpm run build` — typecheck + build all packages
- `pnpm --filter @workspace/api-spec run codegen` — regenerate API hooks and Zod schemas from the OpenAPI spec
- `pnpm --filter @workspace/db run push` — push DB schema changes (dev only)
- Required env: `DATABASE_URL` — Postgres connection string

## Stack

- Android: Kotlin, Jetpack Compose, Android API 26–35
- Local boundary: Android SQLite database and app-private template files
- Local boundary: Room database, encrypted preferences, and app-private files
- Services: MediaProjection foreground capture, explicit AccessibilityService gestures, overlay controls

- pnpm workspaces, Node.js 24, TypeScript 5.9
- API: Express 5
- DB: PostgreSQL + Drizzle ORM
- Validation: Zod (`zod/v4`), `drizzle-zod`
- API codegen: Orval (from OpenAPI spec)
- Build: esbuild (CJS bundle)

## Where things live

- `android-app/app/src/main/java/com/ultra/autodetector/ui/` — XML activities and adapters
- `android-app/app/src/main/java/com/ultra/autodetector/data/` — Room database and encrypted local session boundaries
- `android-app/app/src/main/java/com/ultra/autodetector/service/` — accessibility
  detector, capture, and optional detection-overlay services
- `android-app/app/src/main/java/com/ultra/autodetector/data/local/` — account,
  notice, and encrypted preference state
- `android-app/app/src/main/assets/templates/` — permanent built-in image
  templates copied to app-private storage and cached as OpenCV grayscale Mats
- `docs/ultra-auto-detector-blueprint-report.md` — security and platform review of the uploaded blueprint

## Architecture decisions

- Visible login and sign-up create and authenticate regular user accounts only.
  Administrator access is intentionally separate: hold the `ULTRA` logo for six
  seconds, then enter the configured build-time credentials.
- Local database mode keeps the UI usable without cloud credentials.
- On a user's first authenticated visit, a full-screen onboarding gate requires
  accessibility, overlay, and notification permissions before detector controls
  are revealed. Completion is tracked per local user.
- Screen capture and overlay services start only after explicit user actions and permissions.
- Regular users see account details, permission readiness, and detector controls;
  built-in templates remain hidden from them. Administrators can view templates
  in the main screen when needed.
- Auto-click gestures run through the sticky foreground AccessibilityService with
  a persistent notification and an 800 ms cooldown.
- Template matching runs through OpenCV at 0.70x, 0.85x, 1.0x, and 1.15x scales.
- MediaProjection authorization is held in memory for the current session instead of being serialized as a reusable token.
- License renewals extend from the later of the current expiration and now; admin actions are written to Room.

## Product

Users can sign in or create a pending account, complete one-time device
permission setup, review license status, and start/stop screen detection.
Trusted administrators enter through the six-second logo gesture and can
approve/reject users; built-in template assets are not managed at runtime.

## User preferences

- Preserve the native Kotlin Android architecture and avoid migrating the project to another stack.

## Gotchas

- Before release, replace local admin provisioning with a trusted server-side or managed provisioning path.
- Android SDK platform 35 is required to compile the app; Replit's current container does not provide it.

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details
