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

apply {
    from("gradle/remoteHttpCacheSettings.gradle")
}

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
include("languageScala")
include("pluginUse")
include("pluginDevelopment")
include("modelCore")
include("modelGroovy")
include("buildCacheHttp")
include("testingBase")
include("testingNative")
include("testingJvm")
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

val upperCaseLetters = "\\p{Upper}".toRegex()

fun String.toKebabCase() =
    replace(upperCaseLetters) { "-${it.value.toLowerCase()}" }

rootProject.name = "gradle"

// List of subprojects that have a Groovy DSL build script.
// The intent is for this list to diminish until it disappears.
val groovyBuildScriptProjects = listOf(
    "distributions",
    "base-services",
    "base-services-groovy",
    "logging",
    "process-services",
    "jvm-services",
    "core",
    "dependency-management",
    "wrapper",
    "cli",
    "launcher",
    "messaging",
    "resources",
    "resources-http",
    "resources-gcs",
    "resources-s3",
    "resources-sftp",
    "plugins",
    "scala",
    "ide",
    "ide-native",
    "ide-play",
    "osgi",
    "maven",
    "code-quality",
    "antlr",
    "tooling-api",
    "tooling-api-builders",
    "docs",
    "integ-test",
    "signing",
    "ear",
    "native",
    "internal-testing",
    "internal-integ-testing",
    "internal-performance-testing",
    "internal-android-performance-testing",
    "performance",
    "build-scan-performance",
    "javascript",
    "build-comparison",
    "reporting",
    "diagnostics",
    "publish",
    "ivy",
    "jacoco",
    "build-init",
    "build-option",
    "platform-base",
    "platform-native",
    "platform-jvm",
    "language-jvm",
    "language-java",
    "language-groovy",
    "language-native",
    "language-scala",
    "plugin-use",
    "model-core",
    "model-groovy",
    "build-cache-http",
    "testing-base",
    "testing-native",
    "testing-jvm",
    "platform-play",
    "test-kit",
    "soak",
    "smoke-test",
    "composite-builds",
    "workers",
    "persistent-cache",
    "build-cache",
    "core-api",
    "version-control")

fun buildFileNameFor(projectDirName: String) =
    "$projectDirName${buildFileExtensionFor(projectDirName)}"

fun buildFileExtensionFor(projectDirName: String) =
    if (projectDirName in groovyBuildScriptProjects) ".gradle" else ".gradle.kts"

for (project in rootProject.children) {
    val projectDirName = project.name.toKebabCase()
    project.projectDir = file("subprojects/$projectDirName")
    project.buildFileName = buildFileNameFor(projectDirName)
    assert(project.projectDir.isDirectory)
    assert(project.buildFile.isFile)
}
