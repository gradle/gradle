/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.script.lang.kotlin.provider

import org.gradle.script.lang.kotlin.codegen.generateActionExtensionsJar
import org.gradle.script.lang.kotlin.codegen.generateKotlinGradleApiJar

import org.gradle.script.lang.kotlin.support.ProgressMonitor

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.SelfResolvingDependency

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.util.GFileUtils.moveFile

import org.jetbrains.kotlin.utils.addToStdlib.singletonList

import java.io.File

import java.util.zip.ZipFile

typealias JarCache = (String, JarGenerator) -> File

typealias JarGenerator = (File) -> Unit

typealias JarsProvider = () -> Collection<File>

class KotlinScriptClassPathProvider(
    val classPathRegistry: ClassPathRegistry,
    val gradleApiJarsProvider: JarsProvider,
    val jarCache: JarCache,
    val progressMonitorProvider: JarGenerationProgressMonitorProvider) {

    /**
     * Generated Gradle API jar plus supporting libraries such as groovy-all.jar.
     */
    val gradleApi: ClassPath by lazy {
        DefaultClassPath.of(gradleApiFiles())
    }

    /**
     * Generated extensions to the Gradle API.
     */
    val gradleApiExtensions: ClassPath by lazy {
        gradleApi.filter { it.name.startsWith("gradle-script-kotlin-extensions-") }
    }

    /**
     * gradle-script-kotlin.jar plus kotlin libraries.
     */
    val gradleScriptKotlinJars: ClassPath by lazy {
        DefaultClassPath.of(gradleScriptKotlinJarsFrom(classPathRegistry))
    }

    /**
     * Returns the generated Gradle API jar plus supporting files such as groovy-all.jar.
     */
    private fun gradleApiFiles() =
        gradleApiJarsProvider().flatMap {
            when {
                it.name.startsWith("gradle-api-") ->
                    listOf(
                        kotlinGradleApiFrom(it),
                        kotlinGradleApiExtensionsFrom(it))
                else ->
                    it.singletonList()
            }
        }

    private fun kotlinGradleApiFrom(gradleApiJar: File): File =
        produceFrom(gradleApiJar, "script-kotlin-api") { outputFile, onProgress ->
            generateKotlinGradleApiJar(outputFile, gradleApiJar, onProgress)
        }

    private fun kotlinGradleApiExtensionsFrom(gradleApiJar: File): File =
        produceFrom(gradleApiJar, "script-kotlin-extensions") { outputFile, onProgress ->
            generateActionExtensionsJar(outputFile, gradleApiJar, onProgress)
        }

    private fun produceFrom(gradleApiJar: File, id: String, generate: JarGeneratorWithProgress): File =
        jarCache(id) { outputFile ->
            progressMonitorFor(outputFile, numberOfEntriesIn(gradleApiJar)).use { progressMonitor ->
                generateAtomically(outputFile, { generate(it, progressMonitor::onProgress) })
            }
        }

    typealias JarGeneratorWithProgress = (File, () -> Unit) -> Unit

    private fun generateAtomically(outputFile: File, generate: JarGenerator) {
        val tempFile = tempFileFor(outputFile)
        generate(tempFile)
        moveFile(tempFile, outputFile)
    }

    private fun progressMonitorFor(outputFile: File, totalWork: Int): ProgressMonitor =
        progressMonitorProvider.progressMonitorFor(outputFile, totalWork)

    private fun tempFileFor(outputFile: File): File =
        createTempFile(outputFile.nameWithoutExtension, outputFile.extension).apply {
            deleteOnExit()
        }

    private fun numberOfEntriesIn(gradleApiJar: File) =
        ZipFile(gradleApiJar).use { it.size() }
}

fun gradleApiJarsProviderFor(dependencyFactory: DependencyFactory): JarsProvider =
    { (dependencyFactory.gradleApi() as SelfResolvingDependency).resolve() }

fun gradleScriptKotlinJarsFrom(classPathRegistry: ClassPathRegistry): List<File> =
    classPathRegistry.gradleJars().filter {
        it.name.let { isKotlinJar(it) || it.startsWith("gradle-script-kotlin-") }
    }

fun ClassPathRegistry.gradleJars(): Collection<File> =
    getClassPath(gradleApiNotation.name).asFiles

fun DependencyFactory.gradleApi(): Dependency =
    createDependency(gradleApiNotation)

private val gradleApiNotation = DependencyFactory.ClassPathNotation.GRADLE_API

// TODO: make the predicate more precise
fun isKotlinJar(name: String): Boolean =
    name.startsWith("kotlin-stdlib-")
        || name.startsWith("kotlin-reflect-")
        || name.startsWith("kotlin-runtime-")
