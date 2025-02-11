@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("./declarative")

    // Check out this project as a sibling to now-in-android, that contains the declarative prototype project to use this
    includeBuild("../now-in-android/declarative-gradle/unified-prototype/unified-plugin")

    repositories {
        mavenCentral()
        gradlePluginPortal {
            content {
                includeGroupAndSubgroups("com.gradle")
                includeGroupAndSubgroups("org.gradle")
                includeGroupAndSubgroups("io.github.gradle")
            }
        }
    }
}

plugins {
    id("com.gradle.enterprise").version("3.16.2")
    id("io.github.gradle.gradle-enterprise-conventions-plugin").version("0.9.1")
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.8.0")

    id("org.gradle.experimental.kmp-ecosystem").version("0.1.37")

    id("org.gradle.client.ecosystem.custom-ecosystem")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        gradlePluginPortal {
            content {
                includeGroupAndSubgroups("org.gradle")
            }
        }
        maven(url = "https://repo.gradle.org/gradle/libs-releases") {
            content {
                includeGroup("org.gradle")
            }
        }
        maven(url = "https://repo.gradle.org/gradle/libs-snapshots") {
            content {
                includeGroup("org.gradle")
            }
        }
        mavenCentral()
    }
}

// move this to daemon toolchain once Gradle supports it
require(JavaVersion.current() == JavaVersion.VERSION_17) {
    "This build requires Java 17, currently using ${JavaVersion.current()}"
}

rootProject.name = "gradle-client-root"

include(":gradle-client")
include(":build-action")
include(":mutations-demo")
