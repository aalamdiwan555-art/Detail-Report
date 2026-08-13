# Ultra AutoDetector implementation

This document describes the fully offline native Android implementation. The
app does not use Firebase, Supabase, Google APIs, cloud auth, or API keys.

## Product scope

- Local account registration and sign-in with pending/approved/rejected/expired
  license states.
- Room-backed administrator panel for approvals, expiry editing, deletion,
  notices, counters, filters, and CSV export.
- Encrypted local session and permission metadata.
- Explicit Android permissions for screen capture, overlay, notifications, and
  accessibility gestures.
- Foreground MediaProjection capture, bounded template matching, and a
  draggable pause/stop widget.

## Source map

| Area | Implementation |
| --- | --- |
| Local account entity | `android-app/app/src/main/java/com/ultra/autodetector/data/local/UserEntity.kt` |
| Local notices | `android-app/app/src/main/java/com/ultra/autodetector/data/local/NoticeEntity.kt` |
| Room database | `android-app/app/src/main/java/com/ultra/autodetector/data/local/AppDatabase.kt` |
| Encrypted preferences | `android-app/app/src/main/java/com/ultra/autodetector/data/local/EncryptedPrefsManager.kt` |
| Offline auth | `android-app/app/src/main/java/com/ultra/autodetector/data/repository/AuthRepository.kt` |
| Admin provisioning boundary | `android-app/app/src/main/java/com/ultra/autodetector/data/repository/AdminConfig.kt` |
| Capture/image adapter | `android-app/app/src/main/java/com/ultra/autodetector/opencv/OpenCvManager.kt` |
| Screen capture | `android-app/app/src/main/java/com/ultra/autodetector/service/DetectionService.kt` |
| Accessibility gestures | `android-app/app/src/main/java/com/ultra/autodetector/service/AutoClickService.kt` |
| Floating controls | `android-app/app/src/main/java/com/ultra/autodetector/service/FloatingWidgetService.kt` |
| User/admin UI | `android-app/app/src/main/java/com/ultra/autodetector/ui/` |

## Security decisions

The administrator password hash is supplied only at build time through
`ULTRA_ADMIN_PASSWORD_HASH`. The password itself is never stored in source
control. If the value is absent, administrator access is disabled instead of
silently falling back to a demo credential.

MediaProjection grants are held in memory for the current app session. The
reboot receiver may restore the floating controls when overlay permission is
present, but Android requires fresh capture consent after reboot.

## Build and configuration

1. Open `android-app/` in Android Studio with Android SDK platform 35.
2. Configure the two admin build variables if administrator access is needed.
3. Run `./gradlew assembleDebug`.

The current Replit container does not include the Android SDK, so APK
compilation and device permission testing must run on an Android development
machine.

## OpenCV boundary

No arbitrary native OpenCV artifact is bundled. `OpenCvManager` exposes the
image-reader and bitmap conversion boundary, while `TemplateMatcher` provides
a bounded normalized-correlation implementation that can be built and tested
without native binaries.