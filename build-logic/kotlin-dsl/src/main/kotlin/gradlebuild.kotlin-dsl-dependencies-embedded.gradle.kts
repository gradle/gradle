/*
 * Copyright 2020 the original author or authors.
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

import gradlebuild.basics.accessors.kotlin
import gradlebuild.basics.util.ReproduciblePropertiesWriter
import gradlebuild.kotlindsl.generator.tasks.GenerateKotlinDependencyExtensions

plugins {
    id("gradlebuild.dependency-modules")
    kotlin("jvm")
}

// --- Enable automatic generation of API extensions -------------------
val apiExtensionsOutputDir = layout.buildDirectory.dir("generated-sources/kotlin")

val publishedKotlinDslPluginVersion = "4.1.3" // TODO:kotlin-dsl

tasks {
    val generateKotlinDependencyExtensions by registering(GenerateKotlinDependencyExtensions::class) {
        outputDir = apiExtensionsOutputDir
        embeddedKotlinVersion = libs.kotlinVersion
        kotlinDslPluginsVersion = publishedKotlinDslPluginVersion
    }

    val apiExtensionsFileCollection = files(apiExtensionsOutputDir).builtBy(generateKotlinDependencyExtensions)

    sourceSets.main {
        kotlin.srcDir(apiExtensionsFileCollection)
    }

    // Workaround for https://github.com/gradle/gradle/issues/24131
    // See gradlebuild.unittest-and-compile.gradle.kts
    configurations["transitiveSourcesElements"].outgoing.artifact(apiExtensionsOutputDir) {
        builtBy(generateKotlinDependencyExtensions)
    }

    processResources {
        // Add generated sources to the main jar because `src` or any other Gradle distribution does not include them.
        // A more general solution is probably required: https://github.com/gradle/gradle/issues/21114
        from(apiExtensionsFileCollection)
    }

// -- Version manifest properties --------------------------------------
    val writeVersionsManifest by registering(WriteProperties::class) {
        destinationFile = layout.buildDirectory.file("versionsManifest/gradle-kotlin-dsl-versions.properties")
        property("kotlin", libs.kotlinVersion)
    }

    processResources {
        from(writeVersionsManifest)
    }
}

// -- Embedded Kotlin dependencies -------------------------------------

val embeddedKotlinBaseDependencies by configurations.creating

dependencies {
    embeddedKotlinBaseDependencies(libs.futureKotlin("stdlib"))
    embeddedKotlinBaseDependencies(libs.futureKotlin("reflect"))
}

val writeEmbeddedKotlinDependencies by tasks.registering {
    val outputFile = layout.buildDirectory.file("embeddedKotlinDependencies/gradle-kotlin-dsl-embedded-kotlin.properties")
    outputs.file(outputFile)
    val values = embeddedKotlinBaseDependencies
    inputs.files(values)
    val skippedModules = setOf(project.name, "distributions-dependencies")
    // https://github.com/gradle/configuration-cache/issues/183
    val modules = provider {
        embeddedKotlinBaseDependencies.incoming.resolutionResult.allComponents
            .asSequence()
            .mapNotNull { it.moduleVersion }
            .filter { it.name !in skippedModules }
            .associate { "${it.group}:${it.name}" to it.version }
    }

    doLast {
        ReproduciblePropertiesWriter.store(
            modules.get(),
            outputFile.get().asFile.apply { parentFile.mkdirs() },
            null
        )
    }
}

tasks.processResources {
    from(writeEmbeddedKotlinDependencies)
}
