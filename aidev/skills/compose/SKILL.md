---
name: compose
description: Change Compose screens, state-driven UI, themes, previews, or accessibility in this project.
---

# Compose

- Keep screen content stateless and connect the ViewModel in a route composable.
- Collect screen state with `collectAsStateWithLifecycle`. Collect temporary outputs from the entry's lifecycle; keep a single consumer per output.
- Use MaterialTheme and existing semantic resources for colors and typography. Do not add raw colors to screen code.
- Use string resources from `:resources` for visible text and shared resources for icons and other assets. Import `com.lumiformtest.resources.R`; do not add feature-local resources.
- Append new string entries under `<!-- unchecked strings -->` in every supported locale of `:resources`.
- Use null descriptions for decorative images and useful labels for controls.
- Respect Scaffold and system-bar insets. Check text wrapping, font scaling, and small-screen layout when changing visual content.
- Put reusable components in component files. Do not add composable extension functions to screen files.
- Keep previews independent of Hilt, network access, and databases.

Use focused UI checks for actual interactions and restoration. Scan changed
screens for hardcoded visible strings and colors before finishing.
