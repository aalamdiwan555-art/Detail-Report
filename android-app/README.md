# ULTRA AUTO DETECTOR

Native Kotlin Android app with package `com.ultra.autodetector`.

## Product flow

- Main screen uses the compact premium ULTRA 4K layout and a live START/STOP
  detection control.
- Six taps on the ULTRA logo within two seconds opens the admin panel through
  `AuthRepository.loginAdmin()`.
- Admins can add screenshots to `files/templates/`, edit names, set the
  0.50–0.95 confidence threshold, enable/disable templates, choose click or
  swipe actions, clear local data, and export logs.
- Detection requires Android overlay, accessibility, notification, and
  MediaProjection consent. Detection runs only after the user explicitly starts
  it.
- The 80dp overlay is draggable from its PUSH surface, remembers its last
  position, has a green PUSH state, and its close button sends `STOP_DETECTION`.

## Native architecture

- `DetectionService` — foreground orchestration service, notification channel
  `ULTRA Active`, configurable 100–2000ms scan interval, logging, and action
  dispatch.
- `ScreenCaptureService` — MediaProjection capture service with a 500ms default
  frame cadence.
- `AutoDetectorService` — accessibility gesture executor for clicks and swipes.
- `ImageDetector` — OpenCV `TM_CCOEFF_NORMED` matcher with the required
  `findImage(template, screen, threshold): Point?` API.
- Room `AppDatabase` — `TemplateEntity`, `ActionEntity`, `LogEntity`, plus the
  imported local account tables.
- `OverlayManager` — singleton `TYPE_APPLICATION_OVERLAY` PUSH/START control.

## Build

The app targets SDK 34, compiles with SDK 35, and supports min SDK 26:

```bash
./gradlew :app:assembleDebug
```

The APK is written to
`app/build/outputs/apk/debug/app-debug.apk`.

OpenCV 4.9.0 is consumed from Maven Central using its published Android
coordinate `org.opencv:opencv:4.9.0`.