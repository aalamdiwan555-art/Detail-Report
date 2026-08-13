# Ultra AutoDetector Android App

Native Kotlin Android implementation of the local, self-hosted blueprint.

## Included

- XML activities for authentication, detector controls, and admin approval.
- Room database for users and local approval notices only.
- Permanent built-in image templates loaded from
  `app/src/main/assets/templates/`.
- Encrypted session metadata with app-private built-in template storage.
- Pending/approved/rejected/expired/lifetime license handling.
- Admin actions for 1, 2, or 3 days, lifetime, and rejection.
- Explicit AccessibilityService, overlay, and MediaProjection permission flow.
- Foreground screen capture, OpenCV normalized-correlation matching at multiple
  scales, and user-requested accessibility gestures.
- Optional red detection rectangle overlay.

## Local mode

This build is fully offline and does not import Firebase or require a cloud
service. Data is private to the Android app sandbox and does not synchronize
between devices.

New accounts are pending until an administrator approves them. Passwords are
stored as salted SHA-256 hashes in the local Room database and the current
session is stored in Android `EncryptedSharedPreferences`.

Administrator authorization accepts the configured build-time email/hash and
retains the local administrator fallback configured in `AdminConfig.kt` for
offline operation. Administrators approve users; they cannot upload or delete
templates. Before release, replace local credentials with a trusted server-side
or managed provisioning path.

## Build

From a machine with Android SDK platform 35 and build tools installed:

```bash
./gradlew assembleDebug
```

The current Replit container has Java and Gradle but no Android SDK, so APK
compilation cannot be completed here. Install Android SDK platform 35 and build
tools, then run the command from `android-app/`. The project targets Android
API 34 and compiles against platform 35.

On first launch, grant Accessibility, Draw Over Other Apps, and notifications.
The Start button opens the Android permission flows it needs, then requests
MediaProjection consent for the current session.

## Safety boundary

Screen capture and gesture dispatch begin only after explicit user actions and
Android permission grants. Detection is bounded to the locally imported
templates. The app does not discover target apps, hide gestures, or attempt to
bypass anti-bot or security controls.