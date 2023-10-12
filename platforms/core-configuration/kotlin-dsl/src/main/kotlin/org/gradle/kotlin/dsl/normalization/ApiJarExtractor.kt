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

import org.gradle.cache.FileLockManager
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.filelock.LockOptionsBuilder.mode
import org.gradle.cache.scopes.BuildTreeScopedCacheBuilderFactory
import org.gradle.internal.Factory
import org.gradle.internal.normalization.java.ApiClassExtractor
import org.gradle.kotlin.dsl.support.walkReproducibly
import org.objectweb.asm.ClassReader
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import javax.inject.Inject
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.random.Random


class ApiJarExtractor @Inject constructor(
    cacheFactory: BuildTreeScopedCacheBuilderFactory
) {

    private
    var cache: PersistentCache =
        cacheFactory.createCacheBuilder("kotlin-dsl-script-abi-jars")
            .withDisplayName("Kotlin DSL Script Compilation ABI JARS")
            .withLockOptions(mode(FileLockManager.LockMode.OnDemand))
            .open()

    fun extractAbiJar(jarOrClassesDir: File): File {
        val apiClassExtractor = ApiClassExtractor(emptySet())
        return cache.useCache(Factory {
            when {
                jarOrClassesDir.isDirectory -> extractAbiJarFromClassesDir(apiClassExtractor, jarOrClassesDir)
                else -> extractAbiJarFromJar(apiClassExtractor, jarOrClassesDir)
            }
        })
    }

    private
    fun extractAbiJarFromJar(apiClassExtractor: ApiClassExtractor, jarFile: File): File {
        val apiJar = cache.baseDir.resolve("${jarFile.nameWithoutExtension}-api.jar") // TODO
        if (apiJar.isFile) return apiJar
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
        return apiJar
    }

    private
    fun extractAbiJarFromClassesDir(apiClassExtractor: ApiClassExtractor, classesDir: File): File {
        val apiDir = cache.baseDir.resolve("${classesDir.name}-${Random.nextInt()}-api").toPath() // TODO
        if (apiDir.toFile().exists()) return apiDir.toFile()
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
