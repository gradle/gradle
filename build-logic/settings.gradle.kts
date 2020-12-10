pluginManagement {
    (this as org.gradle.plugin.management.internal.PluginManagementSpecInternal).includeBuild("../build-logic-base")
}

plugins {
    id("gradlebuild.settings-plugins")
    id("gradlebuild.repositories")
}

includeBuild("../build-logic-commons")

// Platform: defines shared dependency versions
include("build-platform")

// Utilities for updating the build itself which are not part of the usual build process
include("build-update-utils")

// Collection of plugins for the root build to configure lifecycle, reporting and IDE integration
include("root-build")

// Shared basics for all
include("basics")

// Compute the identity/version we are building and related details (like current git commit)
include("module-identity")

// Shared information about external modules
include("dependency-modules")

// Special purpose build logic for root project - please preserve alphabetical order
include("cleanup")
include("idea")
include("lifecycle")

// Special purpose build logic for subproject - please preserve alphabetical order
include("binary-compatibility")
include("build-init-samples")
include("buildquality")
include("documentation")
include("integration-testing")
include("jvm")
include("kotlin-dsl")
include("uber-plugins")
include("packaging")
include("performance-testing")
include("profiling")
include("publishing")

fun remoteBuildCacheEnabled(settings: Settings) = settings.buildCache.remote?.isEnabled == true

fun isAdoptOpenJDK() = true == System.getProperty("java.vendor")?.contains("AdoptOpenJDK")

fun isAdoptOpenJDK11() = isAdoptOpenJDK() && JavaVersion.current().isJava11

fun getBuildJavaHome() = System.getProperty("java.home")

gradle.settingsEvaluated {
    if ("true" == System.getProperty("org.gradle.ignoreBuildJavaVersionCheck")) {
        return@settingsEvaluated
    }

    if (remoteBuildCacheEnabled(this) && !isAdoptOpenJDK11()) {
        throw GradleException("Remote cache is enabled, which requires AdoptOpenJDK 11 to perform this build. It's currently ${getBuildJavaHome()}.")
    }
}
