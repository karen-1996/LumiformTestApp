import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import javax.inject.Inject
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

buildscript {
    dependencies {
        // AGP provides Kotlin support. I pin its compiler to match the Kotlin plugins.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    jacoco
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
val coverageEnabled = providers.gradleProperty("coverage").map(String::toBoolean).orElse(false).get()
val coverageVersion = libs.versions.jacoco.get()

jacoco { toolVersion = coverageVersion }

subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            compileSdk { version = release(androidCompileSdk) }
            defaultConfig {
                minSdk = androidMinSdk
                targetSdk = androidTargetSdk
            }
            testCoverage { jacocoVersion = coverageVersion }
            buildTypes.named("debug") {
                enableUnitTestCoverage = coverageEnabled
                enableAndroidTestCoverage = coverageEnabled
            }
        }
    }

    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            compileSdk { version = release(androidCompileSdk) }
            defaultConfig { minSdk = androidMinSdk }
            testCoverage { jacocoVersion = coverageVersion }
            buildTypes.named("debug") {
                enableUnitTestCoverage = coverageEnabled
                enableAndroidTestCoverage = coverageEnabled
            }
        }
    }
}

@CacheableTask
abstract class PrepareJvmCoverageClasses : DefaultTask() {
    @get:Classpath abstract val jars: ListProperty<RegularFile>
    @get:Classpath abstract val directories: ListProperty<Directory>
    @get:Input abstract val excludedClasses: ListProperty<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @get:Inject abstract val archives: ArchiveOperations
    @get:Inject abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun prepare() {
        fileSystem.sync {
            from(directories)
            jars.get().forEach { from(archives.zipTree(it)) }
            into(outputDirectory)
            include("**/*.class")
            exclude(excludedClasses.get())
            duplicatesStrategy = DuplicatesStrategy.FAIL
        }
    }
}

// AGP supplies JaCoCo and the variant reports. Keep normal debug builds uninstrumented.
if (coverageEnabled) {
    tasks.register("debugUnitTestCoverage") {
        group = "verification"
        description = "Run JVM reports and enforce 80% line coverage for non-UI logic."
    }
    tasks.register("debugAndroidTestCoverage") {
        group = "verification"
        description = "Run debug device tests and create app/form JaCoCo reports on the selected device."
        dependsOn(":app:createDebugAndroidTestCoverageReport", ":form:createDebugAndroidTestCoverageReport")
    }

    val coverageExclusions = listOf(
        // UI rendering, previews, and Compose navigation are checked on a device.
        "**/presentation/components/**", "**/presentation/screens/*/preview/**",
        "**/presentation/screens/*/components/**", "**/presentation/screens/*/*ScreenKt*",
        "**/presentation/screens/*/*RouteKt*", "**/presentation/screens/*/*PreviewsKt*",
        "**/presentation/navigation/FormEntriesKt*", "**/presentation/navigation/ImageTransition*",
        "**/presentation/navigation/FormImageTransitionState*", "**/FormScreenTestActivity*",
        // Android/SQLite adapters keep their device tests; pure storage logic stays in scope.
        "**/data/local/FormDao*", "**/data/local/FormDatabase*", "**/data/local/RoomFormLocalDataSource*",
        "**/data/local/FormLocalDataSource*", "**/data/local/FormMigrationsKt*", "**/BitmapImageFileValidator*",
        // Dependency construction and generated classes are not the JVM logic baseline.
        "**/di/**", "**/hilt_aggregated_deps/**", "**/dagger/hilt/**", "**/Hilt_*", "**/HiltWrapper_*",
        "**/*_Factory*", "**/*_MembersInjector*", "**/*_HiltModules*", "**/*_Impl*",
        "**/R.class", "**/R\$*.class", "**/BuildConfig.class"
    )
    val jvmCoverageModules = listOf("base", "networking", "form")

    val focusedReport = tasks.register<JacocoReport>("debugJvmCoverageReport") {
        group = "verification"
        description = "Create an aggregate JVM coverage report for handwritten non-UI logic."
        dependsOn(jvmCoverageModules.map { ":$it:createDebugUnitTestCoverageReport" })
        reports {
            html.required.set(true)
            xml.required.set(true)
            csv.required.set(false)
            html.outputLocation.set(layout.buildDirectory.dir("reports/coverage/jvm/html"))
            xml.outputLocation.set(layout.buildDirectory.file("reports/coverage/jvm/report.xml"))
        }
    }

    subprojects {
        if (name in jvmCoverageModules) {
            plugins.withId("com.android.library") {
                extensions.configure<LibraryAndroidComponentsExtension> {
                    onVariants(selector().withBuildType("debug")) { variant ->
                        val preparedClasses = tasks.register<PrepareJvmCoverageClasses>("prepareDebugJvmCoverageClasses") {
                            excludedClasses.set(coverageExclusions)
                            outputDirectory.set(layout.buildDirectory.dir("coverage/jvm-classes/debug"))
                        }
                        // Read AGP's public class artifacts instead of compiler-specific build paths.
                        variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).use(preparedClasses).toGet(
                            ScopedArtifact.CLASSES, PrepareJvmCoverageClasses::jars, PrepareJvmCoverageClasses::directories
                        )
                        val debugTests = tasks.withType<Test>().matching { it.name == "testDebugUnitTest" }
                        val sources = files("src/main/java", "src/main/kotlin")
                        focusedReport.configure {
                            classDirectories.from(preparedClasses.flatMap { it.outputDirectory })
                            sourceDirectories.from(sources)
                            executionData(debugTests)
                        }
                    }
                }
            }
        }
    }

    val coverageCheck = tasks.register<JacocoCoverageVerification>("debugJvmCoverageCheck") {
        group = "verification"
        description = "Require at least 80% aggregate JVM line coverage for the documented non-UI scope."
        dependsOn(focusedReport)
        classDirectories.from(focusedReport.map { it.classDirectories })
        sourceDirectories.from(focusedReport.map { it.sourceDirectories })
        executionData.from(focusedReport.map { it.executionData })
        // Missing execution data must fail the gate rather than silently skip it.
        setOnlyIf { true }
        outputs.upToDateWhen { false }
        doFirst {
            val data = (this as JacocoCoverageVerification).executionData.files
            check(data.size == jvmCoverageModules.size && data.all { it.isFile }) {
                "JVM coverage data is missing. Run with -Pcoverage=true and all configured JVM suites."
            }
        }
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "TOTALCOUNT"
                    minimum = "1".toBigDecimal()
                }
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }

    tasks.named("debugUnitTestCoverage") { dependsOn(coverageCheck) }
}
