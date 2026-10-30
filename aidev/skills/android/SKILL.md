---
name: android
description: Change Android code, manifests, resources, Gradle modules, and Android tests in this project.
---

# Android

- Place feature modules under `feature/`. Keep feature layers inside their feature module; `:form` maps to `feature/form` in settings.
- Shared HTTP setup belongs in `:networking`: provide singleton OkHttp and Retrofit instances through Hilt. Features depend on this module and only create their own request APIs from Retrofit. Keep HTTP body logging limited to debug builds.
- Read per-build-type base URLs from `networking.baseUrl.debug` and `networking.baseUrl.release` Gradle properties, exposed through the networking module's `BuildConfig.BASE_URL`. Keep endpoint hosts out of production Kotlin code; base URLs need a trailing slash.
- Use AGP's built-in Kotlin support. Keep Kotlin, Compose, serialization, and KSP versions compatible; use the version catalog.
- Keep app versionCode/versionName in root `version.properties`. Keep SDK versions in `gradle.properties`; the root build script applies them to every Android module, following ASA. Do not repeat SDK values in module scripts.
- Add dependencies when their implementation needs them. Check current stable versions in official release notes rather than guessing.
- Keep Android Context out of ViewModels. Put platform work behind context-free domain contracts.
- Use existing theme resources and MaterialTheme for colors. Leave vector asset palettes unchanged unless the task asks to edit them.
- Keep shared Android resources in `resources/src/main/res`, including UI strings, icons, layouts, fonts, XML themes, and configuration XML. Feature modules have no local resources. App-owned non-translatable identity strings, such as `app_name`, belong in `app/src/main/res/values/strings_non_translatable.xml` with `translatable="false"`.
- Give resource consumers a direct `:resources` dependency and import `com.lumiformtest.resources.R`. Keep Compose theme code in `:design`.
- Put visible text in shared string resources. Append new entries under `<!-- unchecked strings -->` in each supported locale of `:resources`.
- Decorative images use a null content description. Meaningful controls need concise accessibility text.
- Keep routine generated build files, local SDK paths, and IDE state outside Git. Commit exported Room schema snapshots under `feature/form/schemas` for database version history.

Run relevant module checks and assemble the app when build files, manifests,
resources, Hilt wiring, or module dependencies change. Inspect lint findings;
do not suppress a warning just to get a clean report.
