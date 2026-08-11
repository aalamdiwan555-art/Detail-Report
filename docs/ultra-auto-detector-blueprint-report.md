# Ultra AutoDetector Blueprint Review and Implementation Report

**Report status:** Complete for the current intake/review checkpoint  
**Review date:** 2026-08-11  
**Source material:** Eight uploaded `ultra_master_prompt_part*.txt` files  
**Implementation status:** Documentation and readiness review only; no Android application code was created in this checkpoint

> This report intentionally does not reproduce passwords, tokens, private keys, or other credential material from the source prompt. Any credential-like value found in the prompt must be treated as compromised and rotated before implementation.

---

## 1. Purpose of this report

This document consolidates the eight uploaded blueprint parts into one implementation-oriented reference. It records:

- What the requested product is supposed to do.
- The planned Android architecture and file structure.
- The authentication, licensing, Firebase, Telegram, OpenCV, and accessibility flows.
- The UI screens and user journeys.
- The security and platform risks found in the supplied blueprint.
- Gaps and inconsistencies that must be resolved before production implementation.
- What was and was not done during this checkpoint.
- A repeatable format for reporting future checkpoints.

This is a review document, not a claim that the supplied Kotlin snippets are already production-ready.

---

## 2. Source files reviewed

All eight uploaded parts were read from the local project files:

1. `attached_assets/ultra_master_prompt_part1_1786443020391.txt`
2. `attached_assets/ultra_master_prompt_part2_1786443020456.txt`
3. `attached_assets/ultra_master_prompt_part3_1786443020492.txt`
4. `attached_assets/ultra_master_prompt_part4_1786443020517.txt`
5. `attached_assets/ultra_master_prompt_part5_1786443020545.txt`
6. `attached_assets/ultra_master_prompt_part6_1786443020572.txt`
7. `attached_assets/ultra_master_prompt_part7_1786443020596.txt`
8. `attached_assets/ultra_master_prompt_part8_1786443020621.txt`

The parts form one continuous blueprint:

| Part | Main subject |
|---|---|
| 1 | Product summary, configuration, project structure, Firestore rules, data models, authentication layout |
| 2 | Main dashboard, admin panel, user row, floating widget layouts |
| 3 | Renewal dialog, Android manifest, accessibility configuration, Gradle setup, colors, strings, themes |
| 4 | Application class, constants, Telegram helper, humanization engine, extensions, encrypted preferences |
| 5 | Firebase authentication, Firestore, and Storage managers |
| 6 | OpenCV conversion, MediaProjection screen capture, and template matching |
| 7 | Foreground detection service, accessibility auto-click service, floating widget service |
| 8 | Authentication, dashboard, and admin activities; user/template adapters; final flow summary |

---

## 3. Product summary

The requested product is an Android application called **Ultra AutoDetector**. Its intended purpose is to:

1. Authenticate users with Firebase email/password authentication.
2. Create new accounts in a pending state.
3. Let an administrator approve, reject, or grant license durations.
4. Capture the device screen using Android `MediaProjection`.
5. Match downloaded image templates against captured frames using OpenCV.
6. Trigger touch gestures through an Android `AccessibilityService`.
7. Add timing and coordinate variation to automated taps.
8. Keep detection running in a foreground service.
9. Display a draggable floating control widget above other apps.
10. Let users contact an administrator through Telegram for renewal requests.
11. Let the administrator upload and delete detection templates stored in Firebase.

The blueprint targets:

- **Language:** Kotlin
- **Minimum Android API:** 26 / Android 8.0
- **Target Android API:** 33+ in the summary, with the Gradle example targeting API 34
- **Architecture:** MVVM plus service-oriented background components
- **Cloud services:** Firebase Authentication, Firestore, Cloud Storage
- **Computer vision:** OpenCV template matching

---

## 4. Current project readiness

### 4.1 What exists now

The current project contains a TypeScript pnpm workspace with:

- An Express API server artifact.
- A mockup/design sandbox artifact.
- Shared API specification, generated clients, Zod types, and database libraries.
- No Android Gradle project.
- No `app/` Android module.
- No Kotlin source tree.
- No `google-services.json`.
- No Android resources, manifest, Gradle wrapper, or Firebase Android configuration.

### 4.2 What was done in this checkpoint

Completed:

