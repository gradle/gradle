import org.gradle.api.internal.FeaturePreviews

pluginManagement {
    repositories {
        maven {
            url = uri("https://repo.gradle.org/gradle/enterprise-libs-release-candidates")
            content {
                val rcAndMilestonesPattern = "\\d{1,2}?\\.\\d{1,2}?(\\.\\d{1,2}?)?-((rc-\\d{1,2}?)|(milestone-\\d{1,2}?))"
                // GE plugin marker artifact
                includeVersionByRegex("com.gradle.enterprise", "com.gradle.enterprise.gradle.plugin", rcAndMilestonesPattern)
                // GE plugin jar
                includeVersionByRegex("com.gradle", "gradle-enterprise-gradle-plugin", rcAndMilestonesPattern)
            }
        }
        jcenter {
            content {
                includeModule("org.openmbee.junit", "junit-xml-parser")
                includeModule("org.codehaus.groovy.modules", "http-builder-ng-core")
            }
        }
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.enterprise").version("3.13.3") // Sync with `build-logic/build-platform/build.gradle.kts`
    id("io.github.gradle.gradle-enterprise-conventions-plugin").version("0.7.6")
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}

includeBuild("build-logic-commons")
includeBuild("build-logic")

apply(from = "gradle/shared-with-buildSrc/mirrors.settings.gradle.kts")

// If you include a new subproject here, you will need to execute the
// ./gradlew generateSubprojectsInfo
// task to update metadata about the build for CI

includeSubproject("distributions-dependencies") // platform for dependency versions
includeSubproject("core-platform")              // platform for Gradle distribution core

// Gradle Distributions - for testing and for publishing a full distribution
includeSubproject("distributions-core")
includeSubproject("distributions-basics")
includeSubproject("distributions-publishing")
includeSubproject("distributions-jvm")
includeSubproject("distributions-native")
includeSubproject("distributions-full")

// Gradle implementation projects
includeSubproject("configuration-cache")
includeSubproject("functional")
includeSubproject("api-metadata")
includeSubproject("base-services")
includeSubproject("base-services-groovy")
includeSubproject("worker-services")
includeSubproject("logging-api")
includeSubproject("logging")
includeSubproject("process-services")
includeSubproject("jvm-services")
includeSubproject("core")
includeSubproject("dependency-management")
includeSubproject("wrapper")
includeSubproject("wrapper-shared")
includeSubproject("cli")
includeSubproject("launcher")
includeSubproject("bootstrap")
includeSubproject("messaging")
includeSubproject("resources")
includeSubproject("resources-http")
includeSubproject("resources-gcs")
includeSubproject("resources-s3")
includeSubproject("resources-sftp")
includeSubproject("plugins")
includeSubproject("scala")
includeSubproject("ide")
includeSubproject("ide-native")
includeSubproject("maven")
includeSubproject("code-quality")
includeSubproject("antlr")
includeSubproject("tooling-api")
includeSubproject("build-events")
includeSubproject("tooling-api-builders")
includeSubproject("signing")
includeSubproject("native")
includeSubproject("reporting")
includeSubproject("diagnostics")
includeSubproject("publish")
includeSubproject("ivy")
includeSubproject("jacoco")
includeSubproject("build-init")
includeSubproject("build-option")
includeSubproject("platform-base")
includeSubproject("platform-native")
includeSubproject("platform-jvm")
includeSubproject("language-jvm")
includeSubproject("language-java")
includeSubproject("java-compiler-plugin")
includeSubproject("language-groovy")
includeSubproject("language-native")
includeSubproject("tooling-native")
includeSubproject("plugin-use")
includeSubproject("plugin-development")
includeSubproject("model-core")
includeSubproject("model-groovy")
includeSubproject("build-cache-http")
includeSubproject("testing-base")
includeSubproject("testing-native")
includeSubproject("testing-jvm")
includeSubproject("testing-jvm-infrastructure")
includeSubproject("testing-junit-platform")
includeSubproject("test-kit")
includeSubproject("installation-beacon")
includeSubproject("composite-builds")
includeSubproject("workers")
includeSubproject("persistent-cache")
includeSubproject("build-cache-base")
includeSubproject("build-cache")
includeSubproject("core-api")
includeSubproject("version-control")
includeSubproject("file-collections")
includeSubproject("file-temp")
includeSubproject("files")
includeSubproject("hashing")
includeSubproject("snapshots")
includeSubproject("file-watching")
includeSubproject("build-cache-packaging")
includeSubproject("execution")
includeSubproject("build-profile")
includeSubproject("kotlin-dsl")
includeSubproject("kotlin-dsl-provider-plugins")
includeSubproject("kotlin-dsl-tooling-models")
includeSubproject("kotlin-dsl-tooling-builders")
includeSubproject("worker-processes")
includeSubproject("base-annotations")
includeSubproject("security")
includeSubproject("normalization-java")
includeSubproject("enterprise")
includeSubproject("enterprise-operations")
includeSubproject("enterprise-logging")
includeSubproject("enterprise-workers")
includeSubproject("build-operations")
includeSubproject("problems")
includeSubproject("instrumentation-agent")
includeSubproject("instrumentation-declarations")

// JVM Platform
includePlatform("ear", "jvm")

// Plugin portal projects
includeSubproject("kotlin-dsl-plugins")

// Internal utility and verification projects
includeSubproject("internal-instrumentation-api")
includeSubproject("internal-instrumentation-processor")
includeSubproject("docs")
includeSubproject("docs-asciidoctor-extensions-base")
includeSubproject("docs-asciidoctor-extensions")
includeSubproject("samples")
includeSubproject("architecture-test")
includeSubproject("internal-testing")
includeSubproject("internal-integ-testing")
includeSubproject("internal-performance-testing")
includeSubproject("internal-architecture-testing")
includeSubproject("internal-build-reports")
includeSubproject("integ-test")
includeSubproject("kotlin-dsl-integ-tests")
includeSubproject("distributions-integ-tests")
includeSubproject("soak")
includeSubproject("smoke-test")
includeSubproject("performance")
includeSubproject("build-scan-performance")

rootProject.name = "gradle"

fun includeSubproject(projectName: String) {
    include(projectName)
    project(":$projectName").projectDir = file("subprojects/$projectName")
}

fun includePlatform(projectName: String, platformName: String) {
    include(projectName)
    project(":$projectName").projectDir = file("platforms/$platformName/$projectName")
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
        throw GradleException("This build requires JDK 11. It's currently ${getBuildJavaHome()}. You can ignore this check by passing '-Dorg.gradle.ignoreBuildJavaVersionCheck=true'.")
    }
}
