/*
 * Copyright 2018 the original author or authors.
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

import build.futureKotlin
import build.kotlin
import codegen.GenerateKotlinDslPluginsExtensions
import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import plugins.bundledGradlePlugin

plugins {
    `kotlin-dsl-plugin-bundle`
}

description = "Kotlin DSL Gradle Plugins deployed to the Plugin Portal"

group = "org.gradle.kotlin"
version = "1.2.9"

base.archivesBaseName = "plugins"

gradlebuildJava {
    moduleType = ModuleType.PORTAL_PLUGINS
}

val generatedSourcesDir = layout.buildDirectory.dir("generated-sources/kotlin")

val generateSources by tasks.registering(GenerateKotlinDslPluginsExtensions::class) {
    outputDir.set(generatedSourcesDir)
    kotlinDslPluginsVersion.set(project.version)
}

sourceSets.main {
    kotlin.srcDir(files(generatedSourcesDir).builtBy(generateSources))
}

dependencies {
    compileOnly(project(":baseServices"))
    compileOnly(project(":logging"))
    compileOnly(project(":coreApi"))
    compileOnly(project(":modelCore"))
    compileOnly(project(":core"))
    compileOnly(project(":languageJvm"))
    compileOnly(project(":languageJava"))
    compileOnly(project(":plugins"))
    compileOnly(project(":pluginDevelopment"))
    compileOnly(project(":kotlinDsl"))

    compileOnly(library("slf4j_api"))
    compileOnly(library("inject"))

    implementation(futureKotlin("stdlib-jdk8"))
    implementation(futureKotlin("gradle-plugin"))
    implementation(futureKotlin("sam-with-receiver"))

    integTestImplementation(project(":baseServices"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":coreApi"))
    integTestImplementation(project(":modelCore"))
    integTestImplementation(project(":core"))
    integTestImplementation(project(":plugins"))
    integTestImplementation(project(":platformJvm"))
    integTestImplementation(project(":kotlinDsl"))
    integTestImplementation(project(":internalTesting"))
    integTestImplementation(project(":internalIntegTesting"))
    integTestImplementation(project(":kotlinDslTestFixtures"))
    integTestImplementation(library("slf4j_api"))
    integTestImplementation(testLibrary("mockito_kotlin"))
    integTestRuntimeOnly(project(":runtimeApiInfo"))
    integTestRuntimeOnly(project(":apiMetadata"))
    integTestRuntimeOnly(project(":pluginDevelopment"))
    integTestRuntimeOnly(project(":toolingApiBuilders"))
}


// plugins ------------------------------------------------------------

bundledGradlePlugin(
    name = "embeddedKotlin",
    shortDescription = "Embedded Kotlin Gradle Plugin",
    pluginId = "org.gradle.kotlin.embedded-kotlin",
    pluginClass = "org.gradle.kotlin.dsl.plugins.embedded.EmbeddedKotlinPlugin")

bundledGradlePlugin(
    name = "kotlinDsl",
    shortDescription = "Gradle Kotlin DSL Plugin",
    pluginId = "org.gradle.kotlin.kotlin-dsl",
    pluginClass = "org.gradle.kotlin.dsl.plugins.dsl.KotlinDslPlugin")

bundledGradlePlugin(
    name = "kotlinDslBase",
    shortDescription = "Gradle Kotlin DSL Base Plugin",
    pluginId = "org.gradle.kotlin.kotlin-dsl.base",
    pluginClass = "org.gradle.kotlin.dsl.plugins.base.KotlinDslBasePlugin")

bundledGradlePlugin(
    name = "kotlinDslCompilerSettings",
    shortDescription = "Gradle Kotlin DSL Compiler Settings",
    pluginId = "org.gradle.kotlin.kotlin-dsl.compiler-settings",
    pluginClass = "org.gradle.kotlin.dsl.plugins.dsl.KotlinDslCompilerPlugins")

bundledGradlePlugin(
    name = "kotlinDslPrecompiledScriptPlugins",
    shortDescription = "Gradle Kotlin DSL Precompiled Script Plugins",
    pluginId = "org.gradle.kotlin.kotlin-dsl.precompiled-script-plugins",
    pluginClass = "org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugins")


// testing ------------------------------------------------------------

val integTestTasks: DomainObjectCollection<IntegrationTest> by extra
integTestTasks.configureEach {
    dependsOn("publishPluginsToTestRepository")
}

// TODO:kotlin-dsl investigate
// See https://builds.gradle.org/viewLog.html?buildId=19024848&problemId=23230
tasks.noDaemonIntegTest {
    enabled = false
}

// TODO:kotlin-dsl
tasks.verifyTestFilesCleanup {
    enabled = false
}
