# Testing and coverage

[Back to the README](../README.md)

## Tests

```shell
./gradlew :base:testDebugUnitTest :networking:testDebugUnitTest :form:testDebugUnitTest
```

The JVM tests cover MVI behavior, destination keys, parsing, entity/domain mapping,
HTTP responses, cache-preserving failures, cancellation, overlapping refreshes,
domain/UI hierarchy and selection mapping, refresh reconciliation, and MVI
cache/refresh timing, ordered writes, cancellation, and error recovery. Image tests
cover original-file reuse, serialized downloads, failures, size limits, cancellation,
corruption recovery, URL identity, and viewer metadata changes/removal.
Networking tests also check the shared transport and logging policy. The optional
:networking:testReleaseUnitTest task checks the release policy separately.
HTTP tests use MockWebServer and do not depend on the public endpoint. The fixture in
`feature/form/src/test/resources/fixtures/lumiform-android-test.json` is a snapshot of the
[challenge endpoint](https://gist.githubusercontent.com/aruana-lumiform/383e1291df3e0cc1d49aeb14d45f5b94/raw/lumiform-android-test.json).
The sample and the [local edge-case scenarios](../feature/form/src/test/resources/fixtures/scenarios/README.md)
are test input only. JVM tests read them as resources, and device tests read the
same files from test APK assets. They are not packaged in the production app.

The documented setup was also checked in a fresh local directory without IDE state
or project build output. Debug app/test builds, JVM tests, and lint passed
using the installed Android SDK and an existing Gradle dependency cache.

Check connected devices first, unlock the selected device, and target its serial:

```shell
adb devices -l
ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest :form:connectedDebugAndroidTest
```

The navigation device test checks that the image destination survives saved
Compose state restoration and that Back returns to the form. It uses an empty
Hilt Activity available only in debug builds. This is not a full process-death
navigation test.

The startup device test launches the app through its launcher intent and checks
that MainActivity becomes the task root after the splash handoff. Theme tests
resolve native window/splash colors and bar icons in both configurations, then
switch Compose between system modes with fallback and dynamic palettes. They
check readable text contrast and matching fallback window/background colors.

Stateless form screen tests cover row kinds/headings, retry actions, cached content
during refresh/failure, empty versus loading, saved list position, and long nested
text at a large font scale in both themes on a small layout width. A typography
check reads the rendered text styles at normal and 2x font scale, verifying that
section sizes decrease over four levels and clamp above question text. Choice tests
check radio/checkbox semantics, one action per chip tap, saved-selection summaries,
horizontally scrolling chips, score badges, touch targets, failure messages, disabled controls, and long
labels with large fonts. Image UI tests cover
thumbnail navigation, missing/read-error states, failure-to-success retry, actual
thumbnail bitmap bounds/cache separation, and non-blocking first loader creation.
Clock-controlled checks verify expansion/shrink bounds, intermediate corner shapes,
and return to the thumbnail's position. A real Coil test delays saved metadata and
the original image, checking that the loaded thumbnail stays visible through the
animation and decoder handoff, while saved read errors still take priority.
Another pixel check scrolls a thumbnail partly under the app bar and verifies
that image pixels stay out of the bar throughout both navigation directions.
List tests check anchor retention when rows above it are inserted/removed and
focus/selection identity when response options reorder.
A real Coil pipeline test loads a thumbnail, closes its loader, and uses a new
loader with no network to decode the larger retained original. Invalid image
bytes are rejected before publishing a file.

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
refresh time. Both image originals were also downloaded in the real app. With no
active network, a force-stop/relaunch loaded thumbnails and opened the titled
viewer. Back restored the exact scrolled form position. The open viewer also
returned from Recents after the background app process was killed, while still
offline. These are manual emulator checks, separate from saved-state UI tests.

On Samsung A23 (Android 14), an offline cold start also kept the saved form and
titled image viewer available. The [README screenshots](../README.md#screenshots) show the final typography,
light/dark modes, choices, and cached-content fallback on that device.

## Coverage

Coverage uses [AGP's built-in JaCoCo support](https://developer.android.com/studio/test/coverage-report),
with the version pinned in the version catalog. It is opt-in with `-Pcoverage=true`
and applies only to debug. Normal debug APKs have no coverage instrumentation.

Recorded local results on **2026-10-31**. The independent review reran all JVM and
device tests, debug assembly, and lint; it did not regenerate coverage:

| Measurement | Result |
| --- | --- |
| JVM logic line coverage | **98.2%** — 785/799 lines |
| JVM logic branch coverage | **88.1%** — 446/506 branches |
| JVM tests | **167 passed** |
| Device tests, last full run on Samsung A23 / API 34 | **70 passed** (65 form, 5 app) |
| Earlier device-only line coverage, `:app` | **74.9%** |
| Earlier device-only line coverage, `:form` | **72.8%** |

The JVM percentages describe the non-UI logic scope below. UI rendering and
Android integration have separate device tests. These are recorded results,
not a live CI badge; regenerate the reports after code changes.

The state-ownership, extension-mapper, domain-policy, and incremental-projection
refactors passed the JVM
suites and 80% coverage gate, debug app/device-test assembly, and debug lint.
Both complete device suites passed on Samsung A23, including a query check that
answer writes do not reload viewer metadata or the form definition graph.
Traversal guards also verify that answer projection never reads the document or
iterates existing rows; unchanged answers do not even look up their row.
The route's device regression verifies that tapping a choice in a 2,000-row form
does not traverse rows on Main. JVM checks cover accepted writes finishing in
order after ViewModel clearing, and bounded known-length, chunked, gzip, and HTTP
error responses with and without debug logging.
The cold-cache fix also passed 13 focused Samsung image-screen, pipeline,
transition, and fixture checks, including two concurrent first downloads with no
error or retry shown. The latest full run on 2026-10-31 passed all 70 device tests on Samsung A23 / API 34.
Schema 2 -> 3 and same-timestamp definition refreshes have device checks. The device coverage percentages
above are from an earlier instrumented baseline; the latest device run used
normal debug builds without coverage instrumentation.

Run the JVM suites, generate reports, and enforce the 80% line-coverage minimum:

```shell
./gradlew debugUnitTestCoverage -Pcoverage=true
```

The focused aggregate report is at `build/reports/coverage/jvm/html/index.html`,
with XML at `build/reports/coverage/jvm/report.xml`. It merges JVM execution data
from `:base`, `:networking`, and `:form`, using AGP's public class artifacts.
`debugJvmCoverageCheck` enforces at least 80% **line** coverage across this scope;
branch coverage is reported separately. To generate the focused report without
the gate, run `debugJvmCoverageReport -Pcoverage=true`.
Missing execution data or an empty coverage scope also fails the gate.

The scope keeps handwritten JVM logic: MVI/ViewModels and contracts, domain
models/use cases, DTO parsing/validation, repositories, mappers, response
reconciliation, image-file caching/fetching, and the HTTP logging interceptor.
Uncovered code in those areas stays in the denominator. ViewModels, contracts,
models, and mappers remain included under `screens/form` and `screens/image`;
only rendering code is excluded there.

Excluded from this focused JVM metric:

- Compose screens, routes, components, previews, and image/navigation transitions.
  Serializable destination keys remain included.
- Android adapters that use Room/SQLite or BitmapFactory: DAOs, database setup,
  migrations, the Room local data source, and bitmap validation. These have device
  tests; the JVM metric makes no claim about their coverage.
- DI wiring and generated Hilt/Dagger, Room implementations, resources, and BuildConfig.

`:app` currently contains Android startup/navigation wiring, and `:design`
contains Compose components/theme code, so neither belongs to this JVM logic
scope. The exclusions are explicit in `build.gradle.kts`. Add new JVM logic/modules
to the scope when the architecture grows.

The original full-module reports remain available:

| Module | HTML report |
| --- | --- |
| base | `base/build/reports/coverage/test/debug/index.html` |
| networking | `networking/build/reports/coverage/test/debug/index.html` |
| form | `feature/form/build/reports/coverage/test/debug/index.html` |

Device coverage runs the existing `:app` and `:form` instrumentation suites.
Check connected devices first, unlock the selected device, and use its serial:

```shell
adb devices -l
ANDROID_SERIAL=<serial> ./gradlew debugAndroidTestCoverage -Pcoverage=true
```

Reports are written under each tested module's
`build/reports/coverage/androidTest/debug/connected/index.html`.
For a single module, use `:form:createDebugUnitTestCoverageReport` or
`:form:createDebugAndroidTestCoverageReport`, with the same coverage property.

Unit and device reports measure different executions. Do not add or average their
percentages. Full-module reports include UI and generated code, so their JVM
number alone does not describe Room or Compose coverage. Use the focused aggregate
for the 80% JVM gate and keep the full/device reports for their separate scopes.
Coverage does not prove visual correctness or scrolling performance.

Reports and execution data stay under ignored `build/` folders.
