# Ultra AutoDetector implementation

This file is the implementation companion to the eight uploaded
`ultra_master_prompt` parts. The source lives in `android-app/` and keeps the
native Kotlin + Jetpack Compose architecture from the imported project.

## Product scope

- User registration, sign-in, password reset, and pending/approved/rejected/
  expired license states.
- Admin license actions and template upload/delete through Firebase.
- Telegram renewal handoff with the account status and UID.
- Explicit Android permissions for screen capture, overlay, notifications, and
  accessibility gestures.
- Foreground MediaProjection capture, bounded template matching, and a
  draggable pause/stop widget.

## Source map

| Blueprint area | Implementation |
| --- | --- |
| Firebase Auth manager | `android-app/app/src/main/java/com/ultra/autodetector/data/firebase/FirebaseAuthManager.kt` |
| Firestore manager | `android-app/app/src/main/java/com/ultra/autodetector/data/firebase/FirestoreManager.kt` |
| Storage manager | `android-app/app/src/main/java/com/ultra/autodetector/data/firebase/StorageManager.kt` |
| Encrypted local preferences | `android-app/app/src/main/java/com/ultra/autodetector/data/local/EncryptedPrefsManager.kt` |
| Capture/image adapter | `android-app/app/src/main/java/com/ultra/autodetector/opencv/OpenCvManager.kt` |
| Screen capture | `android-app/app/src/main/java/com/ultra/autodetector/opencv/ScreenCaptureManager.kt` |
| Template matching | `android-app/app/src/main/java/com/ultra/autodetector/opencv/TemplateMatcher.kt` |
| Foreground detection | `android-app/app/src/main/java/com/ultra/autodetector/service/DetectionService.kt` |
| Accessibility gestures | `android-app/app/src/main/java/com/ultra/autodetector/service/AutoClickService.kt` |
| Floating controls | `android-app/app/src/main/java/com/ultra/autodetector/service/FloatingWidgetService.kt` |
| User/admin UI | `android-app/app/src/main/java/com/ultra/autodetector/ui/MainActivity.kt` |

The imported project uses one Compose activity instead of the blueprint's
legacy XML activity split. This avoids duplicate navigation and keeps all
permission results in one lifecycle owner.

## Security decisions

The uploaded prompt contains administrator credentials and proposes a
client-side email/password administrator check. Those values are treated as
exposed and are not copied into source. Production admin access is based on
the Firebase Authentication `admin == true` custom claim, with Firestore and
Storage rules enforcing the same claim server-side.

The app has a local-only demo repository when `google-services.json` is absent.
The demo identities are explicitly marked as demo behavior in the Android
README and are not production credentials.

MediaProjection grants are kept in memory for the current app session.
Encrypted preferences store permission metadata only; they do not serialize a
reusable screen-capture token.

## Build and configuration

1. Open `android-app/` in Android Studio with Android SDK platform 35.
2. Add a Firebase-provided `app/google-services.json` only for a configured
   Firebase project.
3. Deploy and test `firebase/firestore.rules` and `firebase/storage.rules`
   with authenticated, unauthenticated, regular-user, and admin cases.
4. Build with `./gradlew assembleDebug`.

The current Replit container does not include the Android SDK, so APK
compilation and device permission testing must run on an Android development
machine.

## OpenCV boundary

No arbitrary native OpenCV artifact is bundled. `OpenCvManager` exposes the
image-reader and bitmap conversion boundary, while `TemplateMatcher` provides
a bounded normalized-correlation implementation that can be built and tested
without native binaries. A selected OpenCV version/ABI can replace that
adapter later without changing the service contract.