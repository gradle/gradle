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

package org.gradle.gradlebuild.packaging

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import javax.inject.Inject


/**
 * Produces a shaded jar.
 *
 * @param relocatedClassesConfiguration
 * @param classTreesConfiguration
 * @param entryPointsConfiguration
 * @param manifests
 * @param buildReceipt The build receipt properties file. The file will be included in the shaded jar under {@code /org/gradle/build-receipt.properties}.
 * @param jar The output Jar file.
 */
open class ShadedJar @Inject constructor(
    @get:InputFiles val relocatedClassesConfiguration: FileCollection,
    @get:InputFiles val classTreesConfiguration: FileCollection,
    @get:InputFiles val entryPointsConfiguration: FileCollection,
    @get:InputFiles val manifests: FileCollection,
    @get:Internal val buildReceipt: Provider<RegularFile>,
    @get:Internal val jar: Provider<RegularFile>
) : DefaultTask() {

    @InputFile
    val buildReceiptFile: RegularFileProperty = project.layout.fileProperty()

    @OutputFile
    val jarFile = newOutputFile()

    init {
        buildReceiptFile.set(buildReceipt)
        jarFile.set(jar)
    }

    @TaskAction
    fun shade() {
        val entryPoints = entryPointsConfiguration.files.flatMap { readJson<List<String>>(it) }
        val classTrees = classTreesConfiguration.files.flatMap { readJson<Map<String, List<String>>>(it).entries }
            .groupingBy { it.key }
            .aggregate<Map.Entry<String, List<String>>, String, Set<String>> { _, accumulator: Set<String>?, element: Map.Entry<String, List<String>>, first ->
                if (first) {
                    element.value.toSet()
                } else {
                    accumulator!!.union(element.value)
                }
            }

        val classesToInclude = mutableSetOf<String>()

        val queue: Queue<String> = ArrayDeque<String>()
        queue.addAll(entryPoints)
        while (!queue.isEmpty()) {
            val className = queue.remove()
            if (classesToInclude.add(className)) {
                queue.addAll(classTrees.getOrDefault(className, emptySet()))
            }
        }

        JarOutputStream(BufferedOutputStream(FileOutputStream(jarFile.get().asFile))).use { jarOutputStream ->
            if (!manifests.isEmpty) {
                jarOutputStream.addJarEntry(JarFile.MANIFEST_NAME, manifests.first())
            }
            jarOutputStream.addJarEntry("org/gradle/build-receipt.properties", buildReceiptFile.get().asFile)
            relocatedClassesConfiguration.files.forEach { classesDir ->
                val classesDirPath = classesDir.toPath()
                classesDir.walk().filter {
                    val relativePath = classesDirPath.relativePath(it)
                    classesToInclude.contains(relativePath)
                }.forEach {
                    val relativePath = classesDirPath.relativePath(it)
                    jarOutputStream.addJarEntry(relativePath, it)
                }
            }
        }
    }

    private
    fun Path.relativePath(other: File) = relativize(other.toPath()).toString().replace(File.separatorChar, '/')

    private
    inline fun <reified T> readJson(file: File) =
        file.bufferedReader().use { reader ->
            Gson().fromJson<T>(reader, object : TypeToken<T>() {}.type)
        }
}
