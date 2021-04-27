import org.gradle.api.internal.FeaturePreviews

pluginManagement {
    includeBuild("build-logic-settings")
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
        maven { url = uri("https://repo.gradle.org/gradle/enterprise-libs-release-candidates-local") }
    }
}

plugins {
    id("com.gradle.enterprise").version("3.6.1")
    id("com.gradle.enterprise.gradle-enterprise-conventions-plugin").version("0.7.2")
    id("gradlebuild.base.allprojects")
    // Keep version with `build-logic/build-platform/buildSrc.gradle.kts` in sync
    id("com.gradle.enterprise.test-distribution").version("2.0.3-rc-2")
    id("com.gradle.internal.test-selection").version("0.4.0-rc-1")
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
include("data-structures")
include("api-metadata")
include("base-services")
include("base-services-groovy")
include("logging")
include("process-services")
include("jvm-services")
include("core")
include("dependency-management")
include("wrapper")
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
include("internal-android-performance-testing")
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
