/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.kotlin.dsl.normalization

import org.gradle.internal.normalization.java.ApiClassExtractor
import org.gradle.kotlin.dsl.support.walkReproducibly
import org.objectweb.asm.ClassReader
import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes


class ApiJarExtractor {

    fun extractAbiJar(jarOrClassesDir: File): File {
        val apiClassExtractor = ApiClassExtractor(emptySet())
        return when {
            jarOrClassesDir.isDirectory -> extractAbiJarFromClassesDir(apiClassExtractor, jarOrClassesDir)
            else -> extractAbiJarFromJar(apiClassExtractor, jarOrClassesDir)
        }
    }

    private
    fun extractAbiJarFromJar(apiClassExtractor: ApiClassExtractor, jarFile: File): File {
        val apiJar = Files.createTempFile(jarFile.nameWithoutExtension, "api.jar")
        JarOutputStream(apiJar.outputStream().buffered()).use { output ->
            JarFile(jarFile).use { jar ->
                for (entry in jar.entries().toList().sortedBy { it.name }.distinctBy { it.name }) {
                    when {
                        entry.isDirectory -> {
                            output.putNextEntry(JarEntry(entry.name))
                        }

                        entry.name.endsWith(".class") -> {
                            apiClassExtractor.extractApiClassFrom(ClassReader(jar.getInputStream(entry).readBytes())).ifPresent { apiBytes ->
                                output.putNextEntry(JarEntry(entry.name))
                                output.write(apiBytes)
                            }
                        }

                        else -> {
                            output.putNextEntry(JarEntry(entry.name))
                            output.write(jar.getInputStream(entry).readBytes())
                        }
                    }
                }
            }
        }
        return apiJar.toFile()
    }

    private
    fun extractAbiJarFromClassesDir(apiClassExtractor: ApiClassExtractor, classesDir: File): File {
        val apiDir = Files.createTempDirectory("${classesDir.name}-api")
        classesDir.walkReproducibly().forEach { file ->
            when {
                file.isDirectory -> {
                    apiDir.resolve(file.relativeTo(classesDir).path).createDirectories()
                }

                file.extension == "classes" -> {
                    apiClassExtractor.extractApiClassFrom(ClassReader(file.readBytes())).ifPresent { apiBytes ->
                        apiDir.resolve(file.relativeTo(classesDir).path).writeBytes(apiBytes)
                    }
                }

                else -> {
                    file.copyTo(apiDir.resolve(file.relativeTo(classesDir).path).toFile())
                }
            }
        }
        return apiDir.toFile()
    }
}
