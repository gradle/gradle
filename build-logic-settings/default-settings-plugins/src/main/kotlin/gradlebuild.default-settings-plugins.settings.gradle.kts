/*
 * Copyright 2026 the original author or authors.
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
        maven {
            url = uri("https://repo.gradle.org/gradle/enterprise-libs-release-candidates")
            content {
                val rcAndMilestonesPattern = "\\d{1,2}?\\.\\d{1,2}?(\\.\\d{1,2}?)?-((rc-\\d{1,2}?)|(milestone-\\d{1,2}?))"
                // DV plugin marker artifact
                includeVersionByRegex("com.gradle.develocity", "com.gradle.develocity.gradle.plugin", rcAndMilestonesPattern)
                // DV plugin jar
                includeVersionByRegex("com.gradle", "develocity-gradle-plugin", rcAndMilestonesPattern)
            }
        }
    }
}

plugins {
    id("com.gradle.develocity")
    id("io.github.gradle.develocity-conventions-plugin")
    id("org.gradle.toolchains.foojay-resolver-convention")
}

