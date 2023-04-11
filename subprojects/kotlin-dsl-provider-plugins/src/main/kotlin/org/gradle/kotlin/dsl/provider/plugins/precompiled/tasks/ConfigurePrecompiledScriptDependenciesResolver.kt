/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.fingerprint.classpath.ClasspathFingerprinter

import org.gradle.kotlin.dsl.precompile.PrecompiledScriptDependenciesResolver.EnvironmentProperties.kotlinDslImplicitImports
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.listFilesOrdered
import org.gradle.work.DisableCachingByDefault

import java.io.File
import javax.inject.Inject


@DisableCachingByDefault(because = "Produces no cacheable output")
abstract class ConfigurePrecompiledScriptDependenciesResolver @Inject constructor(

    private
    val implicitImports: ImplicitImports

) : DefaultTask(), SharedAccessorsPackageAware {

    @get:Internal
    abstract val metadataDir: DirectoryProperty

    private
    lateinit var onConfigure: (Provider<String>) -> Unit

    fun onConfigure(action: (Provider<String>) -> Unit) {
        onConfigure = action
    }

    @TaskAction
    fun configureImports() {
        val resolverEnvironment = resolverEnvironmentStringFor(
            implicitImports,
            classPathFingerprinter,
            classPathFiles,
            metadataDir
        )
        onConfigure(resolverEnvironment)
    }
}


internal
fun resolverEnvironmentStringFor(
    implicitImports: ImplicitImports,
    classPathFingerprinter: ClasspathFingerprinter,
    classPathFiles: FileCollection,
    accessorsMetadataDir: Provider<Directory>
): Provider<String> = accessorsMetadataDir.map { metadataDir ->
    resolverEnvironmentStringFor(
        listOf(
            kotlinDslImplicitImports to implicitImportsForPrecompiledScriptPlugins(implicitImports, classPathFingerprinter, classPathFiles)
        ) + precompiledScriptPluginImportsFrom(metadataDir.asFile)
    )
}


private
fun precompiledScriptPluginImportsFrom(metadataDirFile: File): List<Pair<String, List<String>>> =
    metadataDirFile.run {
        require(isDirectory)
        listFilesOrdered().map {
            it.name to it.readLines()
        }
    }


private
fun resolverEnvironmentStringFor(properties: Iterable<Pair<String, List<String>>>): String =
    properties.joinToString(separator = ",") { (key, values) ->
        "$key=\"${values.joinToString(":")}\""
    }
