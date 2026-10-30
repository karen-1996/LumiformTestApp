# Local form scenarios

These JSON files are reviewable test input. They use the challenge's existing
`page`, `section`, `text`, `image`, and `choice` contract. They are kept locally
in Git-ready files and are never bundled into the production app.

`index.json` lists each file, its expected parsing result, and the risk it covers.
The same files drive JVM checks and the instrumentation test APK's assets.
`generate.py` regenerates all files and the index deterministically, without a
network connection or Python packages:

```shell
python3 form/src/test/resources/fixtures/scenarios/generate.py
```

## Cases

- `valid/`: mixed hierarchy, empty documents/containers, multilingual and RTL
  content, long and multiline copy, shared response sets, signed Long/Int boundaries,
  40 nested sections, 64 options per question, 250 text rows, image variants, and
  extra metadata.
- `refresh/`: apply `01-before`, select question 2 options 1 and 3 and question 3
  option 2, then apply `02-reordered` and `03-replaced-set`. The first refresh keeps
  question 2 option 3 and question 3 option 2. The second removes both old answers.
- `invalid/`: unsupported types at the root and inside sections, duplicate IDs,
  conflicting definitions, misplaced pages, missing/null fields, numeric overflow,
  wrong shapes, excessive nesting, and interrupted JSON. `truncated.json` is
  deliberately not valid JSON.
- `probe/`: unusual payloads allowed by the schema. Blank option labels use a
  numbered display fallback, and questions without options explain their state.

JSON does not configure the app's visual theme. Extra theme metadata is ignored;
page/section styles and system light/dark mode come from the app.

Image addresses use `fixtures.example.invalid` so fixture checks cannot contact a
real image host. Screen layout checks render a local drawable. Image-pipeline
checks use a fake `ImageApi` with generated PNGs and controlled failures.

## Run

```shell
./gradlew :form:testDebugUnitTest --tests '*FormFixtureContractTest'
```

Before device checks, run `adb devices -l`, unlock the chosen device, and select
its serial explicitly:

```shell
ANDROID_SERIAL=<serial> ./gradlew :form:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.lumiformtest.form.fixtures.FormFixturePersistenceTest,com.lumiformtest.form.fixtures.FormFixtureScreenTest,com.lumiformtest.form.fixtures.FormFixtureImageTest
```

Device checks use the separate test app and `fixture-scenarios-test.db`. They do
not replace the installed app's form, answers, endpoint, or image files.

The harness uses the existing JUnit4, Compose tests, and real Room database setup.
Stateless screens use the debug test Activity without Hilt routes. Controlled
image API fakes keep decoding/retry checks independent of HTTP availability.

## Findings

- **Excessive nesting:** the 512-section fixture returned a typed failure on the
  JVM but caused `StackOverflowError` on the Samsung API 34 device before the fix.
  The parser now checks JSON container depth without recursion before decoding.
  The limit is 128 object/array levels, including metadata. The 40-section fixture
  remains supported. Brackets and escaped quotes inside text do not count as nesting.
- **Multiline chip labels:** the device check confirmed that `maxLines = 1`
  truncated everything after the first explicit line break. The shared chip now
  replaces line breaks/tabs with spaces for display. The regression check verifies
  the complete label on one scrolling line at twice the normal font scale.
- **Blank labels / empty choice sets:** blank options now display localized names
  such as "Option 2", including in the selected-answer summary. The original
  labels and IDs are retained in storage. Empty sets show "No options available".
  Chips also have an explicit minimum width and height of 48 dp.

## Verification

- 119 form JVM tests passed, including all 32 fixture contracts and nesting-boundary
  and escaped-string checks.
- Debug app and instrumentation APK builds passed. Debug lint reported zero
  errors and one existing Gradle-version warning.
- 28 device checks passed on Samsung A23 / API 34: nine fixture checks and 19
  existing form/image-transition regressions. Fixture coverage includes accepted
  payloads surviving database reopen, rejected refreshes preserving answers and
  timestamps, refresh reconciliation, light/dark and RTL layouts, large-font chip
  labels, fallback labels, empty sets, image failures/retry, and original-file reuse.
- Unsupported types at any tested position reject the whole incoming snapshot;
  supported siblings are never partially saved.

Layout checks are functional checks. They do not establish frame-rate or scrolling
performance on every device. No production endpoint is used by these tests.
