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
internal
class JarPatcher(
    private val project: Project,
    private val temporaryDir: File,
    private val runtime: Configuration,
    private val jarFile: String
) {

    private
    var excludedEntries = mutableListOf<String>()

    private
    var includedJars = mutableMapOf<String, List<String>>()

    fun exclude(exclude: String) = also {
        excludedEntries.add(exclude)
    }

    fun includeJar(includedJar: String, vararg includes: String) = also {
        includedJars[includedJar] = includes.asList()
    }

    fun writePatchedFilesTo(outputDir: File) {
        val originalFile = runtime.files.single { it.name.startsWith(jarFile) }
        val unpackDir = unpack(originalFile)
        pack(unpackDir, outputDir.resolve(originalFile.name))
    }

    private
    fun unpack(file: File) =
        unpackDirFor(file).also { unpackDir ->
            project.run {
                sync {
                    into(unpackDir)
                    from(zipTree(file))
                    exclude(excludedEntries)
                }
            }
        }

    private
    fun unpackDirFor(file: File) =
        temporaryDir.resolve("excluding-${file.name}")

    private
    fun pack(baseDir: File, destFile: File): Unit = project.run {
        val resolvedIncludes = mutableMapOf<File, List<String>>()
        includedJars.forEach { jarPrefix, includes ->
            runtime.files.filter { it.name.startsWith(jarPrefix) }.forEach { includedJar ->
                resolvedIncludes[includedJar] = includes
            }
        }
        copy {
            into(baseDir)
            resolvedIncludes.forEach { sourceJar, includes ->
                from(zipTree(sourceJar)) {
                    include(includes)
                }
            }
        }
        ant.withGroovyBuilder {
            "zip"("basedir" to baseDir, "destfile" to destFile)
        }
    }
}
