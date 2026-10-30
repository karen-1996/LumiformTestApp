# Project guidance

Read the relevant skills in [aidev](aidev/README.md) before changing that area.
They are project-scoped guidance; the user's current instructions take priority.

- Place feature modules under `feature/`; `:form` maps to `feature/form`.
- Keep `presentation`, `domain`, `data`, and `di` inside each feature module.
- Keep OkHttp, interceptors, base URL, and Retrofit provisioning in `:networking`. Features declare request APIs and create them from the injected Retrofit instance.
- Keep shared Android `res/` files in `:resources`, including UI strings, icons, XML themes, and configuration XML. Consumers import `com.lumiformtest.resources.R`; Compose theme code stays in `:design`. App-owned non-translatable identity strings, such as `app_name`, belong in `app/src/main/res/values/strings_non_translatable.xml` with `translatable="false"`.
- Use explicit mappers at data/domain and domain/UI boundaries. Keep DTOs and entities out of domain and UI contracts, and domain models out of screen state.
- Preserve the custom MVI with one action handler for state changes and side effects.
- Prefer lazy initialization wherever it safely defers object creation, following ASA. Use `by lazy` for reusable Kotlin properties, Dagger `Lazy` for conditional injected dependencies, and `remember` for Compose-owned values. Preserve required lifecycle/configuration timing and avoid initializing unused resources during cleanup.
- Keep comments short and natural. Explain why a choice was made, without repeating the code.
- Provide a named Compose preview for every screen and visual component. Use sample UI models and local assets; preview stateless content instead of Hilt routes, and keep previews independent of network and storage.
- Keep changes small enough to review. Do not commit or push until the user reviews and explicitly approves the changes.
- Run checks appropriate to the change and report their actual results and limits.
- Before Android runtime checks, inspect `adb devices -l` and prefer a suitable connected device. Launch an emulator only when connected devices cannot cover the check. Target the selected device explicitly.

Skill routing:

- Android code, resources, Gradle, and manifests: [android](aidev/skills/android/SKILL.md).
- Kotlin and coroutines: [kotlin](aidev/skills/kotlin/SKILL.md).
- Compose UI: [compose](aidev/skills/compose/SKILL.md).
- Navigation and restoration: [navigation-3](aidev/skills/navigation-3/SKILL.md).
- Startup handoff: [splash-startup](aidev/skills/splash-startup/SKILL.md).
- System bars, insets, and mode changes: [edge-to-edge](aidev/skills/edge-to-edge/SKILL.md).
- ViewModels and contracts: [project-mvi](aidev/skills/project-mvi/SKILL.md).
- Layer models and transformations: [layer-mapping](aidev/skills/layer-mapping/SKILL.md).
- API, Room, images, and offline behavior: [offline-data](aidev/skills/offline-data/SKILL.md).
- Review preparation and verification: [review-workflow](aidev/skills/review-workflow/SKILL.md).
- Challenge document review: [pdf](aidev/skills/pdf/SKILL.md).
- Editing these skills: [skill-creator](aidev/skills/skill-creator/SKILL.md).
