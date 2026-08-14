---
name: Android build environment
description: Constraints for building the native Android artifact in this workspace.
---

The Android project requires an Android SDK installation with platform 35 and build tools; Java and Gradle alone are not enough for `assembleDebug`.

**Why:** Gradle reaches dependency resolution but fails when the SDK location is missing, and this workspace does not provide an Android SDK module.

**How to apply:** Keep the native Gradle project unchanged. Build the APK on a machine or workspace with Android SDK platform 35 installed, then use `./gradlew assembleDebug` from `android-app/`.

The workspace's default GraalVM 19 can fail Android Gradle Plugin's JDK image
transform against the Android 35 platform. An installed OpenJDK 17 or 21 and
an explicit `JAVA_HOME` are reliable for the wrapper.

**Why:** The wrapper can configure and compile Kotlin with the default runtime,
but `compileDebugJavaWithJavac` may fail in the Android JDK image transform
before Java compilation.

**How to apply:** Set `JAVA_HOME` to OpenJDK 17 or 21, set
`ANDROID_HOME`/`ANDROID_SDK_ROOT` to an SDK containing platform 35, and run the
wrapper from `android-app/`.