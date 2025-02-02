/*
 * Copyright 2020 the original author or authors.
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

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()

        maven {
            url = uri("https://repo.gradle.org/gradle/enterprise-libs-release-candidates")
            content {
                val rcAndMilestonesPattern = "\\d{1,2}?\\.\\d{1,2}?(\\.\\d{1,2}?)?-((rc-\\d{1,2}?)|(milestone-\\d{1,2}?))"
                // GE plugin marker artifact
                includeVersionByRegex("com.gradle.develocity", "com.gradle.develocity.gradle.plugin", rcAndMilestonesPattern)
                // GE plugin jar
                includeVersionByRegex("com.gradle", "develocity-gradle-plugin", rcAndMilestonesPattern)
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.9.0")
}

includeBuild("../build-logic-settings")

// Shared basics for all
include("basics")

// Platform: defines shared dependency versions
include("build-platform")

// Compute the identity/version we are building and related details (like current git commit)
include("module-identity")

// Code quality rules common to :build-logic and the root build
include("code-quality-rules")

// Plugins to build :build-logic plugins
include("gradle-plugin")

// Plugins to publish gradle projects
include("publishing")

rootProject.name = "build-logic-commons"

// Make sure all the build-logic is compiled for the right Java version
gradle.lifecycle.beforeProject {
    pluginManager.withPlugin("java-base") {
        the<JavaPluginExtension>().toolchain {
            // if you change this java version please also consider changing .idea/misc.xml#project/component(@project-jdk-name}
            // Also, there are a lot of other places this should be changed.
            languageVersion = JavaLanguageVersion.of(17)
            vendor = JvmVendorSpec.ADOPTIUM
        }
    }
}
