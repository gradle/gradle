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

package org.gradle.kotlin.dsl.plugins.precompiled

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.HashCode

import org.gradle.kotlin.dsl.accessors.writeSourceCodeForPluginSpecBuildersFor
import org.gradle.kotlin.dsl.support.serviceOf


@CacheableTask
open class GeneratePluginSpecBuilders : DefaultTask() {

    @get:OutputDirectory
    var outputDirectory = project.objects.directoryProperty()

    @get:Classpath
    lateinit var classPath: FileCollection

    @TaskAction
    @Suppress("unused")
    internal
    fun generate() =
        outputDirectory.asFile.get().let { outputDir ->
            val packageDir = outputDir.resolve(packageName.split('.').joinToString("/")).apply {
                mkdirs()
            }
            val outputFile = packageDir.resolve("PluginSpecBuildersFor$$classPathHash.kt")
            writeSourceCodeForPluginSpecBuildersFor(
                pluginDescriptorClassPath,
                outputFile,
                packageName = kotlinPackageName
            )
        }

    @get:Internal
    internal
    val kotlinPackageName by lazy {
        kotlinPackageNameFor(packageName)
    }

    // TODO: consider a package name derived from the classpath hash
    // "gradle-kotlin-dsl.plugin-spec-builders.$$classPathHash"
    private
    val packageName
        get() = "org.gradle.kotlin.dsl"

    private
    val classPathHash
        get() = hashOf(pluginDescriptorClassPath)

    private
    val pluginDescriptorClassPath by lazy {
        DefaultClassPath.of(classPath.files)
    }

    private
    fun hashOf(classPath: ClassPath): HashCode =
        project.serviceOf<ClasspathHasher>().hash(classPath)

    private
    fun kotlinPackageNameFor(packageName: String) =
        packageName.split('.').joinToString(".") { "`$it`" }
}
