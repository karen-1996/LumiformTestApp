---
name: kotlin
description: Change Kotlin logic, coroutines, injection, or Kotlin tests in this project.
---

# Kotlin

- Follow the existing contracts and naming. Keep abstractions tied to an actual boundary.
- Prefer lazy initialization wherever safe, following ASA. Use `by lazy` for reusable object properties that can wait until first use, with the default thread-safe mode unless single-thread access is guaranteed. Use Dagger `Lazy<T>` when injected object creation should wait for a conditional use; wrapping an already-created dependency does not defer its construction.
- Keep required lifecycle/configuration work explicit. Do not add lazy delegates to constructor data, cheap per-call values, mutable state, or test setup that needs a defined order. Compose-owned values use keyed `remember`, not an unremembered lazy delegate. Cleanup must not initialize unused resources.
- ViewModels call domain use cases; repository implementations and DTO mapping belong in the feature's data layer.
- Keep visible text in the shared `:resources` module and Android Context outside ViewModels.
- Use lifecycle-owned scopes. Propagate coroutine cancellation instead of turning it into a normal failure.
- After suspension, update current state rather than an earlier snapshot. StateFlow update lambdas must be free of side effects.
- Ordered action handling does not serialize asynchronous completions. Protect refreshes and database writes explicitly where ordering matters.
- Dispatch blocking work to the right context behind the data boundary; suspend alone does not move work off the main thread.

Test observable behavior, failure paths, and concurrency rules with deterministic
coroutine tests. Avoid tests that only repeat private implementation details.
