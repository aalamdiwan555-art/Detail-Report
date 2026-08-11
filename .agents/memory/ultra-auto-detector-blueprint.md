---
name: Ultra AutoDetector blueprint
description: Durable constraints for implementing the uploaded Android screen-detection blueprint safely.
---

The uploaded Ultra AutoDetector blueprint is a product specification, not an executable Android project. Before implementation, create a dedicated Android Gradle project and resolve the blueprint's security, authorization, Android lifecycle, OpenCV, and accessibility-policy issues.

**Why:** The source prompt included credential-like administrator material and relies on client-side administrator checks; copying it unchanged would create a serious security risk. The current workspace is a TypeScript/pnpm workspace without an Android module.

**How to apply:** Treat credentials from the prompt as exposed and never reproduce or hardcode them. Use trusted server-side authorization, normalize roles/statuses/license semantics, and implement the Android system in phases with explicit consent, bounded detection, and tested service lifecycles.

The imported Android module should keep native computer-vision dependencies behind a replaceable adapter until a specific OpenCV Android distribution and ABI matrix are selected. Replit's current container can validate Gradle configuration but cannot compile an APK without the Android SDK.

**Why:** The blueprint lists multiple possible OpenCV approaches and the available environment has no Android SDK; adding an arbitrary native artifact would make the project less reproducible and harder to verify.

**How to apply:** Use the dependency-free bitmap matcher/capture boundary for local development, then replace only that adapter after the release build environment and OpenCV version are explicitly chosen.