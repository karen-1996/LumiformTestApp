# Lumiform Android Challenge

My implementation of the Lumiform Android coding challenge.

## Status

This first commit sets up a minimal Compose app. The form viewer, offline storage,
and custom MVI implementation will follow in small, focused commits.

## Setup

- Use Android Studio with support for Android Gradle Plugin 9.5.
- Use JDK 25 for Gradle, as configured in `gradle/gradle-daemon-jvm.properties`.
- Install Android SDK 37. The minimum supported Android version is 8.0 (API 26).
- Open the project and let Gradle sync. Set the Android SDK location in Android
  Studio or in an untracked `local.properties` file using `sdk.dir`.

## Build

```shell
./gradlew :app:assembleDebug :app:lintDebug
```

On Windows, use `gradlew.bat`. Run the `app` configuration on an emulator or device
with API 26 or newer. The current screen only displays the app name.

Feature tests and their run commands will be added with the implementation.
