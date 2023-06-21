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
    id("com.gradle.enterprise").version("3.13.4") // Sync with `build-logic/build-platform/build.gradle.kts`
    id("io.github.gradle.gradle-enterprise-conventions-plugin").version("0.7.6")
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}

includeBuild("build-logic-commons")
includeBuild("build-logic")

apply(from = "gradle/shared-with-buildSrc/mirrors.settings.gradle.kts")

// If you include a new subproject here, you will need to execute the
// ./gradlew generateSubprojectsInfo
// task to update metadata about the build for CI

unassigned {
    subproject("distributions-dependencies") // platform for dependency versions
    subproject("core-platform")              // platform for Gradle distribution core
}

// Gradle Distributions - for testing and for publishing a full distribution
unassigned {
    subproject("distributions-core")
    subproject("distributions-basics")
    subproject("distributions-publishing")
    subproject("distributions-jvm")
    subproject("distributions-native")
    subproject("distributions-full")
}

// Gradle implementation projects
unassigned {
    subproject("configuration-cache")
    subproject("functional")
    subproject("api-metadata")
    subproject("base-services")
    subproject("base-services-groovy")
    subproject("worker-services")
    subproject("logging-api")
    subproject("logging")
    subproject("process-services")
    subproject("jvm-services")
    subproject("core")
    subproject("dependency-management")
    subproject("wrapper")
    subproject("wrapper-shared")
    subproject("cli")
    subproject("launcher")
    subproject("bootstrap")
    subproject("messaging")
    subproject("resources")
    subproject("resources-http")
    subproject("resources-gcs")
    subproject("resources-s3")
    subproject("resources-sftp")
    subproject("plugins")
    subproject("scala")
    subproject("ide")
    subproject("ide-native")
    subproject("maven")
    subproject("code-quality")
    subproject("antlr")
    subproject("tooling-api")
    subproject("build-events")
    subproject("tooling-api-builders")
    subproject("signing")
    subproject("native")
    subproject("reporting")
    subproject("diagnostics")
    subproject("publish")
    subproject("ivy")
    subproject("jacoco")
    subproject("build-init")
    subproject("build-option")
    subproject("platform-base")
    subproject("platform-native")
    subproject("platform-jvm")
    subproject("language-jvm")
    subproject("language-java")
    subproject("java-compiler-plugin")
    subproject("language-groovy")
    subproject("language-native")
    subproject("tooling-native")
    subproject("plugin-use")
    subproject("plugin-development")
    subproject("model-core")
    subproject("model-groovy")
    subproject("build-cache-http")
    subproject("testing-base")
    subproject("testing-native")
    subproject("testing-jvm")
    subproject("testing-jvm-infrastructure")
    subproject("testing-junit-platform")
    subproject("test-kit")
    subproject("installation-beacon")
    subproject("composite-builds")
    subproject("workers")
    subproject("persistent-cache")
    subproject("build-cache-base")
    subproject("build-cache")
    subproject("core-api")
    subproject("version-control")
    subproject("file-collections")
    subproject("file-temp")
    subproject("files")
    subproject("hashing")
    subproject("snapshots")
    subproject("file-watching")
    subproject("build-cache-packaging")
    subproject("execution")
    subproject("build-profile")
    subproject("kotlin-dsl")
    subproject("kotlin-dsl-provider-plugins")
    subproject("kotlin-dsl-tooling-models")
    subproject("kotlin-dsl-tooling-builders")
    subproject("worker-processes")
    subproject("base-annotations")
    subproject("security")
    subproject("normalization-java")
    subproject("enterprise")
    subproject("enterprise-operations")
    subproject("enterprise-logging")
    subproject("enterprise-workers")
    subproject("build-operations")
    subproject("problems")
    subproject("instrumentation-agent")
    subproject("instrumentation-declarations")
}

// JVM Platform
platform("jvm") {
    subproject("ear")
    subproject("war")
}

// Plugin portal projects
unassigned {
    subproject("kotlin-dsl-plugins")
}

// Internal utility and verification projects
unassigned {
    subproject("internal-instrumentation-api")
    subproject("internal-instrumentation-processor")
    subproject("docs")
    subproject("docs-asciidoctor-extensions-base")
    subproject("docs-asciidoctor-extensions")
    subproject("samples")
    subproject("architecture-test")
    subproject("internal-testing")
    subproject("internal-integ-testing")
    subproject("internal-performance-testing")
    subproject("internal-architecture-testing")
    subproject("internal-build-reports")
    subproject("integ-test")
    subproject("kotlin-dsl-integ-tests")
    subproject("distributions-integ-tests")
    subproject("soak")
    subproject("smoke-test")
    subproject("performance")
    subproject("build-scan-performance")
    subproject("precondition-tester")
}

rootProject.name = "gradle"

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

// region platform include DSL

fun platform(platformName: String, platformConfiguration: PlatformScope.() -> Unit) =
    PlatformScope("platforms/$platformName").platformConfiguration()

fun unassigned(platformConfiguration: PlatformScope.() -> Unit) =
    PlatformScope("subprojects").platformConfiguration()

class PlatformScope(
    private val basePath: String
) {
    fun subproject(projectName: String) {
        include(projectName)
        project(":$projectName").projectDir = file("$basePath/$projectName")
    }
}

// endregion
