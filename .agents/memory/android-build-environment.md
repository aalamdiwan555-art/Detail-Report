---
name: Android build environment
description: Constraints for building the native Android artifact in this workspace.
---

The Android project requires an Android SDK installation with platform 35 and build tools; Java and Gradle alone are not enough for `assembleDebug`.

**Why:** Gradle reaches dependency resolution but fails when the SDK location is missing, and this workspace does not provide an Android SDK module.

**How to apply:** Keep the native Gradle project unchanged. Build the APK on a machine or workspace with Android SDK platform 35 installed, then use `./gradlew assembleDebug` from `android-app/`.