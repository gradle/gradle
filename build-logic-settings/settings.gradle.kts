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
    }
}

plugins {
    id("com.gradle.develocity").version("3.17.6") // Run `build-logic-settings/update-develocity-plugin-version.sh <new-version>` to update
    id("io.github.gradle.gradle-enterprise-conventions-plugin").version("0.10.1")
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.8.0")
}

include("build-environment")

rootProject.name = "build-logic-settings"
