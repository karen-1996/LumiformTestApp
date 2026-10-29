---
name: project-mvi
description: Change feature ViewModels, MVI contracts, or the shared action-handling base while preserving the project's custom MVI design.
---

# Custom MVI

- Preserve `sendAction -> onAction(action, prevState)`. The same handler can update state and launch use-case side effects.
- Keep Action, ViewState, NavDestination, and Event contracts. Do not replace them with a separate reducer/effect framework without an explicit architecture change.
- Pass initial state to the base constructor. Do not call an overridable initializer from the base constructor.
- Edit state atomically with the current value. `prevState` is a handling-time snapshot, not a safe value to write after suspension.
- Keep state editors pure; StateFlow can retry them.
- The unlimited action queue is for discrete UI actions. High-frequency streams need a deliberate policy rather than silently dropping user changes.
- Navigation and events have one consumer each and are temporary. Durable content, selections, and recoverable errors belong in state or storage.
- Cancel work and channels when the ViewModel is cleared. Keep Android Context and navigation controllers outside it.
- Keep handlers focused. Extract helpers for real complexity rather than adding a class per action.
- Map use-case results to presentation models before putting them in screen state. Follow the [layer-mapping skill](../layer-mapping/SKILL.md); composables should not receive domain models, DTOs, or entities.

When changing the base, verify ordering, overlapping state updates, cancellation,
and navigation/event delivery behavior. Keep the tests in `:base`; feature tests
should cover their own action outcomes.
