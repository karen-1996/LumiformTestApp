---
name: offline-data
description: Implement or review the form feature's API mapping, relational storage, selection persistence, and offline image behavior.
---

# Offline form data

Follow the implemented API/storage boundaries. Add selection and image behavior only when its phase is in scope.

- Keep remote DTOs, Room entities, mapping, and repository implementations inside `:form/data`; keep models and repository contracts inside `:form/domain`.
- Keep HTTP clients, interceptors, and Retrofit configuration in `:networking`. The feature declares `FormApi` and creates it from the injected Retrofit instance; parsing and form-specific failure handling stay in the feature.
- Use explicit data/domain mappers and keep UI mapping in presentation, following the [layer-mapping skill](../layer-mapping/SKILL.md). Repository contracts expose domain models rather than DTOs or entities.
- Preserve parent relationships and explicit sibling positions. IDs do not define display order; pages can contain questions directly.
- Validate a complete fetched snapshot before replacing saved content. Apply related writes in a transaction and retain the last good snapshot on failure.
- The UI observes local storage. Refresh updates it while cached content remains visible.
- Observe definitions and answers with separate transactional queries. Only definition changes load/map the graph; answer changes reuse its prepared definition and choice index. Combine only matching stored definition versions so a refresh cannot mix old answers with new definitions.
- Increment the definition version inside the replacement transaction, independently of timestamps. Answer writes leave it unchanged. Preserve existing content/answers through migrations 1 -> 2 -> 3 and retain all schema snapshots.
- Translate expected saved-content read failures to the domain observation result. Keep Room/data exception types out of presentation; let cancellation and unrelated failures propagate. Retry recreates a failed local observation.
- Distinguish no saved snapshot from a successfully fetched empty document through refresh metadata. Keep snapshot reads and writes transactional and retain the exported Room schemas.
- Separate response definitions from selected answers. Preserve valid selections on refresh and remove only selections invalidated by the new definitions.
- Capture current answers inside the refresh write transaction, after fetching. Qualify answers by question, response-set, and option IDs; when multiple becomes single, keep the first surviving selected option in the refreshed display order.
- Submit accepted answer writes immediately to the singleton repository. It owns FIFO serialization and an injected supervised app scope, so closing a screen stops waiting without losing later taps. This does not guarantee unfinished writes survive process death.
- Selection writes use their own short Room transactions, not the mutex spanning a network refresh. Keep refresh timestamps unchanged by answer writes, and migrate schema 1 -> 2 without clearing existing content.
- Enforce single-choice replacement and multi-choice toggles transactionally, using the saved question definition.
- Keep choice toggling and refresh reconciliation in pure domain policies. Room maps current definitions/answers to policy inputs and applies the result inside the same transaction; ViewModels do not decide saved-answer rules.
- Serialize or coalesce overlapping refreshes so older requests cannot overwrite newer data.
- Saving image URLs is not saving images. Coil's retained-file fetcher uses the feature ImageApi with shared Retrofit, validates bounded original downloads, and publishes files atomically. Keep original IO outside presentation and never save thumbnail-sized decoded output as the original.
- Stream form responses and bound non-image response bytes in shared networking before debug logging or Retrofit can buffer them. Reject oversized content without replacing the saved snapshot; apply the bound to chunked, decoded gzip, and HTTP error bodies too.
- ImageApi marks requests with Accept: image/*; keep shared debug logging at headers for those requests so body logging cannot buffer downloads.
- Treat the full URL as immutable image identity. Originals in filesDir/form-images are retained until app data is cleared, even after definitions remove a URL. Coil has one lazy Hilt-backed loader and a bounded memory cache; originals replace its separate disk cache. Test both thumbnails and the larger viewer with a new loader and no network.
- Different image URLs may download concurrently. Shared directory creation must accept another request creating it first; keep the deterministic cold-cache race and first-attempt UI checks.
- Navigate with an image ID and resolve metadata from saved form data through the viewer's use case/MVI/mapping path. Handle removed images and failed saved-content reads explicitly.
- Observe viewer metadata with an ID-scoped query of `form_node`, not a full-form/answers snapshot. Map it on the local source's IO dispatcher, and verify that answer writes do not rerun the image query.
- A failed request is not proof of no connectivity. Describe the failed refresh accurately and provide retry without hiding saved content.
- There is no answer-upload endpoint in this challenge. Do not add an outbox, background sync, or scoring behavior without a requirement.

Test round-trip hierarchy/order, failed refresh, atomic writes, selection
reconciliation, and an offline cold start. Include empty, nested, malformed,
and nullable-score fixtures rather than relying only on the happy-path sample.
