# Ultra AutoDetector Android App

Native Kotlin Android implementation of the uploaded Ultra AutoDetector blueprint.

## What is included

- Kotlin + Jetpack Compose Android app.
- Secure local demo mode for development without cloud credentials.
- Firebase Authentication, Firestore, and Storage repository boundary.
- Account and license states.
- Renewal message handoff to Telegram.
- Permission dashboard for accessibility, overlay, notifications, and screen capture.
- Foreground MediaProjection capture lifecycle.
- Accessibility gesture service with explicit user-requested dispatch.
- Draggable floating widget with pause and stop controls.
- Admin view with license actions and template upload/delete flow.

## Local demo mode

When no Firebase configuration is present, the app uses an offline local repository. This lets the UI and core flows run without an administrator password or production credentials.

Demo accounts:

- Regular user: any valid email plus a six-character password.
- Administrator: `admin@local.demo` plus a six-character password.

These are demo-only accounts and are not production credentials.

## Firebase setup

1. Create a Firebase Android app for package `com.ultra.autodetector`.
2. Place the Firebase-provided `google-services.json` at `app/google-services.json`.
3. Add the Google Services Gradle plugin only when the file is available.
4. Deploy and test the rules in `firebase/firestore.rules` and `firebase/storage.rules`.
5. Provision administrator access through trusted Firebase custom claims or a protected backend. Do not put an administrator password in the app.

## Build

From a machine with Android SDK platform 35 and build tools installed:

```bash
./gradlew assembleDebug
```

The current Replit environment has Java and Gradle but does not have an Android SDK, so the APK build could not be completed here.

## Safety boundary

The capture and gesture services start only after explicit user action and permission grants. The app does not discover target applications, send hidden gestures, or attempt to bypass anti-bot or security controls.