- Read all eight uploaded prompt files.
- Consolidated their requirements into this report.
- Identified the product scope and major flows.
- Identified security-sensitive material without copying it into this report.
- Identified implementation blockers and code-level inconsistencies.
- Created a future checkpoint reporting format.

Not done:

- No Android project was scaffolded.
- No Firebase project was connected.
- No Firebase rules were deployed.
- No credentials or secrets were accessed, copied, or configured.
- No Kotlin code was added.
- No OpenCV dependency was installed.
- No Android build or emulator test was run.
- No Telegram message was sent.
- No external integration was added.
- No existing API or design artifact was changed.

### 4.3 Readiness conclusion

The uploaded files are a detailed product blueprint, but they are not directly executable in the current project. A separate Android application artifact or Android project must be created before implementation can begin. The implementation should not proceed with the blueprint unchanged because several security, Android-platform, and correctness issues need resolution first.

---

## 5. Planned architecture from the blueprint

### 5.1 Data layer

The planned data layer contains:

- `FirebaseAuthManager`
  - Login.
  - Registration.
  - Logout.
  - Password reset.
  - Admin detection.
  - Admin document initialization.
- `FirestoreManager`
  - User creation and updates.
  - User lookup and listing.
  - Last-login updates.
  - License grants.
  - User rejection.
  - Template listing, creation, deletion, and live flows.
  - Immutable admin action logs.
- `StorageManager`
  - Template image uploads.
  - Template image downloads to local cache.
  - Template image deletion.
- `EncryptedPrefsManager`
  - Local permission flags.
  - MediaProjection result data.
  - First-launch state.

### 5.2 Domain models

#### User

The planned user record contains:

- Firebase UID.
- Email address.
- Role: user or admin.
- Status: pending, approved, rejected, or expired.
- License expiration timestamp.
- Creation timestamp.
- Last login timestamp.
- Device information.

The model also includes:

- An active-license check.
- Human-readable remaining-time formatting.
- A special lifetime timestamp.

#### Template

The planned template record contains:

- Template ID.
- Name.
- Description.
- Firebase Storage path.
- Download URL.
- Confidence threshold.
- Active/inactive flag.
- Creation timestamp.
- Creating admin UID.

#### License status

The UI status enum maps status values to:

- Display labels.
- Colors.
- Background drawables.
- Whether the detector may be used.

The blueprint also uses an admin state, although the Firestore status list and enum values are not fully aligned. This needs to be normalized during implementation.

---

## 6. Authentication and licensing flow

### 6.1 Registration

The intended registration flow is:

1. User enters an email and password.
2. Firebase Authentication creates the account.
3. A Firestore user document is created.
4. The role is set to `user`.
5. The status is set to `pending`.
6. No expiration timestamp is assigned.
7. Device information is recorded.
8. The user remains on the status screen until approved.

### 6.2 Login

The intended login flow is:

1. Firebase signs the user in.
2. The application loads the user's Firestore record.
3. Last-login information is updated.
4. Admin users go to the main dashboard.
5. Approved users with an active license go to the main dashboard.
6. Pending, rejected, or expired users remain on the authentication/status screen.
7. Non-active users receive a Telegram renewal/contact action.

### 6.3 License actions

The planned administrator actions are:

- Grant 1 day.
- Grant 2 days.
- Grant 3 days.
- Grant lifetime access.
- Reject a user.

Each grant sets the user status to approved and writes an expiration timestamp. The blueprint also intends to record the administrator action in an audit collection.

### 6.4 Access enforcement

The detector should be unavailable when:

- The user is pending.
- The user is rejected.
- The user is expired.
- Required device permissions are missing.
- The user is not authenticated.

Admin users are intended to receive lifetime access, but the exact representation of admin access should be made consistent instead of relying on a special timestamp and separate role/status combinations.

---

## 7. Telegram renewal flow

The blueprint includes a renewal action that:

1. Shows the user's email and status.
2. Builds a pre-filled renewal message.
3. Includes email, status, UID, device information, and request time.
4. Opens the Telegram app if installed.
5. Falls back to Telegram X if available.
6. Falls back to a web URL if no supported app is available.

### Required implementation review

The Telegram deep-link behavior must be tested on supported Android versions and Telegram clients. The web fallback in the source blueprint uses a `start` parameter, which may not behave like a normal pre-filled user message for every Telegram destination. The final implementation should:

