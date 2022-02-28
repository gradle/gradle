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

val publishedKotlinDslPluginVersion = "2.2.0" // TODO:kotlin-dsl

tasks {
    val generateKotlinDependencyExtensions by registering(GenerateKotlinDependencyExtensions::class) {
        outputDir.set(apiExtensionsOutputDir)
        embeddedKotlinVersion.set(libs.kotlinVersion)
        kotlinDslPluginsVersion.set(publishedKotlinDslPluginVersion)
    }

    sourceSets.main {
        kotlin.srcDir(files(apiExtensionsOutputDir).builtBy(generateKotlinDependencyExtensions))
    }

// -- Version manifest properties --------------------------------------
    val writeVersionsManifest by registering(WriteProperties::class) {
        outputFile = buildDir.resolve("versionsManifest/gradle-kotlin-dsl-versions.properties")
        property("kotlin", libs.kotlinVersion)
    }

    processResources {
        from(writeVersionsManifest)
    }
}

// -- Embedded Kotlin dependencies -------------------------------------

val embeddedKotlinBaseDependencies by configurations.creating

dependencies {
    embeddedKotlinBaseDependencies(libs.futureKotlin("stdlib-jdk8"))
    embeddedKotlinBaseDependencies(libs.futureKotlin("reflect"))
}

val writeEmbeddedKotlinDependencies by tasks.registering {
    val outputFile = layout.buildDirectory.file("embeddedKotlinDependencies/gradle-kotlin-dsl-embedded-kotlin.properties")
    outputs.file(outputFile)
    val values = embeddedKotlinBaseDependencies
    inputs.files(values)
    val skippedModules = setOf(project.name, "distributions-dependencies", "kotlin-compiler-embeddable")
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
