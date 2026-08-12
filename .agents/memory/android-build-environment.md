---
name: Android build environment
description: Constraints for building the native Android artifact in this workspace.
---

The Android project requires an Android SDK installation with platform 35 and build tools; Java and Gradle alone are not enough for `assembleDebug`.

**Why:** Gradle reaches dependency resolution but fails when the SDK location is missing, and this workspace does not provide an Android SDK module.

**How to apply:** Keep the native Gradle project unchanged. Build the APK on a machine or workspace with Android SDK platform 35 installed, then use `./gradlew assembleDebug` from `android-app/`.

The system Gradle launcher in this workspace injects its own Java home, while
the generated project wrapper expects `JAVA_HOME` or `java` on `PATH`; the
wrapper is portable on a normal Android/Java machine but may need an explicit
Java home in this container.

**Why:** `gradle` can configure the project here, but `./gradlew` cannot find
Java before it reaches the Android SDK check.

**How to apply:** Treat `gradle :app:tasks` success as configuration-only.
Run the wrapper on a machine with Java and Android SDK platform 35 installed.