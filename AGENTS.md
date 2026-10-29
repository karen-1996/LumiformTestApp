# Project guidance

Read the relevant skills in [aidev](aidev/README.md) before changing that area.
They are project-scoped guidance; the user's current instructions take priority.

- Keep `presentation`, `domain`, `data`, and `di` inside each feature module.
- Keep all Android `res/` files in `:resources`, including strings, icons, XML themes, and configuration XML. Consumers import `com.lumiformtest.resources.R`; Compose theme code stays in `:design`.
- Use explicit mappers at data/domain and domain/UI boundaries. Keep DTOs and entities out of domain and UI contracts, and domain models out of screen state.
- Preserve the custom MVI with one action handler for state changes and side effects.
- Keep comments short and natural. Explain why a choice was made, without repeating the code.
- Keep changes small enough to review. Do not commit or push until the user reviews and explicitly approves the changes.
- Run checks appropriate to the change and report their actual results and limits.

Skill routing:

- Android code, resources, Gradle, and manifests: [android](aidev/skills/android/SKILL.md).
- Kotlin and coroutines: [kotlin](aidev/skills/kotlin/SKILL.md).
- Compose UI: [compose](aidev/skills/compose/SKILL.md).
- Navigation and restoration: [navigation-3](aidev/skills/navigation-3/SKILL.md).
- Startup handoff: [splash-startup](aidev/skills/splash-startup/SKILL.md).
- ViewModels and contracts: [project-mvi](aidev/skills/project-mvi/SKILL.md).
- Layer models and transformations: [layer-mapping](aidev/skills/layer-mapping/SKILL.md).
- API, Room, images, and offline behavior: [offline-data](aidev/skills/offline-data/SKILL.md).
- Review preparation and verification: [review-workflow](aidev/skills/review-workflow/SKILL.md).
- Challenge document review: [pdf](aidev/skills/pdf/SKILL.md).
- Editing these skills: [skill-creator](aidev/skills/skill-creator/SKILL.md).
