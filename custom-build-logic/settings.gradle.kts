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
        mavenCentral()
        gradlePluginPortal()
    }
}

includeBuild("../build-logic-commons")

include("custom")

apply(from = "../gradle/shared-with-buildSrc/mirrors.settings.gradle.kts")

rootProject.name = "custom-build-logic"
