import org.gradle.api.internal.FeaturePreviews

/*
 * Copyright 2010 the original author or authors.
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
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
    }
}

plugins {
    id("com.gradle.enterprise").version("3.4")
    id("com.gradle.enterprise.gradle-enterprise-conventions-plugin").version("0.7.1")
}

apply(from = "gradle/shared-with-buildSrc/mirrors.settings.gradle.kts")

// If you include a new subproject here, you will need to execute the
// ./gradlew generateSubprojectsInfo
// task to update metadata about the build for CI

include("distributions-dependencies") // platform for dependency versions
include("core-platform")              // platform for Gradle distribution core

// Gradle Distributions - for testing and for publishing a full distribution
include("distributions-core")
include("distributions-basics")
include("distributions-publishing")
include("distributions-jvm")
include("distributions-native")
include("distributions-full")

// Gradle implementation projects
include("instant-execution")
include("api-metadata")
include("base-services")
include("base-services-groovy")
include("logging")
include("process-services")
include("jvm-services")
include("core")
include("dependency-management")
include("wrapper")
include("cli")
include("launcher")
include("bootstrap")
include("messaging")
include("resources")
include("resources-http")
include("resources-gcs")
include("resources-s3")
include("resources-sftp")
include("plugins")
include("scala")
include("ide")
include("ide-native")
include("ide-play")
include("maven")
include("code-quality")
include("antlr")
include("tooling-api")
include("build-events")
include("tooling-api-builders")
include("signing")
include("ear")
include("native")
include("javascript")
include("reporting")
include("diagnostics")
include("publish")
include("ivy")
include("jacoco")
include("buildInit")
include("buildOption")
include("platformBase")
include("platformNative")
include("platformJvm")
include("languageJvm")
include("languageJava")
include("java-compiler-plugin")
include("languageGroovy")
include("languageNative")
include("toolingNative")
include("languageScala")
include("pluginUse")
include("pluginDevelopment")
include("modelCore")
include("modelGroovy")
include("build-cache-http")
include("testingBase")
include("testingNative")
include("testingJvm")
include("testing-junit-platform")
include("platformPlay")
include("testKit")
include("installationBeacon")
include("compositeBuilds")
include("workers")
include("persistentCache")
include("buildCacheBase")
include("buildCache")
include("coreApi")
include("versionControl")
include("fileCollections")
include("files")
include("hashing")
include("snapshots")
include("fileWatching")
include("buildCachePackaging")
include("execution")
include("buildProfile")
include("kotlin-compiler-embeddable")
include("kotlinDsl")
include("kotlin-dsl-provider-plugins")
include("kotlin-dsl-tooling-models")
include("kotlin-dsl-tooling-builders")
include("workerProcesses")
include("baseAnnotations")
include("security")
include("normalizationJava")
include("enterprise")
include("buildOperations")

// Plugin portal projects
include("kotlin-dsl-plugins")

// Internal utility and verification projects
include("docs")
include("samples")
include("architectureTest")
include("internalTesting")
include("internalIntegTesting")
include("internalPerformanceTesting")
include("internalAndroidPerformanceTesting")
include("internalBuildReports")
include("integTest")
include("kotlin-dsl-integ-tests")
include("distributionsIntegTests")
include("soak")
include("smokeTest")
include("performance")
include("buildScanPerformance")
include("instantExecutionReport")

val upperCaseLetters = "\\p{Upper}".toRegex()

fun String.toKebabCase() =
    replace(upperCaseLetters) { "-${it.value.toLowerCase()}" }

rootProject.name = "gradle"

// List of sub-projects that have a Groovy DSL build script.
// The intent is for this list to diminish until it disappears.
val groovyBuildScriptProjects = hashSetOf(
    "docs"
)

fun buildFileNameFor(projectDirName: String) =
    "$projectDirName${buildFileExtensionFor(projectDirName)}"

fun buildFileExtensionFor(projectDirName: String) =
    if (projectDirName in groovyBuildScriptProjects) ".gradle" else ".gradle.kts"

for (project in rootProject.children) {
    val projectDirName = project.name.toKebabCase()
    project.projectDir = file("subprojects/$projectDirName")
    project.buildFileName = buildFileNameFor(projectDirName)
    require(project.projectDir.isDirectory) {
        "Project directory ${project.projectDir} for project ${project.name} does not exist."
    }
    require(project.buildFile.isFile) {
        "Build file ${project.buildFile} for project ${project.name} does not exist."
    }
}

FeaturePreviews.Feature.values().forEach { feature ->
    if (feature.isActive) {
        enableFeaturePreview(feature.name)
    }
}