- Use a supported Telegram URL format.
- Avoid exposing unnecessary user identifiers in a URL.
- Clearly tell the user what information will be shared.
- Handle missing Telegram apps and browser failures.
- Never send a renewal request automatically without user confirmation.

---

## 8. Main dashboard requirements

The main dashboard is intended to show:

### Subscription status

- Active, pending, rejected, expired, or admin state.
- Remaining time.
- Lifetime access where applicable.
- A renewal action for non-active users.

### Required permissions

- Accessibility service.
- Draw over other apps.
- Screen recording / MediaProjection.

Each permission needs:

- Current state.
- A grant/settings action when missing.
- A visible confirmation when granted.

### Detector controls

- Start detector.
- Stop detector.
- Ready/running state.
- Error and locked states.
- Floating widget launch while the detector runs.

### Account controls

- Admin panel entry for authorized administrators.
- Logout.

---

## 9. Administrator panel requirements

The administrator panel is intended to be restricted to the administrator account and contains:

### User license management

- Live list of users.
- Email.
- Current status.
- Remaining license time.
- License grant buttons.
- Reject button.
- Confirmation before rejection.

### Template cloud management

- Upload a new image template.
- Store the image in Firebase Storage.
- Store template metadata in Firestore.
- Display the active template list.
- Delete templates after confirmation.
- Refresh detection templates after changes.

### Audit trail

The blueprint intends to write administrator actions to an immutable `adminLogs` collection. The log design should be expanded before production to include:

- Acting administrator UID.
- Action type.
- Target user or template.
- Timestamp from the server.
- Relevant before/after values.
- Device or request context when appropriate.

---

## 10. Detection pipeline

The planned runtime pipeline is:

1. The user grants MediaProjection access.
2. The app starts a foreground detection service.
3. A `VirtualDisplay` renders the screen into an `ImageReader`.
4. Captured frames are converted to OpenCV matrices.
5. Active templates are downloaded and cached locally.
6. OpenCV performs normalized template matching.
7. The highest match above the threshold is selected.
8. Cooldown and humanization logic are applied.
9. A click command is sent to the accessibility service.
10. The accessibility service dispatches a touch gesture.
11. The floating widget remains available for user controls.
12. Templates are periodically refreshed.

### OpenCV behavior specified by the blueprint

- Default confidence threshold: `0.85`.
- Matching method: normalized coefficient matching.
- Template larger than screen: skip safely.
- Match result includes confidence, center point, bounds, and template dimensions.
- Template images are converted to a common channel format.
- OpenCV matrices must be released to prevent memory leaks.

### Performance work required before production

The source snippets perform matching on every received frame and can start multiple processing coroutines. A production implementation should add:

- A single-frame processing gate or bounded frame queue.
- Backpressure when frame processing is slower than capture.
- Explicit dispatcher/thread policy.
- Template cache synchronization.
- A maximum frame rate.
- Memory and battery limits.
- Metrics for frame rate, match time, dropped frames, and click count.
- Safe cancellation when the service stops.

---

## 11. Auto-click and floating widget behavior

### Accessibility auto-click service

The planned accessibility service:

- Receives click coordinates.
- Applies a random delay.
- Applies coordinate jitter.
- Uses a randomized press duration.
- Dispatches an Android gesture.
- Prevents overlapping click operations.
- Reports completion or cancellation.

### Floating widget

The planned overlay widget:

- Appears above other apps.
- Can be dragged.
- Snaps to the nearest screen edge.
- Has play, pause, and close controls.
- Can stop the detector and remove itself.

### Safety and compliance review

Automated interaction with other applications can violate the terms of service of target apps and may create user-safety risks. Before implementation, the product owner should confirm:

- The intended target applications permit this automation.
- The app has a legitimate accessibility use case.
- The Play Store distribution strategy permits the requested accessibility behavior.
- The UI clearly explains what will be captured and controlled.
- The user can stop automation immediately.
- The app does not attempt to evade security or anti-bot controls.

The supplied blueprint describes “humanization” partly as a way to avoid detection. That wording and behavior should be removed or reframed around reliability, accessibility, and user-controlled timing. The app must not be designed to bypass security mechanisms.

---

## 12. Android platform and implementation issues found

The following items must be corrected before treating the blueprint as production-ready.

