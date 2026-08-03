/*
 * Copyright 2024 the original author or authors.
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
        mavenCentral()
        gradlePluginPortal()
        maven {
            name = "Kotlin dev repository"
            url = uri("https://packages.jetbrains.team/maven/p/kt/dev")
            content {
                includeGroupByRegex("org\\.jetbrains\\.kotlin(\\..+)?")
            }
        }
    }
    versionCatalogs {
        create("buildLibs") {
            from(files("../gradle/dependency-management/build.versions.toml"))
            version("errorProne", "stub") // not used in this project
        }
    }
}

pluginManagement {
    repositories {
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

        gradlePluginPortal()

        maven {
            name = "Kotlin dev repository"
            url = uri("https://packages.jetbrains.team/maven/p/kt/dev")
            content {
                includeGroupByRegex("org\\.jetbrains\\.kotlin(\\..+)?")
            }
        }
    }
}

include("architecture-docs")
include("build-environment")
include("configuration-cache-compatibility")
include("default-settings-plugins")
include("version-catalogs")

rootProject.name = "build-logic-settings"

// When run by a Gradle embedding a dev Kotlin, the published `kotlin-dsl` plugin strictly pins the previous Kotlin version
if (Regex(""".+-\d+$""").matches(embeddedKotlinVersion)) {
    gradle.lifecycle.beforeProject {
        configurations.all {
            resolutionStrategy.dependencySubstitution {
                for (module in listOf("kotlin-stdlib", "kotlin-reflect")) {
                    substitute(module("org.jetbrains.kotlin:$module"))
                        .using(module("org.jetbrains.kotlin:$module:$embeddedKotlinVersion"))
                }
            }
        }
    }
}
