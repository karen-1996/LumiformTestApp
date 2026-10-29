---
name: android
description: Change Android code, manifests, resources, Gradle modules, and Android tests in this project.
---

# Android

- Use the existing module structure. Feature layers belong inside their feature module.
- Use AGP's built-in Kotlin support. Keep Kotlin, Compose, serialization, and KSP versions compatible; use the version catalog.
- Add dependencies when their implementation needs them. Check current stable versions in official release notes rather than guessing.
- Keep Android Context out of ViewModels. Put platform work behind context-free domain contracts.
- Use existing theme resources and MaterialTheme for colors. Leave vector asset palettes unchanged unless the task asks to edit them.
- Keep all Android `res/` files in `resources/src/main/res`, including strings, icons, layouts, fonts, XML themes, and configuration XML. Do not create resource folders inside app or feature modules.
- Give resource consumers a direct `:resources` dependency and import `com.lumiformtest.resources.R`. Keep Compose theme code in `:design`.
- Put visible text in shared string resources. Append new entries under `<!-- unchecked strings -->` in each supported locale of `:resources`.
- Decorative images use a null content description. Meaningful controls need concise accessibility text.
- Keep generated files, local SDK paths, IDE state, and build outputs outside Git.

Run relevant module checks and assemble the app when build files, manifests,
resources, Hilt wiring, or module dependencies change. Inspect lint findings;
do not suppress a warning just to get a clean report.
