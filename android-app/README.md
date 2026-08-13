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

Visible login and sign-up are user-only. Administrator authorization accepts
only the configured build-time email/hash and is available after tapping the
`ULTRA` logo six times. Administrators approve users; they cannot upload
or delete templates. Before release, replace local credentials with a trusted
server-side or managed provisioning path.

## Build

From a machine with Android SDK platform 35 and build tools installed:

```bash
./gradlew assembleDebug --no-daemon
```

The debug APK is written to
`app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions workflow also
uploads that file as `ultra-auto-detector-debug-apk` after every Android build
attempt. The project targets Android API 34 and compiles against platform 35;
the machine running the command must have Android SDK platform 35 and build
tools 35.0.0 installed.

For GitHub Actions builds, configure repository secrets named
`ULTRA_ADMIN_EMAIL` and `ULTRA_ADMIN_PASSWORD_HASH`. Replit secrets are not
automatically passed to GitHub Actions.

On the first authenticated visit for each local user, a full-screen setup gate
requires Accessibility, Draw Over Other Apps, and notification access. Start
and Stop remain hidden until this setup is complete. The Start button then
opens the MediaProjection consent flow for the current session.

Regular users cannot view the built-in template gallery. The gallery remains
available to administrators after they tap the visible ULTRA logo six times
and enter the configured administrator credentials.

## Safety boundary

Screen capture and gesture dispatch begin only after explicit user actions and
Android permission grants. Detection is bounded to the locally imported
templates. The app does not discover target apps, hide gestures, or attempt to
bypass anti-bot or security controls.