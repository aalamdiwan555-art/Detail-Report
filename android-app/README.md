# Ultra AutoDetector Android App

Native Kotlin Android implementation of the local, self-hosted blueprint.

## Included

- XML activities for authentication, dashboard, and admin control.
- Room database for users and local image templates.
- Encrypted session metadata with app-private template storage.
- Pending/approved/rejected/expired/lifetime license handling.
- Admin actions for 1, 2, or 3 days, lifetime, and rejection.
- Telegram renewal handoff with pre-filled account and device details.
- Explicit AccessibilityService, overlay, and MediaProjection permission flow.
- Foreground screen capture, bounded normalized-correlation bitmap matching, and
  user-requested accessibility gestures.
- Configured multilingual approval-text detection through the accessibility
  tree, including English, Hindi, Gujarati, Marathi, Bengali, Tamil, Telugu,
  Kannada, Malayalam, Punjabi, Urdu, and Odia variants.
- Draggable floating pause/stop control.

## Local mode

This build is fully offline and does not import Firebase or require a cloud
service. Data is private to the Android app sandbox and does not synchronize
between devices.

New accounts are pending until an administrator approves them. Passwords are
stored as salted SHA-256 hashes in the local Room database and the current
session is stored in Android `EncryptedSharedPreferences`.

Administrator authorization is provisioned at build time rather than stored as
plain-text credential material in source control. Supply `ULTRA_ADMIN_EMAIL`
and `ULTRA_ADMIN_PASSWORD_HASH` as build environment variables (the password
hash is `SHA-256("ultra_salt_2024" + password)`) before the release build.
If those values are absent, administrator access is intentionally disabled.

## Build

From a machine with Android SDK platform 35 and build tools installed:

```bash
./gradlew assembleDebug
```

The current Replit container has Java and Gradle but no Android SDK, so APK
compilation cannot be completed here. Install Android SDK platform 35 and build
tools, then run the command from `android-app/`. The project targets Android
API 34 and compiles against platform 35.

## Safety boundary

Screen capture and gesture dispatch begin only after explicit user actions and
Android permission grants. Detection is bounded to the locally imported
templates. The app does not discover target apps, hide gestures, or attempt to
bypass anti-bot or security controls.