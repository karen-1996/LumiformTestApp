import com.android.build.api.variant.HostTestBuilder

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.lumiformtest.networking"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        debug {
            buildConfigField(
                "String", "BASE_URL",
                "\"${providers.gradleProperty("networking.baseUrl.debug").get()}\""
            )
        }
        release {
            buildConfigField(
                "String", "BASE_URL",
                "\"${providers.gradleProperty("networking.baseUrl.release").get()}\""
            )
        }
    }
}

androidComponents {
    // Check the release logging policy too; AGP only enables debug tests by default.
    beforeVariants(selector().withBuildType("release")) {
        it.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}

dependencies {
    // Features use these types to declare their request APIs.
    api(libs.retrofit)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}
