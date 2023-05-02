dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}

include("kotlin-dsl-shared")

rootProject.name = "build-logic-shared"
