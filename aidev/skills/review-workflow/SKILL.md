---
name: review-workflow
description: Prepare small reviewable changes and verify this Android challenge without committing before user approval.
---

# Review workflow

- Keep each change focused on the agreed phase. Preserve unrelated working-tree files.
- Do not commit or push until the user reviews and explicitly approves the pending changes.
- Use short comments explaining decisions in natural B2 English. Avoid invented results, inflated claims, and commentary that only restates the code.
- Choose checks for the behavior changed. Run module unit tests for logic, assemble after wiring changes, and device tests for actual UI/lifecycle behavior.
- Verify debug builds and relevant tests. Run release checks only when Karen explicitly requests them.
- Keep tests meaningful. Do not add placeholder assertions or snapshots of incidental implementation details.
- Report exactly what ran, what passed, remaining findings, and any untested behavior. Saved Compose state restoration is not a full process-death test.
- Keep IDE files, SDK paths, credentials, build output, and emulator state outside the proposed commit.
- Before runtime checks, run `adb devices -l`. Prefer a suitable connected physical device, then an already running emulator. Launch an emulator only when the connected devices cannot cover the check.
- Target the chosen serial explicitly with `adb -s` and the equivalent Gradle device filter; do not run checks on every connected device by default.
- When an emulator is needed, use a read-only instance where practical and stop any emulator started for verification when finished.
- Before handoff, inspect the diff, check whitespace, and confirm Git still has the expected commit when approval is pending.

See the root README for current build and test commands. Keep those commands
updated when the test setup changes rather than maintaining a second command list here.
