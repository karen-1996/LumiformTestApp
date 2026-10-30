import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val appVersionFile = rootProject.layout.projectDirectory.file("version.properties")
val appVersionProperties = Properties().apply {
    val content = providers.fileContents(appVersionFile).asText.orNull
        ?: throw GradleException("Missing app version properties file: ${appVersionFile.asFile.path}")
    content.reader().use(::load)
}

fun appVersionProperty(name: String): String =
    appVersionProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("Missing $name in ${appVersionFile.asFile.path}")

val appVersionCode = appVersionProperty("versionCode").toIntOrNull()?.takeIf { it > 0 }
    ?: throw GradleException("versionCode must be a positive integer in ${appVersionFile.asFile.path}")
val appVersionName = appVersionProperty("versionName")

android {
    namespace = "com.lumiformtest.app"

    defaultConfig {
        applicationId = "com.lumiformtest.app"
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":design"))
    implementation(project(":form"))
    implementation(project(":networking"))
    implementation(project(":resources"))
    implementation(libs.coil.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
