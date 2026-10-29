---
name: kotlin
description: Change Kotlin logic, coroutines, injection, or Kotlin tests in this project.
---

# Kotlin

- Follow the existing contracts and naming. Keep abstractions tied to an actual boundary.
- ViewModels call domain use cases; repository implementations and DTO mapping belong in the feature's data layer.
- Keep visible text in the shared `:resources` module and Android Context outside ViewModels.
- Use lifecycle-owned scopes. Propagate coroutine cancellation instead of turning it into a normal failure.
- After suspension, update current state rather than an earlier snapshot. StateFlow update lambdas must be free of side effects.
- Ordered action handling does not serialize asynchronous completions. Protect refreshes and database writes explicitly where ordering matters.
- Dispatch blocking work to the right context behind the data boundary; suspend alone does not move work off the main thread.

Test observable behavior, failure paths, and concurrency rules with deterministic
coroutine tests. Avoid tests that only repeat private implementation details.
