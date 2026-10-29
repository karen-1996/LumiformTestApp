---
name: review-workflow
description: Prepare small reviewable changes and verify this Android challenge without committing before user approval.
---

# Review workflow

- Keep each change focused on the agreed phase. Preserve unrelated working-tree files.
- Do not commit or push until the user reviews and explicitly approves the pending changes.
- Use short comments explaining decisions in natural B2 English. Avoid invented results, inflated claims, and commentary that only restates the code.
- Choose checks for the behavior changed. Run module unit tests for logic, assemble after wiring changes, and device tests for actual UI/lifecycle behavior.
- Check release assembly when changing generated code, Hilt, serialization, or shrinker-sensitive integration.
- Keep tests meaningful. Do not add placeholder assertions or snapshots of incidental implementation details.
- Report exactly what ran, what passed, remaining findings, and any untested behavior. Saved Compose state restoration is not a full process-death test.
- Keep IDE files, SDK paths, credentials, build output, and emulator state outside the proposed commit.
- Use a read-only test emulator where practical, and stop an emulator started for verification when finished.
- Before handoff, inspect the diff, check whitespace, and confirm Git still has the expected commit when approval is pending.

See the root README for current build and test commands. Keep those commands
updated when the test setup changes rather than maintaining a second command list here.
