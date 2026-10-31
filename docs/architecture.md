# Architecture and implementation notes

[Back to the README](../README.md)

## Startup

`SplashActivity` is the launcher and uses AndroidX SplashScreen. It opens
`MainActivity` and finishes, so Back does not return to the splash. I keep the
system splash during this handoff instead of drawing a second splash layout.
There is no fixed delay or network request in the launcher.

This follows Android's [routing Activity pattern](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate).
The custom-splash lint check is disabled only on this Activity because it never
draws its own splash content.

## Light and dark themes

The app follows the device's light/dark setting. Compose uses Material 3 dynamic
colors on Android 12+, with light/dark fallback palettes on older versions.
The native window and splash also have matching night resources and system-bar
icon settings; both Activities use AndroidX edge-to-edge handling.
Both screens and their top bars use a neutral gray surface in light and dark mode;
the native window and splash share the same background.
Dynamic palettes are remembered with configuration keys, so night mode and
resource-color changes refresh them. The light image canvas keeps transparent
black artwork readable in either mode.

Page titles use coral and section headings use green, inspired by the challenge's
example. Shared day/night resources keep these hierarchy colors readable in both
themes, including when the device supplies dynamic Material colors. Question text
keeps the theme's neutral foreground; size and indentation also show the hierarchy.

## Structure

- `app`: application, splash launcher, MainActivity, root navigation, and app identity strings.
- `base`: MVI contracts and `BaseCoroutineViewModel`.
- `design`: shared Compose theme and reusable UI components, including choice chips.
- `networking`: shared OkHttp client, logging interceptor, and Retrofit instance.
- `resources`: shared Android resource files, including UI strings, icons, XML themes,
  and backup configuration.
- `feature/form` (`:form`): form and image destinations, UI, ViewModels, and the
  feature's data and domain layers.

I keep the layers inside each feature, so changes to the form stay together.
Feature modules live next to each other under `feature/`.
There are no separate data or domain Gradle modules.

Presentation code is grouped by screen:

```text
presentation/
  screens/
    form/       screen, route, components, mapper, model, preview, viewmodel
    image/      screen, route, mapper, model, viewmodel
  components/   shared image rendering and loader
  navigation/   destinations and shared transitions
```

Shared Android `res/` files live in `resources/src/main/res`. Modules that use them
depend directly on `:resources` and import `com.lumiformtest.resources.R`.
I keep Compose theme code and reusable UI components in `:design`, as in ASA.
Shared Compose sizes and spacing live in `design/theme/Dimensions.kt`, including
the thumbnail size used by the form, previews, and image sizing tests.
Shared components accept display values and callbacks; feature models and answer
handling stay in `:form`.

App identity strings, such as `app_name`, live separately in
`app/src/main/res/values/strings_non_translatable.xml` and use `translatable="false"`.

## Layer mapping

I use separate models and explicit mappers at each layer boundary:

```text
DTOs / Room entities -> domain models -> UI models
       data/mapper              screens/<screen>/mapper
```

Data mappers handle storage and API formats. Presentation mappers prepare domain
data for the screen, including hierarchy depth and display state. This keeps
DTOs, entities, and domain models out of screen state and composables.

