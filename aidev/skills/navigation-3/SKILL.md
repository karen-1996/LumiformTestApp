---
name: navigation-3
description: Change Navigation 3 destinations, entry providers, back-stack ownership, or restoration in this project.
---

# Navigation 3

- Use typed, serializable NavKey destinations. Pass IDs, not DTOs or whole content trees.
- Keep destination definitions and entry registration in the feature. The app combines entries and owns the saved back stack.
- Keep ViewModels independent of Navigation 3. Translate their navigation outputs in the UI.
- Retain the saveable-state and ViewModel-store entry decorators so state is scoped to each destination.
- Keep the root destination when handling Back; normal root Back behavior belongs to the Activity/system.
- Avoid adding duplicate top entries from repeated navigation commands.
- Check key serialization and the actual entry-to-entry restore/Back flow after navigation changes. Serialization alone does not prove process-death restoration.

Read the official [state guide](https://developer.android.com/guide/navigation/navigation-3/save-state)
when changing restoration or entry scope. Check the
[release notes](https://developer.android.com/jetpack/androidx/releases/navigation3)
before upgrading; use stable versions unless the task needs an experimental API.