### 12.1 Credentials are embedded in the source prompt

The uploaded prompt contains administrator credential material. It is not repeated here and was not used.

Required action:

- Treat the credential as exposed.
- Rotate it immediately if it has ever been real.
- Do not place passwords in Kotlin constants, Gradle files, source control, or Firebase client code.
- Use a secure administrative setup process.
- Prefer server-side authorization claims or a protected admin backend rather than an email/password comparison in the app.

### 12.2 Client-side admin checks are insufficient

The blueprint checks whether the current email equals an administrator email in several client locations. Client checks are useful for presentation only; they are not authorization.

Required action:

- Enforce admin privileges in Firebase rules and/or trusted server code.
- Use custom claims or a secure role document managed by a trusted backend.
- Do not let the client create or upgrade its own admin document.
- Do not let a client-controlled email decide authorization.

### 12.3 Firestore rules need stronger validation

The supplied rules protect several writes by email, but they do not fully validate all field transitions or prevent all unauthorized document shapes.

Required action:

- Validate field types and allowed fields.
- Validate role and status transitions.
- Prevent users from changing their own identity, role, license, or audit data.
- Require server timestamps where possible.
- Protect audit records from client-side forgery.
- Test rules with authenticated, unauthenticated, regular-user, and administrator cases.

### 12.4 Password reset is defined but not exposed

The authentication manager includes a password-reset method, but the supplied authentication layout does not include a visible password-reset action.

Required action:

- Add a password-reset user journey.
- Add validation, loading, success, and error states.
- Avoid revealing whether an email address exists.

### 12.5 Registration does not clearly sign out the new user

Firebase account creation generally authenticates the newly created account immediately. The intended UX is a pending status screen, but the blueprint does not fully define whether the user remains signed in.

Required action:

- Choose and document one behavior:
  - Keep the user signed in with restricted access, or
  - Sign out and require login after approval.
- Make Firestore rules and UI behavior match that choice.

### 12.6 Status and lifetime values are inconsistent

The blueprint uses:

- `approved` plus a lifetime timestamp.
- A `lifetime` status in one model description.
- An `ADMIN` enum state.
- An `admin` role.

Required action:

- Define a single canonical role model.
- Define a single canonical account status model.
- Decide whether lifetime is a license type, an expiration value, or both.
- Use server-side time comparisons.

### 12.7 License grants may overwrite an existing license

The planned grant logic calculates expiration from the current time. Renewing an already active license may shorten it instead of extending it.

Required action:

- Decide whether grants replace the expiration or extend from the later of now and the current expiration.
- Record the previous and new expiration values in the audit log.

### 12.8 MediaProjection data should not be persisted casually

The blueprint serializes an `Intent` into encrypted preferences. This can be fragile across Android versions and may not remain valid after process/device changes.

Required action:

- Treat MediaProjection authorization as revocable and session-specific.
- Handle invalid or expired projection tokens.
- Request permission again when starting capture fails.
- Avoid assuming the serialized intent is permanently reusable.

### 12.9 Android manifest details need API-level review

The manifest includes permissions and service declarations that vary by Android release. The exact foreground-service types, notification permission behavior, exported attributes, and accessibility-service requirements must be checked against the final target SDK.

Required action:

- Validate the manifest with the selected compile and target SDK.
- Test Android 8, Android 12, Android 13, and Android 14+ behavior.
- Add correct foreground service startup and notification handling.
- Confirm that every declared resource exists.

### 12.10 OpenCV dependency is unresolved

The Gradle section lists multiple possible OpenCV approaches but does not select one. Some are comments or may be unavailable in the selected repository.

Required action:

- Select one supported OpenCV distribution.
- Pin and verify its version and ABI support.
- Test native library loading on all supported architectures.
- Define a fallback/error state when OpenCV initialization fails.

### 12.11 Template upload is incomplete

The administrator activity contains placeholder comments for image upload completion. It does not fully define:

- Metadata input.
- Template ID generation.
- Upload cancellation.
- File type and size validation.
- Storage cleanup when Firestore creation fails.
- Firestore cleanup when Storage upload fails.
- Retry behavior.

Required action:

- Implement the upload as an explicit transaction-like workflow with cleanup and retry behavior.

### 12.12 Template thresholds are not fully used

The template model includes a per-template confidence threshold, but the detection service uses the global threshold for all templates.

