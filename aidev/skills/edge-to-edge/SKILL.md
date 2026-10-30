---
name: edge-to-edge
description: Keep system bars, safe areas, and native startup themes aligned with this Compose app's light and dark modes.
---

# Edge-to-edge and system themes

- Keep AndroidX enableEdgeToEdge in MainActivity and the routing SplashActivity. AndroidX owns the system splash; do not add another Compose splash screen or a fixed delay.
- Follow the system night configuration. Keep window/splash backgrounds and light-system-bar flags in qualified resources under :resources; let the Activities update their bar appearance after configuration changes.
- Keep Compose palette caches sensitive to configuration/resource changes, including night mode and dynamic system colors.
- Apply Scaffold/system insets once and keep the first/last controls reachable. When changing list insets, prefer content padding so the viewport can draw behind bars; avoid adding extra app-bar padding.
- Account for status/navigation contrast and gesture/three-button navigation differences. Let AndroidX provide the navigation contrast scrim where needed.
- Preserve original image colors. The shared neutral image canvas supports transparent black artwork in either app mode.
- If text inputs are introduced, handle IME insets and manifest resize behavior together; the current form text questions are display-only.
- Verify relevant startup, navigation, palette, and configuration-change behavior with debug checks. Follow Karen's request to run release checks only when explicitly requested.

Official reference: [Android edge-to-edge guidance](https://developer.android.com/develop/ui/views/layout/edge-to-edge).
