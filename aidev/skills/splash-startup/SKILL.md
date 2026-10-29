---
name: splash-startup
description: Change the AndroidX SplashScreen launcher and its handoff to MainActivity.
---

# App startup

- SplashActivity is the launcher; MainActivity owns the content and Navigation 3 back stack.
- Install the AndroidX splash before `super.onCreate`, and use a Theme.SplashScreen starting theme with the normal post-splash theme.
- Keep splash XML themes and icons in `:resources`, with the other Android resources.
- Keep the system splash during the Activity handoff, then finish the routing Activity. Do not draw a second splash layout or add a fixed delay.
- Keep fetching and offline content decisions outside the launcher. A slow connection must not hold startup indefinitely.
- Preserve an existing MainActivity when routing to it again; avoid stacking duplicate main screens.
- Keep MainActivity internal to the app unless an actual external entry point is introduced.
- Verify a launcher start reaches MainActivity and Back cannot return to SplashActivity. Check cold launch visually on a device when changing splash themes.

See Android's [routing Activity guidance](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate)
when changing the handoff. The separate launcher Activity is this project's
chosen structure; it is not a rule for all Android apps.
