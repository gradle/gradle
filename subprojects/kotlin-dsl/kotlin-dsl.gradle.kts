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
import org.gradle.build.ReproduciblePropertiesWriter

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

    implementation(project(":baseServices"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":persistentCache"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":baseServicesGroovy")) // for 'Specs'
    implementation(project(":fileCollections"))
    implementation(project(":files"))
    implementation(project(":resources"))
    implementation(project(":buildCache"))
    implementation(project(":toolingApi"))

    implementation(library("groovy"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("inject"))

    implementation(futureKotlin("scripting-common")) {
        isTransitive = false
    }
    implementation(futureKotlin("scripting-jvm")) {
        isTransitive = false
    }
    implementation(futureKotlin("scripting-jvm-host-embeddable")) {
        isTransitive = false
    }
    implementation(futureKotlin("scripting-compiler-embeddable")) {
        isTransitive = false
    }
    implementation(futureKotlin("scripting-compiler-impl-embeddable")) {
        isTransitive = false
    }
    implementation(futureKotlin("sam-with-receiver-compiler-plugin")) {
        isTransitive = false
    }
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.1.0") {
        isTransitive = false
    }

    testImplementation(project(":kotlinDslTestFixtures"))
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

    testImplementation(testLibrary("archunit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.0.1")
    testImplementation("org.awaitility:awaitility-kotlin:3.1.6")

    testRuntimeOnly(project(":runtimeApiInfo"))

    integTestImplementation(project(":languageGroovy"))
    integTestImplementation(project(":internalTesting"))
    integTestImplementation(testLibrary("mockito_kotlin"))

    integTestRuntimeOnly(project(":runtimeApiInfo"))
    integTestRuntimeOnly(project(":apiMetadata"))
    integTestRuntimeOnly(project(":pluginDevelopment"))
    integTestRuntimeOnly(project(":toolingApiBuilders"))
    integTestRuntimeOnly(project(":kotlinDslProviderPlugins"))
    integTestRuntimeOnly(project(":testingJunitPlatform"))
}

// --- Enable automatic generation of API extensions -------------------
val apiExtensionsOutputDir = layout.buildDirectory.dir("generated-sources/kotlin")

val publishedKotlinDslPluginVersion = "1.3.3" // TODO:kotlin-dsl

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


// -- Embedded Kotlin dependencies -------------------------------------

val embeddedKotlinBaseDependencies by configurations.creating

dependencies {
    embeddedKotlinBaseDependencies(futureKotlin("stdlib-jdk8"))
    embeddedKotlinBaseDependencies(futureKotlin("reflect"))
}

val writeEmbeddedKotlinDependencies by tasks.registering {
    val outputFile = layout.buildDirectory.file("embeddedKotlinDependencies/gradle-kotlin-dsl-embedded-kotlin.properties")
    outputs.file(outputFile)
    val values = embeddedKotlinBaseDependencies
    inputs.files(values)
    val skippedModules = setOf(project.name, "distributionsDependencies", "kotlinCompilerEmbeddable")

    doLast {
        val modules = values.incoming.resolutionResult.allComponents
            .asSequence()
            .mapNotNull { it.moduleVersion }
            .filter { it.name !in skippedModules }
            .associate { "${it.group}:${it.name}" to it.version }

        ReproduciblePropertiesWriter.store(
            modules,
            outputFile.get().asFile.apply { parentFile.mkdirs() },
            null
        )
    }
}

tasks.processResources {
    from(writeEmbeddedKotlinDependencies)
}
