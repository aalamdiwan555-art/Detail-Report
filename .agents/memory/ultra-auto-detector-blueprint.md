---
name: Ultra AutoDetector blueprint
description: Durable constraints for implementing the uploaded Android screen-detection blueprint safely.
---

The uploaded Ultra AutoDetector blueprint is a product specification, not an executable Android project. Before implementation, create a dedicated Android Gradle project and resolve the blueprint's security, authorization, Android lifecycle, OpenCV, and accessibility-policy issues.

**Why:** The source prompt included credential-like administrator material and relies on client-side administrator checks; copying it unchanged would create a serious security risk. The current workspace is a TypeScript/pnpm workspace without an Android module.

**How to apply:** Treat credentials from the prompt as exposed and never reproduce or hardcode them. Use trusted server-side authorization, normalize roles/statuses/license semantics, and implement the Android system in phases with explicit consent, bounded detection, and tested service lifecycles.