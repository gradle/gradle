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
    id("com.gradle.enterprise").version("3.3.4")
}

apply(from = "gradle/build-cache-configuration.settings.gradle.kts")
apply(from = "gradle/shared-with-buildSrc/mirrors.settings.gradle.kts")

// If you include a new subproject here, you will need to execute the
// ./gradlew generateSubprojectsInfo
// task to update metadata about the build for CI

include("distributionsDependencies") // platform for dependency versions
include("corePlatform")              // platform for Gradle distribution core

// Gradle Distributions - for testing and for publishing a full distribution
include("distributionsCore")
include("distributionsBasics")
include("distributionsPublishing")
include("distributionsJvm")
include("distributionsNative")
include("distributionsFull")

// Gradle implementation projects
include("instantExecution")
include("apiMetadata")
include("baseServices")
include("baseServicesGroovy")
include("logging")
include("processServices")
include("jvmServices")
include("core")
include("dependencyManagement")
include("wrapper")
include("cli")
include("launcher")
include("bootstrap")
include("messaging")
include("resources")
include("resourcesHttp")
include("resourcesGcs")
include("resourcesS3")
include("resourcesSftp")
include("plugins")
include("scala")
include("ide")
include("ideNative")
include("idePlay")
include("maven")
include("codeQuality")
include("antlr")
include("toolingApi")
include("buildEvents")
include("toolingApiBuilders")
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
include("javaCompilerPlugin")
include("languageGroovy")
include("languageNative")
include("toolingNative")
include("languageScala")
include("pluginUse")
include("pluginDevelopment")
include("modelCore")
include("modelGroovy")
include("buildCacheHttp")
include("testingBase")
include("testingNative")
include("testingJvm")
include("testingJunitPlatform")
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
include("kotlinCompilerEmbeddable")
include("kotlinDsl")
include("kotlinDslProviderPlugins")
include("kotlinDslToolingModels")
include("kotlinDslToolingBuilders")
include("workerProcesses")
include("baseAnnotations")
include("security")
include("normalizationJava")
include("enterprise")

// Plugin portal projects
include("kotlinDslPlugins")

// Internal utility and verification projects
include("docs")
include("samples")
include("architectureTest")
include("internalTesting")
include("internalIntegTesting")
include("internalPerformanceTesting")
include("internalAndroidPerformanceTesting")
include("internalBuildReports")
include("kotlinDslTestFixtures")
include("integTest")
include("kotlinDslIntegTests")
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
    if (feature.isActive) { enableFeaturePreview(feature.name) }
}