Required action:

- Use each template's configured threshold, or remove the per-template field.
- Document precedence when a global and per-template setting both exist.

### 12.13 Frame conversion may mishandle row padding

The OpenCV image conversion code reads the full buffer but constructs a matrix using only width and height without clearly accounting for row stride/padding.

Required action:

- Implement and test conversion for the actual `ImageReader` format.
- Verify coordinates against the device display.
- Test different densities, orientations, and devices.

### 12.14 Rotation and coordinate scaling are not defined

The blueprint assumes captured screen coordinates map directly to accessibility gesture coordinates.

Required action:

- Define orientation handling.
- Handle display cutouts and system bars.
- Handle density and scaling.
- Test coordinate accuracy on multiple screen sizes.

### 12.15 Background service lifecycle is incomplete

The snippets contain possible lifecycle risks:

- Repeated start calls may create duplicate capture state.
- Destruction may call stop logic more than once.
- Broadcast receiver cleanup must be guarded.
- Wake-lock acquisition should be balanced and bounded.
- Service restarts may lack valid projection data.

Required action:

- Make start/stop idempotent.
- Handle process death.
- Handle projection revocation.
- Add service state persistence that does not falsely claim active capture.

### 12.16 Floating widget pause behavior is only a placeholder

The pause button currently logs an action but does not pause matching or clicks.

Required action:

- Define paused state in the detection service.
- Stop frame processing and click dispatch while paused.
- Keep the foreground notification and widget state synchronized.

### 12.17 Some referenced resources are missing from the listed structure

The snippets reference icons and resources that are not all present in the initial file list, including accessibility, overlay, screen recording, detector, upload, check, back, and other icons.

Required action:

- Create or replace every referenced resource.
- Run resource compilation early.
- Provide content descriptions for all meaningful icons.

### 12.18 XML namespace issue in the renewal dialog

The renewal dialog uses `app:` attributes but the displayed root element does not show an `app` namespace declaration.

Required action:

- Add the namespace or replace the attributes with valid alternatives.
- Run Android resource compilation to catch similar errors.

### 12.19 Deprecated APIs appear in the snippets

The blueprint uses older patterns such as `startActivityForResult`, `onActivityResult`, and `Display.getMetrics`.

Required action:

- Use Activity Result APIs.
- Use current display/window metrics APIs.
- Review all Android API-level deprecations against the selected target SDK.

### 12.20 Accessibility policy and user disclosure are underspecified

The app captures screens and controls gestures in other applications. The blueprint needs a transparent consent and disclosure experience.

Required action:

- Explain exactly what is captured.
- Explain what actions may be performed.
- Provide clear start, pause, and stop controls.
- Avoid hidden or deceptive behavior.
- Add a first-run consent flow and an accessible privacy policy link.

---

## 13. Recommended implementation sequence

The safest implementation order is:

### Phase 0: Product and security decisions

- Confirm the intended target apps and permitted use.
- Rotate any exposed credentials.
- Define the administrator provisioning process.
- Define canonical roles, statuses, and license semantics.
- Decide whether this is an internal distribution app or a public Play Store app.

### Phase 1: Android project foundation

- Create a dedicated Android Gradle project.
- Configure Kotlin, compile SDK, target SDK, min SDK, and build types.
- Add resource structure and dependency versions.
- Add application and manifest scaffolding.
- Add a local-only development mode that does not use production Firebase.

### Phase 2: Authentication and account state

- Add Firebase Auth.
- Add account model and rules.
- Implement registration, login, logout, password reset, and pending state.
- Implement trusted administrator authorization.
- Add emulator tests for state transitions.

### Phase 3: License administration

- Add user list and license actions.
- Implement server-authorized grants and rejection.
- Implement renewal extension semantics.
- Add immutable audit logging.
- Test rules and UI behavior for every role/status.

### Phase 4: Template management

- Add Storage and Firestore template records.
- Implement validated upload, metadata, retry, and cleanup.
- Implement download caching and invalidation.
- Add per-template threshold handling.

### Phase 5: Permissions and dashboard

- Implement accessibility, overlay, notification, and MediaProjection flows.
- Add permission re-checking in `onResume`.
- Add explicit disclosure and user consent.
- Add clear locked and error states.

### Phase 6: Detection engine

