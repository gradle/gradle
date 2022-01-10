/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            name = "Gradle Enterprise release candidates"
            url = uri("https://repo.gradle.org/gradle/enterprise-libs-release-candidates")
            content {
                val rcAndMilestonesPattern = "\\d{1,2}?\\.\\d{1,2}?(\\.\\d{1,2}?)?-((rc-\\d{1,2}?)|(milestone-\\d{1,2}?))"
                includeVersionByRegex("com.gradle", "gradle-enterprise-gradle-plugin", rcAndMilestonesPattern)
                includeVersionByRegex("com.gradle.enterprise", "test-distribution-gradle-plugin", rcAndMilestonesPattern)
                includeVersionByRegex("com.gradle.internal", "test-selection-gradle-plugin", rcAndMilestonesPattern)
            }
        }
        maven {
            name = "Gradle public repository"
            url = uri("https://repo.gradle.org/gradle/public")
            content {
                includeModule("classycle", "classycle")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

includeBuild("../build-logic-commons")

apply(from = "../gradle/shared-with-buildSrc/mirrors.settings.gradle.kts")

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

// Third party installation build logic for root project - please preserve alphabetical order
include("third-party-installations")

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
