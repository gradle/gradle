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
package org.gradle.modules

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

import org.gradle.kotlin.dsl.*

import java.io.File


/**
 * Patch JARs by removing/adding specific entries from/into the specified jar file.
 *
 * This is used to patch the kotlin-compiler-embeddable jar, which has:
 * <ul>
 *     <li>a bogus `CharsetProvider` entry,</li>
 *     <li>an old version of `native-platform`,</li>
 *     <li>an old version of `jansi`.</li>
 * </ul>
 */
open class JarPatcher(val project: Project, val temporaryDir: File, val runtime: Configuration, val jarFile: String) {
    var excludedEntries = mutableListOf<String>()
    var includedJars = mutableMapOf<String, List<String>>()

    fun exclude(exclude: String): JarPatcher {
        excludedEntries.add(exclude)
        return this
    }

    fun includeJar(includedJar: String, vararg includes: String): JarPatcher {
        includedJars.put(includedJar, includes.asList())
        return this
    }

    fun writePatchedFilesTo(outputDir: File) {
        val originalFile = runtime.files.single { it.name.startsWith(jarFile) }
        val unpackDir = unpack(originalFile)

        val patchedFile = File(outputDir, originalFile.name)
        pack(unpackDir, patchedFile)
    }

    private fun unpack(file: File): File {
        val unpackDir = File(temporaryDir, "excluding-" + file.name)
        project.sync({
            this.into(unpackDir)
            this.from(project.zipTree(file))
            this.exclude(excludedEntries)
        })
        return unpackDir
    }

    private fun pack(baseDir: File, destFile: File) {
        val resolvedIncludes = mutableMapOf<File, List<String>>()
        includedJars.forEach { jarPrefix, includes ->
            runtime.files.filter { it.name.startsWith(jarPrefix) }.forEach { includedJar ->
                resolvedIncludes.put(includedJar, includes)
            }
        }
        project.copy({
            this.into(baseDir)
            resolvedIncludes.forEach { sourceJar, includes ->
                this.from(project.zipTree(sourceJar), {
                    includes.forEach { include ->
                        this.include(include)
                    }
                })
            }
        })
        project.ant.withGroovyBuilder {
            "zip"("basedir" to baseDir, "destfile" to destFile)
        }
    }
}