- Initialize OpenCV.
- Implement safe image conversion.
- Implement bounded frame processing.
- Implement template matching and coordinate mapping.
- Add performance instrumentation.

### Phase 7: Accessibility and overlay controls

- Implement safe gesture dispatch.
- Implement pause/resume/stop state.
- Implement widget dragging and edge snapping.
- Make all service transitions idempotent.

### Phase 8: Verification

- Run unit tests for license and matching logic.
- Run Firebase emulator rule tests.
- Run Android resource and manifest checks.
- Test supported Android versions and screen sizes.
- Test process death, permission revocation, network loss, and Storage failures.
- Perform a privacy and policy review before distribution.

---

## 14. Verification checklist

### Security

- [ ] No passwords or private credentials in source.
- [ ] Any credential from the uploaded prompt has been rotated.
- [ ] Admin authorization is enforced outside the client UI.
- [ ] Firestore rules are tested with emulator rules tests.
- [ ] Storage rules are tested.
- [ ] Audit logs cannot be altered by regular users.
- [ ] Sensitive data in renewal messages is minimized and user-confirmed.

### Account and licensing

- [ ] Registration creates only a pending regular user.
- [ ] Pending users cannot start detection.
- [ ] Rejected users cannot start detection.
- [ ] Expired users cannot start detection.
- [ ] Active licenses behave correctly near expiration.
- [ ] Renewals extend or replace expiration according to the documented decision.
- [ ] Lifetime access is represented consistently.
- [ ] Password reset is available and tested.

### Permissions and services

- [ ] Accessibility service can be enabled and detected.
- [ ] Overlay permission is rechecked after returning from settings.
- [ ] MediaProjection consent is requested correctly.
- [ ] Notification permission is handled on Android 13+.
- [ ] Foreground service starts and stops correctly.
- [ ] Projection revocation stops capture safely.
- [ ] Pause really pauses.
- [ ] Stop immediately stops matching and clicking.

### Computer vision

- [ ] OpenCV initializes on every supported ABI.
- [ ] Image row padding is handled correctly.
- [ ] Screen rotation is handled.
- [ ] Template and screen color channels are compatible.
- [ ] Templates larger than the screen are rejected safely.
- [ ] Per-template thresholds work if retained.
- [ ] Frame processing has bounded concurrency.
- [ ] OpenCV matrices are released on all paths.

### Administrator functions

- [ ] User list updates in real time or refreshes predictably.
- [ ] License buttons are authorized and audited.
- [ ] Rejection requires confirmation.
- [ ] Template upload validates size and format.
- [ ] Failed uploads clean up partial state.
- [ ] Template deletion cleans up both metadata and image.
- [ ] Admin panel is unavailable to regular users.

### UX and disclosure

- [ ] The user understands what screen data is captured.
- [ ] The user understands what gestures may be performed.
- [ ] Start, pause, and stop controls are obvious.
- [ ] All icon-only controls have content descriptions.
- [ ] Loading, error, empty, and offline states are implemented.
- [ ] Renewal messages are previewed before opening Telegram.

---

## 15. Checkpoint reporting log

The user requested a detailed report after checkpoints. Future work should append an entry using this structure rather than replacing earlier history.

### Checkpoint 0 — Intake and blueprint review

**Date:** 2026-08-11  
**Scope:** Read and consolidate the eight uploaded blueprint parts.  
**Completed:**

- Reviewed all eight local source files.
- Extracted product scope, architecture, data models, UI, services, and final flow.
- Created this consolidated report.
- Redacted credential material from the report.
- Identified that the current project is not yet an Android project.
- Documented security, Android API, OpenCV, lifecycle, and completeness risks.

**Files created:**

- `docs/ultra-auto-detector-blueprint-report.md`

**Files changed:** None besides the new report.  
**Dependencies added:** None.  
**Secrets accessed or changed:** None.  
**External services accessed:** None.  
**Tests/builds run:** None; no Android project exists to build at this checkpoint.  
**Known blockers:** Android project foundation, Firebase project configuration, secure admin provisioning, OpenCV distribution choice, and product/policy decisions.  
**Next safe step:** Confirm the security and product decisions in Section 13 before creating the Android project.

### Future checkpoint template

Copy this section for each future milestone:

