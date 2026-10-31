https://github.com/user-attachments/assets/162c2b8e-dcaa-460c-bf28-4024768a74a3

[Download the demo](docs/videos/lumiform-demo.mp4) · [Install the debug APK (1.0.1)](https://github.com/karen-1996/LumiformTestApp/releases/download/v1.0.1-debug/LumiformTestApp-1.0.1-debug.apk) · Android 8.0+

# Lumiform Android Challenge

A native Kotlin app that fetches the [challenge JSON](https://gist.githubusercontent.com/aruana-lumiform/383e1291df3e0cc1d49aeb14d45f5b94/raw/lumiform-android-test.json),
displays its hierarchy, and keeps fetched content available offline.

## Features

- Pages, nested sections, and text questions rendered in source order, with font
  sizes, colors, and indentation showing the hierarchy.
- Single- and multiple-choice questions with locally saved answers. Refresh keeps
  selections that remain valid in the updated definitions.
- Image thumbnails that open a titled viewer, with shared-element transitions.
  Downloaded originals remain available offline.
- Loading, empty, error/retry, refresh progress, and last-successful-update states.
  Failed requests keep saved content visible and choices usable.
- System light/dark themes, Material 3 dynamic colors on Android 12+, accessible
  selection controls, and named Compose previews using local sample data.

## Screenshots

Captured on Samsung A23 / Android 14. Dynamic colors vary by device.

| Form hierarchy · light | Choices · dark |
| --- | --- |
| <img src="docs/screenshots/form-light.png" width="240" alt="Form hierarchy with an image thumbnail in light mode"> | <img src="docs/screenshots/form-choices-dark.png" width="240" alt="Single and multiple choice chips in dark mode"> |

| Image viewer · dark | Saved form · offline |
| --- | --- |
| <img src="docs/screenshots/image-viewer-dark.png" width="240" alt="Full image with its title in dark mode"> | <img src="docs/screenshots/offline-form-dark.png" width="240" alt="Cached form still visible with the no-internet message"> |

## Setup and build

Use Android Studio with Android Gradle Plugin 9.5 support, JDK 25, and Android
SDK 37. The minimum supported version is Android 8.0 (API 26).

```shell
git clone https://github.com/karen-1996/LumiformTestApp.git
cd LumiformTestApp
./gradlew :app:assembleDebug :app:lintDebug
```

Set the SDK location in Android Studio or an untracked `local.properties` file
with `sdk.dir`, then run the `app` configuration on a device or emulator.
On Windows, use `gradlew.bat`.

Versions live in `version.properties`; shared SDK levels and per-build-type base
URLs live in `gradle.properties`. Both base URLs use the challenge host by default
and require a trailing `/`. For example:

```shell
./gradlew :app:assembleDebug -Pnetworking.baseUrl.debug=https://example.com/
```

## Architecture

Jetpack Compose, custom MVI, Hilt, Navigation 3, Retrofit/OkHttp, Room, and Coil.

| Module | Responsibility |
| --- | --- |
| `app` | Application, splash handoff, and root navigation |
| `feature/form` (`:form`) | Form and image screens, feature presentation/domain/data/DI |
| `base` | MVI contracts and coroutine ViewModel base |
| `design` | Compose theme and shared visual components |
| `networking` | Shared HTTP clients, base URL, logging, timeouts, and response limits |
| `resources` | Shared Android strings, icons, themes, and configuration XML |

I keep each feature's layers together and use explicit mappings from DTOs and
Room entities to domain models, then UI models. Presentation is grouped by screen.
ViewModels call use cases; composables receive UI state and send actions.

Room is the source of truth. The app validates a complete response before replacing
saved definitions and reconciling answers in one transaction. Parent IDs and sibling
positions preserve hierarchy and order. Failed fetches or writes retain the last
good snapshot; explicit migrations preserve existing data and answers.

The MVI action handler owns state changes and launches side effects. Refreshes are
coalesced, and accepted answer writes finish in order in an app-owned scope after
screen exit. Definition and answer observations are separate; answer updates reuse
unchanged UI rows. Navigation carries only image IDs and restores entry state.

Coil handles decoding and memory caching. A custom fetcher retains validated original
image files atomically, so offline viewing is independent of an evictable thumbnail
cache. See [architecture notes](docs/architecture.md) for the implementation details.

## Tests and verification

Run JVM tests:

```shell
./gradlew :base:testDebugUnitTest :networking:testDebugUnitTest :form:testDebugUnitTest
```

For device tests, inspect connected devices first, unlock the selected device, and
target its serial:

```shell
adb devices -l
ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest :form:connectedDebugAndroidTest
```

Verified on **2026-10-31**:

| Check | Result |
| --- | --- |
| JVM tests | **167 passed** |
| Device tests · Samsung A23 / Android 14 | **70 passed** (65 form, 5 app) |
| Debug assembly | Passed |
| Debug lint | 0 errors; 1 Gradle-version warning |
| Recorded JVM logic coverage | **98.2% lines**, **88.1% branches** |

Tests cover parsing, mappings, MVI/concurrency, Room persistence and migrations,
answer reconciliation, offline image reuse, navigation, themes, accessibility
semantics, and UI restoration. HTTP tests use MockWebServer and checked-in fixtures.

Coverage is opt-in and enforces an **80% line minimum** for handwritten JVM logic:

```shell
./gradlew debugUnitTestCoverage -Pcoverage=true
```

The aggregate report is at `build/reports/coverage/jvm/html/index.html`. The metric
excludes Compose rendering, Android/Room adapters, DI, and generated code; device
tests cover Android integration separately. Coverage was not regenerated during
the latest review. See [testing and coverage](docs/testing.md) for scope, reports,
and the distinction between automated restoration and manual process-death checks.

## Limitations

- Images must finish loading once to be available offline. Originals are retained
  until app data is cleared; changed bytes at the same URL are not refreshed.
- Section font sizes decrease through four levels, then keep a readable minimum;
  indentation is also limited for small screens.
- Accepted answer writes survive screen exit, but only committed Room answers are
  durable across process death. There is no answer-upload endpoint or background sync.
- The latest automated device verification covers API 34. Release and minimum-API
  checks were not repeated in the latest review.

## Development guidance

[AGENTS.md](AGENTS.md) and [aidev](aidev/README.md) record the project conventions
and AI-assisted workflow. They complement code review and tests.
