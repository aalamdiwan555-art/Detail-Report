# Ultra AutoDetector Android App

Native Kotlin Android implementation of the local, self-hosted blueprint.

## Included

- XML activities for authentication, dashboard, and admin control.
- Room database for users and local image templates.
- Admin template manager for uploading, previewing, refreshing, and deleting
  OpenCV detector image templates.
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

Administrator authorization accepts the configured build-time email/hash and
retains the local administrator fallback configured in `AdminConfig.kt` for
offline operation. Before release, replace local credentials with a trusted
server-side or managed provisioning path.

## Build

From a machine with Android SDK platform 35 and build tools installed:

```bash
./gradlew assembleDebug
```

The current Replit container has Java and Gradle but no Android SDK, so APK
compilation cannot be completed here. Install Android SDK platform 35 and build
tools, then run the command from `android-app/`. The project targets Android
API 34 and compiles against platform 35.

On first launch, grant Accessibility, Draw Over Other Apps, and battery
optimization exemption. Android 13+ also prompts for notifications; selected
OEMs expose an additional auto-start settings shortcut. The Start button stays
disabled until the three core background permissions are granted.

## Safety boundary

Screen capture and gesture dispatch begin only after explicit user actions and
Android permission grants. Detection is bounded to the locally imported
templates. The app does not discover target apps, hide gestures, or attempt to
bypass anti-bot or security controls.