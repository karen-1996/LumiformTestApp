# Lumiform Android Challenge

My implementation of the Lumiform Android coding challenge.

## Status

The foundation includes feature modules, my custom MVI base, Hilt, and Navigation 3.
The form feature now has domain models, JSON decoding, snapshot validation, and
data-to-domain mapping. The screen is still empty. Fetching content, offline
storage, choice selection, and image loading will follow in small, focused changes.

## Startup

`SplashActivity` is the launcher and uses AndroidX SplashScreen. It opens
`MainActivity` and finishes, so Back does not return to the splash. I keep the
system splash during this handoff instead of drawing a second splash layout.
There is no fixed delay or network request in the launcher.

This follows Android's [routing Activity pattern](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate).
The custom-splash lint check is disabled only on this Activity because it never
draws its own splash content.

## Structure

- `app`: application, splash launcher, MainActivity, and root navigation.
- `base`: MVI contracts and `BaseCoroutineViewModel`.
- `design`: shared Compose theme.
- `resources`: all Android resource files, including strings, icons, XML themes,
  and backup configuration.
- `form`: form and image destinations, UI, ViewModels, and the feature's data and
  domain layers.

I keep the layers inside each feature, so changes to the form stay together.
There are no separate data or domain Gradle modules.

All Android `res/` files live in `resources/src/main/res`. Modules that use them
depend directly on `:resources` and import `com.lumiformtest.resources.R`.
I keep Compose theme code in `:design`, as in ASA.

## Layer mapping

I use separate models and explicit mappers at each layer boundary:

```text
DTOs / Room entities -> domain models -> UI models
       data/mapper              presentation/mapper
```

Data mappers handle storage and API formats. Presentation mappers prepare domain
data for the screen, including hierarchy depth and display state. This keeps
DTOs, entities, and domain models out of screen state and composables.

Mappers stay inside the feature and have no side effects. The data-to-domain
mapper is implemented. Entity and presentation mappers will be added with Room
and the hierarchy UI. Reverse mappings are added for actual write operations.

## Content contract

The parser handles pages, sections, text, images, and choices using the endpoint's
`type` field. Domain models use Kotlin types without serialization annotations or
Android dependencies. The mapper preserves source order, text, IDs, image titles
and URLs, choice settings, and optional nullable integer scores.

I validate the complete snapshot before it can replace saved content. Content IDs
are unique across the document. Response-set IDs have a separate namespace, and
option IDs are unique within each set. Questions can share a response set when
its ordered definition matches; conflicting definitions are rejected.

Extra metadata fields are accepted. Unknown content types, missing required
fields, invalid hierarchy, and duplicate IDs produce `InvalidFormContentException`
in the data layer. A later repository will handle this failure while keeping the
last valid content. Empty documents, pages, and sections are supported.

## MVI

The UI sends an `Action` to the ViewModel. I handle state changes and side effects
in the same `onAction` function. For this small feature, it keeps the flow easy
to follow without separate reducer and effect-handler classes.

Actions are queued in order. Work launched by an action can finish later, so it
must edit the latest state rather than write an old state snapshot. The initial
state is passed to the base constructor to avoid calling an overridden method
before the feature ViewModel is initialized.

`StateFlow` holds the screen state. Navigation and temporary UI events use separate
channels with one consumer each. They can wait for a collector while the ViewModel
is alive, but are not durable storage or an exactly-once delivery mechanism.
The action queue is unlimited, following my ASA implementation, and is intended
for discrete UI actions rather than continuous input streams.

The UI applies navigation commands to a saved Navigation 3 back stack. Serializable
keys hold only navigation arguments, and each entry owns its ViewModel and saved
Compose state. The image destination is wired, but its content will be added with
the data layer; there is no sample image button in the form screen.

## Setup

- Use Android Studio with support for Android Gradle Plugin 9.5.
- Use JDK 25 for Gradle, as configured in `gradle/gradle-daemon-jvm.properties`.
- Install Android SDK 37. The minimum supported Android version is 8.0 (API 26).
- Open the project and let Gradle sync. Set the Android SDK location in Android
  Studio or in an untracked `local.properties` file using `sdk.dir`.

## Build

```shell
./gradlew :app:assembleDebug :app:lintDebug
```

On Windows, use `gradlew.bat`. Run the `app` configuration on an emulator or device
with API 26 or newer.

## Tests

```shell
./gradlew :base:testDebugUnitTest :form:testDebugUnitTest
```

The JVM tests cover MVI behavior, destination-key serialization, JSON parsing,
data mapping, and snapshot validation. The test fixture in
`form/src/test/resources/fixtures/lumiform-android-test.json` is a snapshot of the
[challenge endpoint](https://gist.githubusercontent.com/aruana-lumiform/383e1291df3e0cc1d49aeb14d45f5b94/raw/lumiform-android-test.json).
It is JVM test input only and is not packaged in the app's Android resources.

With an emulator or device connected:

```shell
./gradlew :app:connectedDebugAndroidTest
```

The navigation device test checks that the image destination survives saved
Compose state restoration and that Back returns to the form. It uses an empty
Hilt Activity available only in debug builds. Full process-death and offline
tests will follow when the data layer is implemented.

The startup device test launches the app through its launcher intent and checks
that MainActivity becomes the task root after the splash handoff.

## AI development guidance

[aidev](aidev/README.md) contains the project skills and how they are used.
`AGENTS.md` routes relevant work to them. The skills record my architecture and
review choices; they do not replace code review or tests.
