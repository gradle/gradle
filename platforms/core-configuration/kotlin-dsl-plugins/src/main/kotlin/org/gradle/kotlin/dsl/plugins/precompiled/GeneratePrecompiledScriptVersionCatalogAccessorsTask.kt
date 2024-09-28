/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.catalog.DefaultVersionCatalog
import org.gradle.api.internal.catalog.LibrariesSourceGenerator
import org.gradle.api.internal.catalog.VersionCatalogView
import org.gradle.api.problems.Problems
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.plugin.use.PluginDependency
import java.io.ObjectInputStream
import java.io.Serializable
import java.nio.file.Files
import java.util.Base64
import java.util.WeakHashMap
import java.util.zip.GZIPInputStream
import javax.inject.Inject

@CacheableTask
abstract class GeneratePrecompiledScriptVersionCatalogAccessorsTask : DefaultTask() {
    @get:Internal
    abstract val versionCatalogs: SetProperty<VersionCatalog>

    internal data class VersionCatalogInput(
        val libraries: Map<String, MinimalExternalModuleDependency>,
        val versions: Map<String, VersionConstraint>,
        val plugins: Map<String, PluginDependency>,
        val bundles: Map<String, ExternalModuleDependencyBundle>,
    ) : Serializable {
        constructor(catalog: VersionCatalog) : this(
            catalog.libraryAliases.associateWith { catalog.findLibrary(it).get().get() },
            catalog.versionAliases.associateWith { catalog.findVersion(it).get() },
            catalog.pluginAliases.associateWith { catalog.findPlugin(it).get().get() },
            catalog.bundleAliases.associateWith { catalog.findBundle(it).get().get() },
        )
    }

    private val versionCatalogsInput: Map<String, VersionCatalogInput> by lazy {
        versionCatalogs.get().associateBy(
            { it.name },
            { VersionCatalogInput(it) }
        )
    }

    @get:Input
    internal val versionCatalogLibraries
        get() = versionCatalogsInput.mapValues { it.value.libraries }

    @get:Input
    internal val versionCatalogVersions
        get() = versionCatalogsInput.mapValues { it.value.versions }

    @get:Input
    internal val versionCatalogPlugins
        get() = versionCatalogsInput.mapValues { it.value.plugins }

    @get:Input
    internal val versionCatalogBundles
        get() = versionCatalogsInput.mapValues { it.value.bundles }

    @get:Input
    abstract val targetPackage: Property<String>

    @get:OutputDirectory
    abstract val outputJavaSrcDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputKotlinSrcDirectory: DirectoryProperty

    @get:Inject
    internal abstract val problems: Problems

    @TaskAction
    fun generateAccessors() {
        val outputJava = outputJavaSrcDirectory.get().asFile
        val outputKotlin = outputKotlinSrcDirectory.get().asFile
        val packageName = targetPackage.get()
        val packageRelDir = packageName.replace(".", "/")
        Files.createDirectories(outputJava.toPath().resolve(packageRelDir))
        Files.createDirectories(outputKotlin.toPath().resolve(packageRelDir))
        outputKotlin.resolve("${packageRelDir}/VersionCatalogAccessors.kt").writer().use { catalogExtWriter ->
            catalogExtWriter.appendLine("package $packageName")
            catalogExtWriter.appendLine()
            catalogExtWriter.appendLine("import ${DefaultVersionCatalog::class.qualifiedName}")
            catalogExtWriter.appendLine("import ${Project::class.qualifiedName}")
            catalogExtWriter.appendLine()
            catalogExtWriter.appendLine("private val catalogs = ${WeakHashMap::class.qualifiedName}<Project, Any>()")
            catalogExtWriter.appendLine()
            catalogExtWriter.appendLine("""
                fun deserializeVersionCatalog(input: String): DefaultVersionCatalog {
                    val bytes = ${Base64::class.qualifiedName}.getDecoder().decode(input)
                    return ${ObjectInputStream::class.qualifiedName}(${GZIPInputStream::class.qualifiedName}(bytes.inputStream())).use {
                        (it.readObject() as DefaultVersionCatalog)
                    }
                }
            """.trimIndent())

            versionCatalogs.get().forEach { catalog ->
                val className = "LibrariesFor${catalog.name.uppercaseFirstChar()}"
                val defaultVersionCatalog = (catalog as VersionCatalogView).config
                outputJava.resolve("$packageRelDir/$className.java").writer().use {
                    LibrariesSourceGenerator.generateSource(
                        it,
                        defaultVersionCatalog,
                        packageName,
                        className,
                        problems
                    )
                }
                catalogExtWriter.appendLine("val ${catalog.name}Serialized = \"${serializeVersionCatalog(defaultVersionCatalog)}\"")
                catalogExtWriter.appendLine("val Project.${catalog.name}: $packageName.$className")
                catalogExtWriter.appendLine("    get() = catalogs.computeIfAbsent(this) { " +
                    "objects.newInstance(" +
                    "$packageName.$className::class.java, " +
                    "deserializeVersionCatalog(${catalog.name}Serialized)," +
                    ")" +
                    "} as $packageName.$className")
            }
        }
    }
}
