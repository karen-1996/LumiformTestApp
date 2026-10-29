plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.lumiformtest.resources"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
}

dependencies {
    // The shared starting theme inherits AndroidX's splash theme.
    implementation(libs.androidx.core.splashscreen)
}