Stateless mappers are extension functions in named mapper files, such as
`toDomain`, `toStorage`, and `toUiModel`. This follows the style used by Google's
[Now in Android sample](https://github.com/android/nowinandroid/blob/main/core/database/src/main/kotlin/com/google/samples/apps/nowinandroid/core/database/model/TopicEntity.kt).
They need no injected mapper instances. Stateful parsing keeps its own class.

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
Debug logs form bodies and image headers; release clients have no logging interceptor.
The feature declares `FormApi` and creates it from the injected Retrofit instance.
Form decoding, validation, and failure handling stay in `:form`.

Form responses use Retrofit streaming. Shared networking caps non-image responses
at 5 MiB before logging or Retrofit can buffer them, including chunked bodies,
decoded gzip, and HTTP error bodies. An oversized form is rejected as invalid
content and leaves the last saved snapshot unchanged. Images keep their separate
20 MiB download budget.

Retrofit fetches the challenge endpoint with OkHttp. The response body is closed
after the existing parser decodes and validates it. Fetching, body reading, and
mapping run outside the main thread.

I use Room as the source of truth. The repository observes only saved snapshots;
fetching alone does not publish content. Five tables store nodes with parent IDs
and sibling positions, response sets, ordered response options, selected answers,
and the last successful refresh time. This is structured storage rather than a cached JSON blob.

Definitions and answers use separate transactional Room queries. Answer writes
do not reload or remap the definition graph. The local source combines only
matching stored definition versions, so independently scheduled queries cannot
mix old answers with new definitions. Replacing a definition increments its
version in the write transaction, even when the refresh timestamp stays the same.
A write failure rolls
back both content and metadata. A failed fetch or invalid response never starts
replacement. An empty fetched form has metadata; no saved form is represented
by null. Cached read errors propagate rather than pretending the cache is empty.

A mutex covers the complete fetch-and-save operation, so overlapping refreshes
cannot commit out of order. Cancellation propagates, and expected fetch, content,
and storage failures are exposed as domain results. ObserveFormUseCase and
RefreshFormUseCase provide the boundary for the MVI integration. Expected saved
content read failures become a domain observation result; retry restarts a failed
observer. The ViewModel does not import Room or data-layer exceptions.

Schemas 1, 2, and 3 are exported under `feature/form/schemas` and kept in Git. The explicit
1 -> 2 migration adds selections and their indexes. The 2 -> 3 migration adds
definition versioning while preserving saved content and answers. There is no
destructive-migration fallback. Image URLs stay in Room;
downloaded originals live in app-owned files.

## Choice persistence

Single-choice questions replace their previous answer and keep an already-selected
option selected. Multiple-choice options toggle independently. Pure domain policies
decide the next selection and reconcile answers after definition changes. The DAO
reads the current saved definition and answers, maps them to policy inputs, and
applies the result in one transaction. Answers belong
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
while a saved-content read failure prevents reliable observation. Selected state
comes from Room, with no optimistic update. Write failures stay in screen state and show a
message while the screen is resumed; retry is another explicit tap, not an automatic
replay of a toggle.

Options use custom rounded chips from `:design` in a single horizontally scrolling
row. Its viewport reaches both screen edges, while content padding keeps the first
and last options aligned with the form. Labels stay on one line, and each response
set retains its scroll position. Selected chips
have a different fill and border, while radio/checkbox semantics keep the behavior clear
for accessibility. Optional response scores appear as badges; no score is calculated.
The footer lists saved selected labels, or "None selected", beside the choice type.
Lists and score values follow the current locale.
Multiline option labels display on one line without dropping words. Blank labels
use numbered names in the chips and selection summary; saved definitions stay
unchanged. Questions with an empty response set show "No options available".

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
theme palettes, and preview data use lazy properties. Conditional injected
dependencies use Dagger Lazy; release builds do not create the unused logger.
Compose values keep their lifecycle-aware remember behavior, and cleanup does not
initialize unused resources.

Feature data and control state belong in `UiState`, without private initialization
flags, observer jobs, counters, or queues in the ViewModels. Required work starts
from `init`. The image ID comes through Hilt's [assisted factory](https://developer.android.com/training/dependency-injection/hilt-jetpack#assisted-injection)
and is kept in initial state, including when the image is missing or a read fails.

The form ViewModel observes Room and starts one refresh per ViewModel instance.
Repeated refresh taps are coalesced while work is active. Choice attempts and their
pending status stay in state. The action handler submits every accepted tap before
returning. The singleton repository serializes these writes with a FIFO mutex in
an injected, supervised app scope, following Android's
[coroutine lifetime guidance](https://developer.android.com/kotlin/coroutines/coroutines-best-practices#creating-coroutines-business-data-layer).
Closing the screen cancels UI waiters and observers; submitted writes still finish
in order. This does not guarantee unfinished writes survive process death: only
answers already committed to Room are durable across that boundary.
Failed observers end before a retry; existing loading state coalesces repeated taps.
Mapping reuses unchanged rows from the current UI content without a separate cache.
It checks existing rows and response definitions before allocating replacements;
an answer-only change also retains the response-set and option-list instances.
The stored definition version selects this fast path before any tree traversal.
The mapper compares saved answers and updates only changed choice rows through
the row index kept in state. [Persistent collections](https://github.com/Kotlin/kotlinx.collections.immutable)
share the remaining row storage, so there is no full-list copy. Unchanged answers
do not look up their UI rows. Definition refreshes still perform a full projection.
Image presence is projected with the definition and kept in UI content. The route
uses that flag and the row index instead of scanning rows on recomposition or
when preparing image navigation.
Cache reads and refresh
have separate state: a fast fetch failure cannot hide a pending cache read, and
refresh success waits for Room rather than publishing response content directly.
Saved content and its timestamp remain visible on refresh failure. After a transport
failure, the data source checks the network status supplied by `:networking`.
When Android reports no validated internet connection, the screen shows "No internet
connection". Other request failures keep the existing message. This check does not
block successful requests or turn cancellation into an offline error. Network-state
capabilities follow the [Android connectivity guidance](https://developer.android.com/develop/connectivity/network-ops/reading-network-state).
The list uses stable IDs and saved scroll
state. Section headings decrease through 22, 20, 18, and 16 sp, then keep the
16 sp minimum above 14 sp question text. The policy lives in
`design/theme/HierarchyTypography.kt`; indentation also has a readable limit.
Image captions use the same small monospace style as choice-selection summaries.
Pure presentation mapping reuses flattened rows when only selections change, replacing
only affected choice rows.

The list uses validated unique node IDs as keys and separate content types for
row reuse. The same callbacks are shared across rows, and item content captures
only the values it needs. Choice options have keys qualified by response-set and
option IDs, keeping focus with the option after a definition reorder. Each
option is a separate skippable composable. Scroll position is read by the lazy
layout rather than by the screen composition. These choices follow Android's
[Compose performance guidance](https://developer.android.com/develop/ui/compose/performance/bestpractices).

The UI applies navigation commands to a saved Navigation 3 back stack. Serializable
keys hold only navigation arguments, and each entry owns its ViewModel and saved
Compose state. Tapping a thumbnail sends its ID through the form's navigation
output. The image entry resolves current title and URL from saved content through
ObserveImageUseCase and its own MVI ViewModel, then maps metadata to a UI model.
The repository observes only the requested image row, with local mapping on IO.
Answer writes do not rerun this query, and the viewer never searches the full form
tree on Main.
A removed image has a missing state; a failed saved-content read has retry.

## Images

I use [Coil 3.6.3](https://coil-kt.github.io/coil/changelog/) for image decoding,
size-aware rendering, and a bounded memory cache. The Application supplies one
Hilt-backed ImageLoader through Coil's lazy singleton factory. Its first creation
runs on IO, with a fixed-size placeholder while preparing, so dependency setup
does not block the UI thread. Preparation is shared by the form entry, so rows
returning to the viewport get the prepared loader immediately. Thumbnails fit the
whole image inside a compact 120 dp square. AsyncImage resolves the decode target
from measured layout bounds and device density, without enlarging small originals.
Coil can reuse a suitable nearby decode size. Separate thumbnail/viewer memory
keys keep a large viewer bitmap out of the scrolling list. The viewer fits the
whole image and keeps its title visible.
A shared light canvas keeps transparent black artwork readable in dark mode.
Loading and retry states belong to the image component; they do not block form
content or reset answers.

The image expands from its thumbnail into the viewer with a 350 ms shared-element
transition, and shrinks back on Back. The shared key uses the node ID, so separate
questions using the same URL do not animate into each other. Navigation still
carries only the image ID. The same image layout stays in place while its bounds
and corner shape animate together. A loaded bitmap stays visible during motion;
the larger original is decoded afterward, with the thumbnail covering the handoff.
Back uses the cached viewer bitmap when available.
The shared overlay is clipped to the screen's content viewport, so a partly hidden
thumbnail stays behind the app bar during opening and closing.

A temporary UI preview carries the clicked image metadata and prepared loader
into the viewer, so reading Room does not insert a loading screen into the motion.
Saved observations remain authoritative, including missing images and read errors.
The preview is not saved in navigation state; restored entries resolve from Room.
Standalone screens and previews also work without a transition scope.

Coil's HTTP fetcher is replaced with a small retained-file fetcher. The feature's
ImageApi uses the shared Retrofit/OkHttp instance from :networking. Image requests send Accept: image/* so debug logging records only headers and
keeps the response body streamed. The fetcher
saves the original bytes before Coil displays them, so a thumbnail does not become
the only available offline resolution. Originals also serve as the disk layer;
Coil's separate evictable disk cache is disabled to avoid storing another copy.
The extension follows Coil's [fetcher API](https://github.com/coil-kt/coil/blob/3.6.3/coil-core/src/commonMain/kotlin/coil3/fetch/Fetcher.kt).
Image bytes stay inside this rendering/storage pipeline; domain and UI mapping
still handle image metadata, without exposing platform files or bitmaps.

Each full URL, including its query, has a SHA-256 filename. Requests for the same
URL share a mutex. Downloads stream on IO, are limited to 20 MiB, and must pass a
small raster decode before an atomic rename publishes the original. Failure or
cancellation removes the temporary file. Existing valid files need no network;
corrupt files can be downloaded again. Per-process file stamps (length and modified
time) avoid repeating the validation decode while a retained file is unchanged.
Directory creation tolerates parallel first downloads for different URLs, so a
request does not fail when another image has just created the shared directory.
No database migration is needed here.

For this small form, downloaded originals are retained until app data is cleared,
including images removed from later definitions. URLs identify immutable images;
changed bytes at the same URL are not automatically refreshed. There is no image
prefetch or background download: an image must have finished loading once before
it is available offline. Zoom and export are outside the challenge scope.

## Compose previews

Each screen and visual component has named, themed previews beside its code.
They cover content, loading/failure, selected/disabled choices, dark mode, and
long text where relevant. Screen previews use sample UI models instead of Hilt
routes. Shared fixtures live in
[FormPreviewData.kt](../feature/form/src/main/java/com/lumiformtest/form/presentation/screens/form/preview/FormPreviewData.kt).
Image previews use a local drawable and never create the image loader or make
network/database calls. Preview wrappers and non-visual state helpers are covered
through their screen/component previews.
