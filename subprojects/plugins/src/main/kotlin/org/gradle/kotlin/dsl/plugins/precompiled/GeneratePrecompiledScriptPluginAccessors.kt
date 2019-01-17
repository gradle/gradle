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

package org.gradle.kotlin.dsl.plugins.precompiled

import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing

import org.gradle.kotlin.dsl.accessors.TypedProjectSchema
import org.gradle.kotlin.dsl.accessors.hashCodeFor

import org.gradle.kotlin.dsl.concurrent.IO
import org.gradle.kotlin.dsl.concurrent.withAsynchronousIO
import org.gradle.kotlin.dsl.concurrent.writeFile

import java.io.File


@CacheableTask
open class GeneratePrecompiledScriptPluginAccessors : ClassPathSensitiveCodeGenerationTask() {

    @get:OutputDirectory
    var metadataOutputDir = project.objects.directoryProperty()

    @get:Internal
    internal
    lateinit var plugins: List<ScriptPlugin>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @Suppress("unused")
    internal
    val scriptFiles: Set<File>
        get() = scriptPluginFilesOf(plugins)

    /**
     *  ## Computation and sharing of type-safe accessors
     * 1. Group precompiled script plugins by the set of plugins applied in their `plugins` block.
     * 2. For each group, compute the project schema implied by the set of plugins.
     * 3. Re-group precompiled script plugins by project schema.
     * 4. For each group, emit the type-safe accessors implied by the schema to a package named after the schema
     * hash code.
     * 5. For each group, for each script plugin in the group, write the generated package name to a file named
     * after the contents of the script plugin file. This is so the file can easily be found by
     * `PrecompiledScriptDependenciesResolver`.
     */
    @TaskAction
    fun generate() {
        withAsynchronousIO(project) {
            plugins.mapNotNull {
                scriptWithPluginsBlock(it)
            }.groupBy {
                it.pluginsBlock.plugins
            }.map {
                it to HashedProjectSchema(projectSchemaImpliedBy(it.key))
            }.groupBy(
                { (_, projectSchema) -> projectSchema },
                { (pluginGroup, _) -> pluginGroup.value }
            ).forEach { (projectSchema, pluginGroups) ->
                writeTypeSafeAccessorsFor(projectSchema)
                for (scriptPlugin in pluginGroups.asIterable().flatten()) {
                    writeContentAddressableImplicitImportFor(scriptPlugin, projectSchema.packageName)
                }
            }
        }
    }

    private
    fun IO.writeTypeSafeAccessorsFor(projectSchema: HashedProjectSchema) {
        TODO("not implemented")
    }

    private
    fun IO.writeContentAddressableImplicitImportFor(scriptPlugin: ScriptWithPluginsBlock, packageName: String) {
        io { writeFile(implicitImportFileFor(scriptPlugin), "$packageName.*".toByteArray()) }
    }

    private
    fun implicitImportFileFor(scriptPlugin: ScriptWithPluginsBlock): File =
        metadataOutputDir.get().asFile.resolve(hashBytesOf(scriptPlugin.script.scriptFile).toString())

    private
    fun hashBytesOf(file: File) = Hashing.hashBytes(file.readBytes())

    private
    fun scriptWithPluginsBlock(plugin: ScriptPlugin): ScriptWithPluginsBlock? =
        null

    private
    fun projectSchemaImpliedBy(
        plugins: List<PluginApplication>
    ): TypedProjectSchema = TODO()
}


internal
data class HashedProjectSchema(
    val schema: TypedProjectSchema,
    val hash: HashCode = hashCodeFor(schema)
) {
    val packageName by lazy {
        kotlinPackageNameFor("gradle-kotlin-dsl.type-safe-accessors.$$hash")
    }

    override fun hashCode(): Int = hash.hashCode()

    override fun equals(other: Any?): Boolean = other is HashedProjectSchema && hash == other.hash
}


internal
data class ScriptWithPluginsBlock(
    val script: ScriptPlugin,
    val pluginsBlock: PluginsBlock
)


internal
data class PluginsBlock(
    val lineNumber: Int,
    val plugins: List<PluginApplication>
)


internal
data class PluginApplication(
    val id: String,
    val version: String?,
    val apply: Boolean?
)


internal
fun scriptPluginFilesOf(list: List<ScriptPlugin>) = list.map { it.scriptFile }.toSet()
