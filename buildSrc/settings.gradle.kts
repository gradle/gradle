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

fun RepositoryHandler.kotlinDev() =
    maven(url = "https://dl.bintray.com/kotlin/kotlin-dev")

pluginManagement {
    repositories {
        kotlinDev()
        gradlePluginPortal()
    }
}

gradle.rootProject {
    allprojects {
        repositories {
            kotlinDev()
        }
    }
}

apply(from = "../gradle/shared-with-buildSrc/build-cache-configuration.settings.gradle.kts")

val upperCaseLetters = "\\p{Upper}".toRegex()

fun String.toKebabCase() =
    replace(upperCaseLetters) { "-${it.value.toLowerCase()}" }

rootProject.name = "buildSrc"

// Please preserve alphabetical order
include("binaryCompatibility")
include("build")
include("buildquality")
include("cleanup")
include("configuration")
include("docs")
include("ide")
include("integrationTesting")
include("kotlinDsl")
include("uberPlugins")
include("packaging")
include("plugins")
include("profiling")
include("performance")
include("versioning")

fun buildFileNameFor(projectDirName: String) =
    "$projectDirName.gradle.kts"

for (project in rootProject.children) {
    val projectDirName = project.name.toKebabCase()
    project.projectDir = file("subprojects/$projectDirName")
    project.buildFileName = buildFileNameFor(projectDirName)
    assert(project.projectDir.isDirectory)
    assert(project.buildFile.isFile)
}

