# Lumiform Android Challenge

My implementation of the Lumiform Android coding challenge.

## Status

The foundation includes feature modules, my custom MVI base, Hilt, and Navigation 3.
The form feature has domain models, JSON validation, API fetching, and relational
offline storage. The screen observes saved content, refreshes on first open, and
renders pages, nested sections, text, choice controls, and image titles. Single
and multiple answers are saved locally and preserved when refreshed definitions
still support them. Loading, empty, error/retry, refresh progress, and last-update
states are implemented. Image loading follows in its own phase.

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
- `networking`: shared OkHttp client, logging interceptor, and Retrofit instance.
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

Mappers stay inside the feature and have no side effects. DTO/domain,
entity/domain, and domain/UI mappers are implemented. The presentation mapper
flattens the tree in source order and preserves parent IDs, depth, and question
metadata. Reverse mappings are added for actual write operations.

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
in the data layer. The repository handles this failure while keeping the last
valid content. Empty documents, pages, and sections are supported.

## API and offline storage

The `:networking` module provides singleton OkHttp and Retrofit instances through
Hilt, following ASA. It owns the base URL, bounded connection/read/write/call
timeouts, and the [HTTP logging interceptor](https://github.com/lysine-dev/okhttp/tree/main/okhttp-logging-interceptor).
Body logging is enabled only in debug; release clients have no logging interceptor.
The feature declares `FormApi` and creates it from the injected Retrofit instance.
Form decoding, validation, and failure handling stay in `:form`.

Retrofit fetches the challenge endpoint with OkHttp. The response body is closed
after the existing parser decodes and validates it. Fetching, body reading, and
mapping run outside the main thread.

I use Room as the source of truth. The repository observes only saved snapshots;
fetching alone does not publish content. Five tables store nodes with parent IDs
and sibling positions, response sets, ordered response options, selected answers,
and the last successful refresh time. This is structured storage rather than a cached JSON blob.

Room reads and replaces each snapshot in a transaction. A write failure rolls
back both content and metadata. A failed fetch or invalid response never starts
replacement. An empty fetched form has metadata; no saved form is represented
by null. Cached read errors propagate rather than pretending the cache is empty.

A mutex covers the complete fetch-and-save operation, so overlapping refreshes
cannot commit out of order. Cancellation propagates, and expected fetch, content,
and storage failures are exposed as domain results. ObserveFormUseCase and
RefreshFormUseCase provide the boundary for the MVI integration. Expected saved
content read failures become a domain observation result; retry restarts a failed
observer. The ViewModel does not import Room or data-layer exceptions.

Schemas 1 and 2 are exported under `form/schemas` and kept in Git. The explicit
1 -> 2 migration adds selections and their indexes without clearing the saved
form. There is no destructive-migration fallback. Image URLs are stored now;
downloaded image files belong to the image phase.

## Choice persistence

Radio questions replace their previous answer and keep an already-selected option
selected. Checkbox questions toggle independently. The DAO reads the current
saved definition and applies each operation in one transaction. Answers belong
to a question and response set; shared definitions do not share selected answers.
Composite foreign keys reject options from a different set. A stale or removed
option returns a domain failure; saved answers stay unchanged.

Refresh reads the latest selections inside its write transaction, then restores
only valid answers after replacing definitions. This includes answers saved while
the request was still fetching. A changed question type or response-set identity
clears invalid answers; removed options are discarded. When multiple choice becomes
single choice, the first surviving selected option in the new display order is
kept. Any failed replacement rolls back content, answers, and refresh time together.
Selection writes never change the successful remote refresh timestamp.

Choices remain usable during network refresh or a failed request. They are disabled
while a saved-content read failure prevents reliable observation. Checked state
comes from Room, with no optimistic update. Write failures stay in screen state and show a
message while the screen is resumed; retry is another explicit tap, not an automatic
replay of a toggle.

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

I prefer lazy initialization where creation can safely wait until first use,
following ASA. The MVI state/channels, parser configuration, refresh mutex, fallback
theme palettes, selection queue/writer, and preview data use lazy properties. Conditional injected
dependencies use Dagger Lazy; release builds do not create the unused logger.
Compose values keep their lifecycle-aware remember behavior, and cleanup does not
initialize unused resources.

The form ViewModel observes Room and starts one refresh per ViewModel instance.
Repeated refresh taps are coalesced while work is active. Choice taps use one
FIFO writer so suspended writes cannot reorder accepted input. Cache reads and refresh
have separate state: a fast fetch failure cannot hide a pending cache read, and
refresh success waits for Room rather than publishing response content directly.
Saved content and its timestamp remain visible on refresh failure. Messages do not
infer connectivity from request failures. The list uses stable IDs and saved scroll
state; indentation and section typography have readable lower bounds. Pure
presentation mapping reuses flattened rows when only selections change, replacing
only affected choice rows.

The UI applies navigation commands to a saved Navigation 3 back stack. Serializable
keys hold only navigation arguments, and each entry owns its ViewModel and saved
Compose state. The image destination is wired, but its content will be added with
the image phase; there is no sample image button in the form screen.

## Setup

- Use Android Studio with support for Android Gradle Plugin 9.5.
- Use JDK 25 for Gradle, as configured in `gradle/gradle-daemon-jvm.properties`.
- Install Android SDK 37. The minimum supported Android version is 8.0 (API 26).
- Open the project and let Gradle sync. Set the Android SDK location in Android
  Studio or in an untracked `local.properties` file using `sdk.dir`.

## Build

App versions are set in root `version.properties` (`versionCode` and `versionName`).
Shared SDK versions are set in `gradle.properties` (`android.minSdk`,
`android.compileSdk`, and `android.targetSdk`). The root build script applies them
to all Android modules, following ASA; targetSdk applies to the app.

```shell
./gradlew :app:assembleDebug :app:lintDebug
```

Release enables code and resource shrinking; debug explicitly disables both.

Base URLs are configured in `gradle.properties` as `networking.baseUrl.debug` and
`networking.baseUrl.release`. Both use the challenge host by default. The networking
module generates `BuildConfig.BASE_URL` for the selected build type, which Retrofit
reads at runtime. URLs must include a trailing `/`. To override a URL for one build:

```shell
./gradlew :app:assembleDebug -Pnetworking.baseUrl.debug=https://example.com/
```

On Windows, use `gradlew.bat`. Run the `app` configuration on an emulator or device
with API 26 or newer.

## Tests

```shell
./gradlew :base:testDebugUnitTest :networking:testDebugUnitTest :networking:testReleaseUnitTest :form:testDebugUnitTest
```

The JVM tests cover MVI behavior, destination keys, parsing, entity/domain mapping,
HTTP responses, cache-preserving failures, cancellation, overlapping refreshes,
domain/UI hierarchy and selection mapping, refresh reconciliation, and MVI
cache/refresh timing, ordered writes, cancellation, and error recovery.
Networking tests also check the shared transport and debug/release logging policy.
HTTP tests use MockWebServer and do not depend on the public endpoint. The fixture in
`form/src/test/resources/fixtures/lumiform-android-test.json` is a snapshot of the
[challenge endpoint](https://gist.githubusercontent.com/aruana-lumiform/383e1291df3e0cc1d49aeb14d45f5b94/raw/lumiform-android-test.json).
It is JVM test input only and is not packaged in the app's Android resources.

With an emulator or device connected:

```shell
./gradlew :app:connectedDebugAndroidTest
./gradlew :form:connectedDebugAndroidTest
```

The navigation device test checks that the image destination survives saved
Compose state restoration and that Back returns to the form. It uses an empty
Hilt Activity available only in debug builds. This is not a full process-death
navigation test.

The startup device test launches the app through its launcher intent and checks
that MainActivity becomes the task root after the splash handoff.

Stateless form screen tests cover row kinds/headings, retry actions, cached content
during refresh/failure, empty versus loading, saved list position, and long nested
text at a large font scale in both themes on a small layout width. Choice tests
check radio/checkbox semantics, one action per whole-row tap, failure messages,
control availability, and long labels with large fonts.

The form device tests use a real Room database to check reopen persistence,
offline refresh failure, foreign-key rollback, empty-cache semantics, and complete
observed snapshots. Selection tests cover foreign keys, shared definitions,
concurrent taps, mode/identity changes, rollback, answers saved during fetch, and
coherent observed definitions/answers. A migration test starts from the exported
v1 schema, preserves its content/time, and saves/reopens answers in v2. These
tests create and remove separate test databases.

The real app was also checked manually after a successful fetch: with the test
emulator's network disabled and no active network, a force-stop/relaunch displayed
saved content and selected answers, retained the successful update time, and
offered retry after refresh failed. Another checkbox answer was saved offline.
After reconnecting, a successful refresh retained all answers and updated the
refresh time. Downloaded images are still part of the image phase.

## AI development guidance

[aidev](aidev/README.md) contains the project skills and how they are used.
`AGENTS.md` routes relevant work to them. The skills record my architecture and
review choices; they do not replace code review or tests.
