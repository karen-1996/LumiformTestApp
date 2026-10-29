plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.lumiformtest.resources"
}

dependencies {
    // The shared starting theme inherits AndroidX's splash theme.
    implementation(libs.androidx.core.splashscreen)
}
