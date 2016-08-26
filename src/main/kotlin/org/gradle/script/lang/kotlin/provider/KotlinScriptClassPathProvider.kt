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

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.SelfResolvingDependency

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory
import org.gradle.api.internal.cache.GeneratedGradleJarCache

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.script.lang.kotlin.codegen.isApiClassEntry
import org.gradle.script.lang.kotlin.codegen.conflictsWithExtension
import org.gradle.script.lang.kotlin.support.asm.removeMethodsMatching

import org.gradle.util.GFileUtils.moveFile

import java.io.File

class KotlinScriptClassPathProvider(
    val classPathRegistry: ClassPathRegistry,
    val dependencyFactory: DependencyFactory,
    val jarCache: GeneratedGradleJarCache) {

    /**
     * Generated Gradle API jar plus supporting libraries such as groovy-all.jar.
     */
    val gradleApi: ClassPath by lazy {
        DefaultClassPath.of(gradleApiFiles())
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
        gradleApiDependency().resolve().map {
            when {
//              it.name.startsWith("gradle-api-") -> kotlinGradleApiFrom(it)
                else -> it
            }
        }

    private fun kotlinGradleApiFrom(gradleApiJar: File): File =
        jarCache["script-kotlin-api", { outputJar ->
            val tempFile = tempFileFor(outputJar)
            generateKotlinGradleApiAt(tempFile, gradleApiJar)
            moveFile(tempFile, outputJar)
        }]

    private fun tempFileFor(outputJar: File): File =
        createTempFile(outputJar.name, ".tmp").apply {
            deleteOnExit()
        }

    private fun generateKotlinGradleApiAt(outputFile: File, gradleApiJar: File) {
        gradleApiJar.inputStream().use { input ->
            outputFile.outputStream().use { output ->
                removeMethodsMatching(
                    ::conflictsWithExtension,
                    input.buffered(),
                    output.buffered(),
                    shouldTransformEntry = { isApiClassEntry() })
            }
        }
    }

    private fun gradleApiDependency() =
        (dependencyFactory.gradleApi() as SelfResolvingDependency)
}

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

