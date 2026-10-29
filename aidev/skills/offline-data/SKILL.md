---
name: offline-data
description: Implement or review the form feature's API mapping, relational storage, selection persistence, and offline image behavior.
---

# Offline form data

This skill records the agreed direction. Add implementation when its phase is in scope.

- Keep remote DTOs, Room entities, mapping, and repository implementations inside `:form/data`; keep models and repository contracts inside `:form/domain`.
- Use explicit data/domain mappers and keep UI mapping in presentation, following the [layer-mapping skill](../layer-mapping/SKILL.md). Repository contracts expose domain models rather than DTOs or entities.
- Preserve parent relationships and explicit sibling positions. IDs do not define display order; pages can contain questions directly.
- Validate a complete fetched snapshot before replacing saved content. Apply related writes in a transaction and retain the last good snapshot on failure.
- The UI observes local storage. Refresh updates it while cached content remains visible.
- Separate response definitions from selected answers. Preserve valid selections on refresh and remove only selections invalidated by the new definitions.
- Enforce single-choice replacement and multi-choice toggles transactionally, using the saved question definition.
- Serialize or coalesce overlapping refreshes so older requests cannot overwrite newer data.
- Saving image URLs is not saving images. Define retention for downloaded originals and test thumbnails and the viewer offline after restart.
- A failed request is not proof of no connectivity. Describe the failed refresh accurately and provide retry without hiding saved content.
- There is no answer-upload endpoint in this challenge. Do not add an outbox, background sync, or scoring behavior without a requirement.

Test round-trip hierarchy/order, failed refresh, atomic writes, selection
reconciliation, and an offline cold start. Include empty, nested, malformed,
and nullable-score fixtures rather than relying only on the happy-path sample.
