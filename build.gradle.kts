import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

buildscript {
    dependencies {
        // AGP provides Kotlin support. I pin its compiler to match the Kotlin plugins.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
}

fun androidSdkVersionProperty(name: String): Int =
    providers.gradleProperty(name).orNull?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        ?: throw GradleException("Gradle property $name must be a positive integer.")

val androidCompileSdk = androidSdkVersionProperty("android.compileSdk")
val androidMinSdk = androidSdkVersionProperty("android.minSdk")
val androidTargetSdk = androidSdkVersionProperty("android.targetSdk")

subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            compileSdk { version = release(androidCompileSdk) }
            defaultConfig {
                minSdk = androidMinSdk
                targetSdk = androidTargetSdk
            }
        }
    }

    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            compileSdk { version = release(androidCompileSdk) }
            defaultConfig { minSdk = androidMinSdk }
        }
    }
}
