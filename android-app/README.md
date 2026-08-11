# Ultra AutoDetector Android App

Native Kotlin Android implementation of the uploaded Ultra AutoDetector blueprint.

## What is included

- Kotlin + Jetpack Compose Android app.
- Private on-device SQLite database mode for development without cloud credentials.
- Firebase Authentication, Firestore, and Storage repository boundary.
- Account and license states.
- Renewal message handoff to Telegram.
- Permission dashboard for accessibility, overlay, notifications, and screen capture.
- Foreground MediaProjection capture lifecycle.
- Accessibility gesture service with explicit user-requested dispatch.
- Draggable floating widget with pause and stop controls.
- Admin view with license actions and template upload/delete flow.

## Local database mode

The default repository is a private SQLite database stored in the Android
application sandbox. Users, licenses, templates, session state, and
permission state survive app restarts without Firebase, Supabase, cloud
credentials, or network access.

This database is device-local. It does not synchronize users across devices
and is not a replacement for a trusted production server.

Demo accounts:

- Regular user: any valid email plus a password with at least eight characters.
- Administrator: `admin@local.demo` / `UltraAdmin!26`.
- Seeded operator account: `active@local.demo` / `ActiveUser!26`.

The local administrator credential is fixed and development-only. It must not
be used as production authentication.

These are demo-only accounts and are not production credentials.

## Optional Firebase adapter

`FirebaseRepository` remains available as an optional cloud adapter for a
future server-backed build. The default `RepositoryProvider` selects
`LocalDatabaseRepository`, so Firebase setup is not required for the current
app.

## Build

From a machine with Android SDK platform 35 and build tools installed:

```bash
./gradlew assembleDebug
```

The current Replit environment has Java and Gradle but does not have an Android SDK, so the APK build could not be completed here.

## Safety boundary

The capture and gesture services start only after explicit user action and permission grants. The app does not discover target applications, send hidden gestures, or attempt to bypass anti-bot or security controls.