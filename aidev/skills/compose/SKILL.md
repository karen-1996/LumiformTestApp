---
name: compose
description: Change Compose screens, state-driven UI, themes, previews, or accessibility in this project.
---

# Compose

- Keep screen content stateless and connect the ViewModel in a route composable.
- Keep each screen, route, and owned components beside its `viewmodel`, `model`, and `mapper` packages in `presentation/screens/<screen>`. Screen preview files and helpers live in its `preview` package; component previews can stay beside their components. Shared components and navigation stay at presentation level.
- Collect screen state with `collectAsStateWithLifecycle`. Collect temporary outputs from the entry's lifecycle; keep a single consumer per output.
- Use MaterialTheme and existing semantic resources for colors and typography. Do not add raw colors to screen code.
- Follow the system light/dark mode. Keep native window/splash resources and system-bar icons aligned with Compose, and keep dynamic palette caches keyed by configuration changes.
- Use string resources from `:resources` for visible text and shared resources for icons and other assets. Import `com.lumiformtest.resources.R`; do not add feature-local resources.
- Append new string entries under `<!-- unchecked strings -->` in every supported locale of `:resources`.
- Use null descriptions for decorative images and useful labels for controls.
- Respect Scaffold and system-bar insets. Check text wrapping, font scaling, and small-screen layout when changing visual content.
- Put reusable components in component files. Do not add composable extension functions to screen files.
- Provide a named @Preview for every screen and visual component, including private visual building blocks. Keep it in the same file or a nearby *Previews.kt file and wrap it in the app theme.
- Show meaningful states where relevant: content, loading, failure, selection, disabled controls, and long text. Include dark/small-width/large-font cases when useful.
- Share sample UI fixtures and local drawable assets. Keep previews independent of Hilt, network access, image-loader initialization, and databases.
- Preview route/navigation wiring through its stateless screen content. Non-visual state/resource helpers and preview wrappers do not need standalone UI previews.
- Keep lazy item keys based on validated node IDs and content types based on row structure. Key response options by response-set/option IDs so reordered definitions retain the correct focus and state.
- Limit item captures to row inputs and shared callbacks. Reuse unchanged rows from mapping and verify skippability with compiler reports before adding stability annotations.
- Prepare the lazy image loader off the UI thread and share its State at the form entry. Read that State only in image consumers so returning rows reuse the prepared loader immediately.
- Keep decoded-thumbnail/cache tests separate from frame-time claims. Compiler reports and software-rendered emulator behavior do not establish a frame-rate improvement.

Use focused UI checks for actual interactions and restoration. Scan changed
screens for hardcoded visible strings and colors before finishing.
