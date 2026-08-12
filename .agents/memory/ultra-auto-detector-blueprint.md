---
name: Ultra AutoDetector blueprint
description: Durable constraints for implementing the uploaded Android screen-detection blueprint safely.
---

The uploaded Ultra AutoDetector blueprint is implemented in the dedicated native Android Gradle project under `android-app/`; keep its security, authorization, Android lifecycle, OpenCV, and accessibility-policy boundaries intact.

**Why:** The source prompt included credential-like administrator material and relied on client-side administrator checks; copying it unchanged would create a serious security risk. The project now has a native Android module, but its cloud and device validation remain environment-dependent.

**How to apply:** Treat credentials from the prompt as exposed and never reproduce or hardcode them. Use trusted server-side authorization, normalize roles/statuses/license semantics, and implement the Android system in phases with explicit consent, bounded detection, and tested service lifecycles.

The imported Android module should keep native computer-vision dependencies behind a replaceable adapter until a specific OpenCV Android distribution and ABI matrix are selected. Replit's current container can validate Gradle configuration but cannot compile an APK without the Android SDK.

**Why:** The blueprint lists multiple possible OpenCV approaches and the available environment has no Android SDK; adding an arbitrary native artifact would make the project less reproducible and harder to verify.

**How to apply:** Use the dependency-free bitmap matcher/capture boundary for local development, then replace only that adapter after the release build environment and OpenCV version are explicitly chosen.

Administrator authorization must be provisioned at build time or through a
trusted external mechanism; never copy exposed prompt credentials into Kotlin,
Gradle, documentation, or a demo seed.

**Why:** Client-side administrator constants are recoverable from an APK and
the imported prompt included credential-like material that must be treated as
compromised.

**How to apply:** Keep the local Room authorization flow offline, but require a
release build to provide administrator identity material through a protected
provisioning path and disable admin access when it is absent.