```text
### Checkpoint N — <short name>

Date:
Scope:

Completed:
- 

Files created:
- 

Files changed:
- 

Dependencies added or removed:
- 

Secrets accessed or changed:
- None / describe only the category, never the value

External services accessed:
- 

Tests and verification:
- 

Known issues:
- 

Security or privacy impact:
- 

Next safe step:
- 
```

### Checkpoint 1 — Native Android implementation

**Date:** 2026-08-11  
**Scope:** Build the native Kotlin Android application foundation and connect the core product flows.

**Completed:**

- Created a dedicated Android project under `android-app/`.
- Added Kotlin, Android Gradle, Jetpack Compose, Firebase, coroutines, and secure local development configuration.
- Added offline local demo mode so the app does not require production credentials to run.
- Added Firebase repository boundaries for Auth, Firestore, and Storage.
- Added account statuses, license state, template data, permission state, and detector state.
- Added authentication and registration screens.
- Added subscription status, renewal handoff, permissions, detector controls, template list, and settings/privacy disclosure.
- Added administrator controls for license actions and template upload/delete.
- Added foreground MediaProjection capture lifecycle.
- Added accessibility gesture service and draggable floating widget with pause/stop controls.
- Added secure Firestore and Storage rules using trusted admin claims rather than email/password checks.
- Added Android project README and Gradle wrapper generation support.
- Added `.gitignore` entries for native Android outputs.

**Files created or changed:**

- `android-app/settings.gradle.kts`
- `android-app/build.gradle.kts`
- `android-app/gradle.properties`
- `android-app/app/build.gradle.kts`
- `android-app/app/src/main/AndroidManifest.xml`
- `android-app/app/src/main/java/com/ultra/autodetector/**`
- `android-app/app/src/main/res/**`
- `android-app/README.md`
- `firebase/firestore.rules`
- `firebase/storage.rules`
- `.gitignore`

**Dependencies added:** AndroidX Compose, lifecycle, DataStore/security support, Kotlin coroutines/serialization, Firebase Auth/Firestore/Storage, and Material components.

**Secrets accessed or changed:** None. The administrator password from the uploaded prompt was not copied or used.

**External services accessed:** No Firebase project was connected. No Telegram message was sent. No cloud data was read or changed.

**Tests and verification:**

- Java toolchain installed and available.
- Gradle installed and project configuration recognized.
- `gradle tasks --all` completed successfully.
- `gradle assembleDebug` reached dependency resolution but could not proceed because this environment has no Android SDK or `ANDROID_HOME`.
- Source-level corrections were made for activity result constants, nullable account access, and Firestore template serialization.

**Known issues:**

- An Android SDK-enabled environment is required to produce an APK.
- Template matching is isolated behind the dependency-free `OpenCvManager` and `TemplateMatcher` adapters. The foreground service downloads active authorized templates, evaluates bounded bitmap matches, and dispatches only explicit accessibility gestures. A selected native OpenCV distribution can replace the adapter later.
- Firebase production mode requires a project-specific `google-services.json` and trusted Firebase custom admin claims.
- Firebase template listing and admin user listing need live snapshot flows for production parity.

**Security or privacy impact:**

- No embedded production credentials.
- Rules use a trusted `admin` custom claim.
- User-visible disclosure explains screen capture and gesture behavior.
- Detection starts only after explicit permission and start actions.
- The app does not implement anti-bot evasion or hidden target-app discovery.

**Next safe step:** Build the APK in Android Studio or an Android-SDK-enabled environment, then wire a selected OpenCV distribution and Firebase project configuration; run emulator tests for permissions, service lifecycle, rules, and template matching.

---

## 16. Final assessment

The uploaded blueprint is a substantial functional specification for an Android application, not a drop-in implementation for the current project. It covers most major screens and components, but it contains security-sensitive credential handling, incomplete production paths, inconsistent account-state definitions, and several Android lifecycle and platform concerns.

The correct next step is **not** to copy the snippets unchanged. The correct next step is to:

1. Remove and rotate exposed credentials.
2. Confirm the legitimate and permitted use of accessibility automation.
3. Define trusted administrator authorization.
4. Normalize roles, statuses, and license behavior.
5. Create a dedicated Android project foundation.
6. Implement and test the system in the phased order above.

The native Android foundation and core user flows are now implemented in `android-app/`. Cloud configuration, the final OpenCV distribution, APK compilation, and device/emulator verification remain environment-dependent follow-up work.