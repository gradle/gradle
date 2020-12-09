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

import gradlebuild.basics.BuildEnvironment
import gradlebuild.kotlindsl.compiler.tasks.CheckKotlinCompilerEmbeddableDependencies
import gradlebuild.kotlindsl.compiler.tasks.PatchKotlinCompilerEmbeddable

plugins {
    id("gradlebuild.dependency-modules")
    id("gradlebuild.unittest-and-compile")
}

val kotlinCompilerEmbeddable by configurations.creating

dependencies {
    findProject(":distributions-dependencies")?.let { kotlinCompilerEmbeddable(it) }
    kotlinCompilerEmbeddable(libs.futureKotlin("compiler-embeddable"))
}

tasks {
    val checkKotlinCompilerEmbeddableDependencies by registering(CheckKotlinCompilerEmbeddableDependencies::class) {
        current.from(configurations.runtimeClasspath)
        expected.from(kotlinCompilerEmbeddable)
    }

    val patchKotlinCompilerEmbeddable by registering(PatchKotlinCompilerEmbeddable::class) {
        dependsOn(checkKotlinCompilerEmbeddableDependencies)
        excludes.set(
            listOf(
                "META-INF/services/javax.annotation.processing.Processor",
                "META-INF/native/**/*jansi.*"
            )
        )
        originalFiles.from(kotlinCompilerEmbeddable)
        dependencies.from(
            configurations.detachedConfiguration(
                project.dependencies.project(":distributions-dependencies"),
                project.dependencies.create(libs.jansi)
            )
        )
        dependenciesIncludes.set(
            mapOf(
                "jansi-" to listOf("META-INF/native/**", "org/fusesource/jansi/internal/CLibrary*.class")
            )
        )
        additionalRootFiles.from(classpathManifest)

        outputFile.set(jar.get().archiveFile)

        outputs.doNotCacheIfSlowInternetConnection()
    }

    jar.configure {
        dependsOn(patchKotlinCompilerEmbeddable)
        actions.clear()
    }
}

fun TaskOutputs.doNotCacheIfSlowInternetConnection() {
    doNotCacheIf("Slow internet connection") {
        BuildEnvironment.isSlowInternetConnection
    }
}
