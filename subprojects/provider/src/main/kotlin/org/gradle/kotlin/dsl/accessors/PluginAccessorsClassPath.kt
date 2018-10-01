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

package org.gradle.kotlin.dsl.accessors

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal

import org.gradle.internal.classpath.ClassPath

import org.gradle.kotlin.dsl.codegen.compileKotlinApiExtensionsTo
import org.gradle.kotlin.dsl.codegen.fileHeader
import org.gradle.kotlin.dsl.codegen.pluginEntriesFrom

import org.gradle.kotlin.dsl.provider.kotlinScriptClassPathProviderOf
import org.gradle.kotlin.dsl.support.useToRun

import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

import java.io.File


/**
 * Produces an [AccessorsClassPath] with type-safe accessors for all plugin ids found in the
 * `buildSrc` classpath of the given [project].
 *
 * The accessors provide content-assist for plugin ids and quick navigation to the plugin source code.
 */
fun pluginAccessorsClassPath(project: Project): AccessorsClassPath = project.rootProject.let { rootProject ->

    rootProject.getOrCreateProperty("gradleKotlinDsl.pluginAccessorsClassPath") {
        val buildSrcClassLoaderScope = baseClassLoaderScopeOf(rootProject)
        val cacheKeySpec = accessorsCacheKeyPrefix + buildSrcClassLoaderScope.exportClassLoader
        cachedAccessorsClassPathFor(rootProject, cacheKeySpec) { srcDir, binDir ->
            kotlinScriptClassPathProviderOf(rootProject).run {
                buildPluginAccessorsFor(
                    pluginDescriptorsClassPath = exportClassPathFromHierarchyOf(buildSrcClassLoaderScope),
                    accessorsCompilationClassPath = compilationClassPathOf(buildSrcClassLoaderScope),
                    srcDir = srcDir,
                    binDir = binDir
                )
            }
        }
    }
}


private
fun baseClassLoaderScopeOf(rootProject: Project) =
    (rootProject as ProjectInternal).baseClassLoaderScope


internal
fun buildPluginAccessorsFor(
    pluginDescriptorsClassPath: ClassPath,
    accessorsCompilationClassPath: ClassPath,
    srcDir: File,
    binDir: File
) {
    val pluginSpecs = pluginSpecsFrom(pluginDescriptorsClassPath)
    val pluginTrees = PluginTree.of(pluginSpecs)
    val accessors = pluginAccessorsFor(pluginTrees)
    val sourceFile = srcDir.resolve("org/gradle/kotlin/dsl/PluginAccessors.kt")
    writePluginAccessorsTo(sourceFile, accessors)
    compileKotlinApiExtensionsTo(
        binDir,
        listOf(sourceFile),
        accessorsCompilationClassPath.asFiles
    )
}


private
fun writePluginAccessorsTo(sourceFile: File, accessors: Sequence<PluginAccessor>) {
    sourceFile.apply { parentFile.mkdirs() }.bufferedWriter().useToRun {
        appendln(fileHeader)
        appendln("""
            import ${PluginDependenciesSpec::class.qualifiedName}
            import ${PluginDependencySpec::class.qualifiedName}
        """.replaceIndent())
        accessors.runEach {
            newLine()
            newLine()
            when (this) {
                is PluginAccessor.ForPlugin -> {
                    appendln("""
                        /**
                         * The `$id` plugin implemented by [$implementationClass].
                         */
                        val `$extendedType`.`$extensionName`: PluginDependencySpec
                            get() = ${pluginDependenciesSpecOf(extendedType)}.id("$id")
                    """.replaceIndent())
                }
                is PluginAccessor.ForGroup -> {
                    appendln("""
                        /**
                         * The `$id` plugin group.
                         */
                        class `$groupType`(internal val plugins: PluginDependenciesSpec)


                        /**
                         * Plugin ids starting with `$id`.
                         */
                        val `$extendedType`.`$extensionName`: `$groupType`
                            get() = `$groupType`(${pluginDependenciesSpecOf(extendedType)})
                    """.replaceIndent())
                }
            }
        }
    }
}


private
fun pluginDependenciesSpecOf(extendedType: String): String = when (extendedType) {
    "PluginDependenciesSpec" -> "this"
    else -> "plugins"
}


private
inline fun <T> Sequence<T>.runEach(f: T.() -> Unit) {
    forEach { it.run(f) }
}


internal
fun pluginAccessorsFor(pluginTrees: Map<String, PluginTree>, extendedType: String = "PluginDependenciesSpec"): Sequence<PluginAccessor> = sequence {

    for ((extensionName, pluginTree) in pluginTrees) {
        when (pluginTree) {
            is PluginTree.PluginGroup -> {
                val groupId = pluginTree.path.joinToString(".")
                val groupType = pluginGroupTypeFor(pluginTree.path)
                yield(PluginAccessor.ForGroup(groupId, groupType, extendedType, extensionName))
                yieldAll(pluginAccessorsFor(pluginTree.plugins, groupType))
            }
            is PluginTree.PluginSpec -> {
                yield(
                    PluginAccessor.ForPlugin(
                        pluginTree.id,
                        pluginTree.implementationClass,
                        extendedType,
                        extensionName
                    )
                )
            }
        }
    }
}


internal
sealed class PluginAccessor {

    data class ForPlugin(
        val id: String,
        val implementationClass: String,
        val extendedType: String,
        val extensionName: String
    ) : PluginAccessor()

    data class ForGroup(
        val id: String,
        val groupType: String,
        val extendedType: String,
        val extensionName: String
    ) : PluginAccessor()
}


private
fun pluginSpecsFrom(pluginDescriptorsClassPath: ClassPath): Iterable<PluginTree.PluginSpec> =
    pluginDescriptorsClassPath
        .asFiles
        .filter { it.isFile && it.extension.equals("jar", true) }
        .flatMap { pluginEntriesFrom(it) }
        .map { PluginTree.PluginSpec(it.pluginId, it.implementationClass) }


private
fun pluginGroupTypeFor(path: List<String>) =
    path.joinToString(separator = "") { it.capitalize() } + "PluginGroup"


