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

apply(from = "gradle/shared-with-buildSrc/build-cache-configuration.settings.gradle.kts")
apply(from = "gradle/shared-with-buildSrc/mirrors.settings.gradle.kts")

include("instantExecution")
include("apiMetadata")
include("distributionsDependencies")
include("distributions")
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
include("launcherBootstrap")
include("launcherStartup")
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
include("osgi")
include("maven")
include("announce")
include("codeQuality")
include("antlr")
include("toolingApi")
include("toolingApiBuilders")
include("docs")
include("integTest")
include("signing")
include("ear")
include("native")
include("internalTesting")
include("internalIntegTesting")
include("internalPerformanceTesting")
include("internalAndroidPerformanceTesting")
include("performance")
include("buildScanPerformance")
include("javascript")
include("buildComparison")
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
include("soak")
include("smokeTest")
include("compositeBuilds")
include("workers")
include("runtimeApiInfo")
include("persistentCache")
include("buildCache")
include("coreApi")
include("versionControl")
include("files")
include("snapshots")
include("architectureTest")
include("buildCachePackaging")
include("execution")
include("buildProfile")
include("kotlinCompilerEmbeddable")
include("kotlinDsl")
include("kotlinDslProviderPlugins")
include("kotlinDslPlugins")
include("kotlinDslToolingModels")
include("kotlinDslToolingBuilders")
include("kotlinDslTestFixtures")
include("kotlinDslIntegTests")
include("workerProcesses")

val upperCaseLetters = "\\p{Upper}".toRegex()

fun String.toKebabCase() =
    replace(upperCaseLetters) { "-${it.value.toLowerCase()}" }

rootProject.name = "gradle"

// List of sub-projects that have a Groovy DSL build script.
// The intent is for this list to diminish until it disappears.
val groovyBuildScriptProjects = hashSetOf(
    "distributions",
    "docs",
    "performance"
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

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
    }
}

val ignoredFeatures = setOf(
    // we don't want to publish Gradle metadata to public repositories until the format is stable.
    FeaturePreviews.Feature.GRADLE_METADATA
)

FeaturePreviews.Feature.values().forEach { feature ->
    if (feature.isActive && feature !in ignoredFeatures) {
        enableFeaturePreview(feature.name)
    }
}

