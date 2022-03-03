import org.gradle.api.internal.FeaturePreviews

pluginManagement {
    includeBuild("build-logic-settings")
    repositories {
        maven {
            url = uri("https://repo.gradle.org/gradle/enterprise-libs-release-candidates")
            content {
                val rcAndMilestonesPattern = "\\d{1,2}?\\.\\d{1,2}?(\\.\\d{1,2}?)?-((rc-\\d{1,2}?)|(milestone-\\d{1,2}?))"
                includeVersionByRegex("com.gradle", "gradle-enterprise-gradle-plugin", rcAndMilestonesPattern)
                includeVersionByRegex("com.gradle.enterprise", "test-distribution-gradle-plugin", rcAndMilestonesPattern)
                includeVersionByRegex("com.gradle.enterprise.test-distribution", "com.gradle.enterprise.test-distribution.gradle.plugin", rcAndMilestonesPattern)
                includeVersionByRegex("com.gradle.internal", "test-selection-gradle-plugin", rcAndMilestonesPattern)
                includeVersionByRegex("com.gradle.internal.test-selection", "com.gradle.internal.test-selection.gradle.plugin", rcAndMilestonesPattern)
            }
        }
        maven {
            name = "Kotlin EAP repository"
            url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap/")
            content {
                includeVersionByRegex("org.jetbrains.kotlin", "kotlin-.*", "1.7.0-dev-1904")
            }
        }
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.enterprise").version("3.8.1")
    id("io.github.gradle.gradle-enterprise-conventions-plugin").version("0.7.6")
    id("gradlebuild.base.allprojects")
    id("com.gradle.enterprise.test-distribution").version("2.2.3") // Sync with `build-logic/build-platform/build.gradle.kts`
    id("gradlebuild.internal.testfiltering")
    id("com.gradle.internal.test-selection").version("0.6.5-rc-1")
    id("gradlebuild.internal.cc-experiment")
}

includeBuild("build-logic-commons")
includeBuild("build-logic")

apply(from = "gradle/shared-with-buildSrc/mirrors.settings.gradle.kts")

// If you include a new subproject here, you will need to execute the
// ./gradlew generateSubprojectsInfo
// task to update metadata about the build for CI

include("distributions-dependencies") // platform for dependency versions
include("core-platform")              // platform for Gradle distribution core

// Gradle Distributions - for testing and for publishing a full distribution
include("distributions-core")
include("distributions-basics")
include("distributions-publishing")
include("distributions-jvm")
include("distributions-native")
include("distributions-full")

// Gradle implementation projects
include("configuration-cache")
include("functional")
include("api-metadata")
include("base-services")
include("base-services-groovy")
include("worker-services")
include("logging-api")
include("logging")
include("process-services")
include("jvm-services")
include("core")
include("dependency-management")
include("wrapper")
include("wrapper-shared")
include("cli")
include("launcher")
include("bootstrap")
include("messaging")
include("resources")
include("resources-http")
include("resources-gcs")
include("resources-s3")
include("resources-sftp")
include("plugins")
include("scala")
include("ide")
include("ide-native")
include("maven")
include("code-quality")
include("antlr")
include("tooling-api")
include("build-events")
include("tooling-api-builders")
include("signing")
include("ear")
include("native")
include("reporting")
include("diagnostics")
include("publish")
include("ivy")
include("jacoco")
include("build-init")
include("build-option")
include("platform-base")
include("platform-native")
include("platform-jvm")
include("language-jvm")
include("language-java")
include("java-compiler-plugin")
include("language-groovy")
include("language-native")
include("tooling-native")
include("plugin-use")
include("plugin-development")
include("model-core")
include("model-groovy")
include("build-cache-http")
include("testing-base")
include("testing-native")
include("testing-jvm")
include("testing-junit-platform")
include("test-kit")
include("installation-beacon")
include("composite-builds")
include("workers")
include("persistent-cache")
include("build-cache-base")
include("build-cache")
include("core-api")
include("version-control")
include("file-collections")
include("file-temp")
include("files")
include("hashing")
include("snapshots")
include("file-watching")
include("build-cache-packaging")
include("execution")
include("build-profile")
include("kotlin-compiler-embeddable")
include("kotlin-dsl")
include("kotlin-dsl-provider-plugins")
include("kotlin-dsl-tooling-models")
include("kotlin-dsl-tooling-builders")
include("worker-processes")
include("base-annotations")
include("security")
include("normalization-java")
include("enterprise")
include("enterprise-operations")
include("enterprise-logging")
include("enterprise-workers")
include("build-operations")
include("problems")

// Plugin portal projects
include("kotlin-dsl-plugins")

// Internal utility and verification projects
include("docs")
include("samples")
include("architecture-test")
include("internal-testing")
include("internal-integ-testing")
include("internal-performance-testing")
include("internal-build-reports")
include("integ-test")
include("kotlin-dsl-integ-tests")
include("distributions-integ-tests")
include("soak")
include("smoke-test")
include("performance")
include("build-scan-performance")
include("configuration-cache-report")

rootProject.name = "gradle"

for (project in rootProject.children) {
    project.projectDir = file("subprojects/${project.name}")
}

FeaturePreviews.Feature.values().forEach { feature ->
    if (feature.isActive) {
        enableFeaturePreview(feature.name)
    }
}

fun remoteBuildCacheEnabled(settings: Settings) = settings.buildCache.remote?.isEnabled == true

fun getBuildJavaHome() = System.getProperty("java.home")

gradle.settingsEvaluated {
    if ("true" == System.getProperty("org.gradle.ignoreBuildJavaVersionCheck")) {
        return@settingsEvaluated
    }

    if (!JavaVersion.current().isJava11) {
        throw GradleException("This build requires JDK 11. It's currently ${getBuildJavaHome()}. You can ignore this check by passing '-Dorg.gradle.ignoreBuildJavaVersionCheck'.")
    }
}
