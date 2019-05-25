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

import org.gradle.gradlebuild.unittestandcompile.ModuleType
import build.futureKotlin
import build.kotlin
import build.kotlinVersion
import codegen.GenerateKotlinDependencyExtensions

plugins {
    `kotlin-dsl-module`
}

description = "Kotlin DSL Provider"

gradlebuildJava {
    moduleType = ModuleType.CORE
}

dependencies {
    api(project(":kotlinDslToolingModels"))
    api(project(":kotlinCompilerEmbeddable"))
    api(futureKotlin("stdlib-jdk8"))

    compileOnly(project(":baseServices"))
    compileOnly(project(":native"))
    compileOnly(project(":logging"))
    compileOnly(project(":processServices"))
    compileOnly(project(":persistentCache"))
    compileOnly(project(":coreApi"))
    compileOnly(project(":modelCore"))
    compileOnly(project(":core"))
    compileOnly(project(":baseServicesGroovy")) // for 'Specs'
    compileOnly(project(":files"))
    compileOnly(project(":resources"))
    compileOnly(project(":buildCache"))
    compileOnly(project(":toolingApi"))

    compileOnly(library("groovy"))
    compileOnly(library("slf4j_api"))
    compileOnly(library("guava"))
    compileOnly(library("inject"))

    implementation(futureKotlin("scripting-compiler-embeddable")) {
        isTransitive = false
    }
    implementation(futureKotlin("sam-with-receiver-compiler-plugin")) {
        isTransitive = false
    }
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.0.5") {
        isTransitive = false
    }

    testImplementation(project(":kotlinDslTestFixtures"))
    testImplementation(project(":baseServices"))
    testImplementation(project(":processServices"))
    testImplementation(project(":coreApi"))
    testImplementation(project(":modelCore"))
    testImplementation(project(":core"))
    testImplementation(project(":baseServicesGroovy"))
    testImplementation(project(":resources"))
    testImplementation(project(":buildCache"))
    testImplementation(project(":buildCacheHttp"))
    testImplementation(project(":buildInit"))
    testImplementation(project(":jacoco"))
    testImplementation(project(":platformNative"))
    testImplementation(project(":plugins"))
    testImplementation(project(":versionControl"))
    testImplementation(library("ant"))
    testImplementation(library("asm"))
    testImplementation(testLibrary("mockito_kotlin"))
    testImplementation(testLibrary("jackson_kotlin"))

    testImplementation("com.tngtech.archunit:archunit:0.8.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.0.1")
    testImplementation("org.awaitility:awaitility-kotlin:3.1.6")

    testRuntimeOnly(project(":runtimeApiInfo"))

    integTestImplementation(project(":baseServices"))
    integTestImplementation(project(":coreApi"))
    integTestImplementation(project(":modelCore"))
    integTestImplementation(project(":core"))
    integTestImplementation(project(":languageGroovy"))
    integTestImplementation(project(":internalTesting"))
    integTestImplementation(testLibrary("mockito_kotlin"))

    integTestRuntimeOnly(project(":runtimeApiInfo"))
    integTestRuntimeOnly(project(":apiMetadata"))
    integTestRuntimeOnly(project(":pluginDevelopment"))
    integTestRuntimeOnly(project(":toolingApiBuilders"))
}

// --- Enable automatic generation of API extensions -------------------
val apiExtensionsOutputDir = layout.buildDirectory.dir("generated-sources/kotlin")

val publishedKotlinDslPluginVersion = "1.2.8" // TODO:kotlin-dsl

tasks {

    // TODO:kotlin-dsl
    verifyTestFilesCleanup {
        enabled = false
    }

    val generateKotlinDependencyExtensions by registering(GenerateKotlinDependencyExtensions::class) {
        outputDir.set(apiExtensionsOutputDir)
        embeddedKotlinVersion.set(kotlinVersion)
        kotlinDslPluginsVersion.set(publishedKotlinDslPluginVersion)
    }

    val generateExtensions by registering {
        dependsOn(generateKotlinDependencyExtensions)
    }

    sourceSets.main {
        kotlin.srcDir(files(apiExtensionsOutputDir).builtBy(generateExtensions))
    }

// -- Version manifest properties --------------------------------------
    val writeVersionsManifest by registering(WriteProperties::class) {
        outputFile = buildDir.resolve("versionsManifest/gradle-kotlin-dsl-versions.properties")
        property("kotlin", kotlinVersion)
    }

    processResources {
        from(writeVersionsManifest)
    }
}
