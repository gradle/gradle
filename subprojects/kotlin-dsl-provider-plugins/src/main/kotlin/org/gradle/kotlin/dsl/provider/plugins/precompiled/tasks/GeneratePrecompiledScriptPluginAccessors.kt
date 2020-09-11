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

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.initialization.DefaultGradlePropertiesController
import org.gradle.initialization.GradlePropertiesController
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.concurrent.CompositeStoppable.stoppable
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.internal.hash.HashCode
import org.gradle.internal.resource.TextFileResourceLoader
import org.gradle.kotlin.dsl.accessors.AccessorFormats
import org.gradle.kotlin.dsl.accessors.ProjectSchemaProvider
import org.gradle.kotlin.dsl.accessors.TypedProjectSchema
import org.gradle.kotlin.dsl.accessors.buildAccessorsFor
import org.gradle.kotlin.dsl.accessors.hashCodeFor
import org.gradle.kotlin.dsl.concurrent.AsyncIOScopeFactory
import org.gradle.kotlin.dsl.concurrent.IO
import org.gradle.kotlin.dsl.concurrent.writeFile
import org.gradle.kotlin.dsl.precompile.PrecompiledScriptDependenciesResolver
import org.gradle.kotlin.dsl.provider.plugins.precompiled.PrecompiledScriptPlugin
import org.gradle.kotlin.dsl.provider.plugins.precompiled.scriptPluginFilesOf
import org.gradle.kotlin.dsl.support.KotlinScriptType
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.support.useToRun
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.internal.PluginRequestApplicator
import org.gradle.plugin.use.internal.PluginRequestCollector
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import javax.inject.Inject


@CacheableTask
abstract class GeneratePrecompiledScriptPluginAccessors @Inject internal constructor(

    private
    val projectLayout: ProjectLayout,

    private
    val classLoaderScopeRegistry: ClassLoaderScopeRegistry,

    private
    val asyncIOScopeFactory: AsyncIOScopeFactory,

    private
    val textFileResourceLoader: TextFileResourceLoader,

    private
    val projectSchemaProvider: ProjectSchemaProvider

) : ClassPathSensitiveCodeGenerationTask() {

    private
    val gradleUserHomeDir = project.gradle.gradleUserHomeDir

    private
    val projectDesc = project.toString()

    @get:InputFiles
    @get:Classpath
    abstract val runtimeClassPathFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val metadataOutputDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compiledPluginsBlocksDir: DirectoryProperty

    @get:Internal
    internal
    lateinit var plugins: List<PrecompiledScriptPlugin>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @Suppress("unused")
    internal
    val scriptFiles: Set<File>
        get() = scriptPluginFilesOf(plugins)

    /**
     *  ## Computation and sharing of type-safe accessors
     * 1. Group precompiled script plugins by the list of plugins applied in their `plugins` block.
     * 2. For each group, compute the project schema implied by the list of plugins.
     * 3. Re-group precompiled script plugins by project schema.
     * 4. For each group, emit the type-safe accessors implied by the schema to a package named after the schema
     * hash code.
     * 5. For each group, for each script plugin in the group, write the generated package name to a file named
     * after the contents of the script plugin file. This is so the file can be easily found by
     * [PrecompiledScriptDependenciesResolver].
     */
    @TaskAction
    fun generate() {

        recreateTaskDirectories()

        val projectPlugins = selectProjectPlugins()
        if (projectPlugins.isNotEmpty()) {
            asyncIOScopeFactory.newScope().useToRun {
                generateTypeSafeAccessorsFor(projectPlugins)
            }
        }
    }

    private
    fun recreateTaskDirectories() {
        recreate(temporaryDir)
        recreate(sourceCodeOutputDir.get().asFile)
        recreate(metadataOutputDir.get().asFile)
    }

    private
    fun IO.generateTypeSafeAccessorsFor(projectPlugins: List<PrecompiledScriptPlugin>) {
        resolvePluginGraphOf(projectPlugins)
            .groupBy(
                { it.plugins },
                { it.scriptPlugin }
            ).let {
                projectSchemaImpliedByPluginGroups(it)
            }.forEach { (projectSchema, scriptPlugins) ->
                writeTypeSafeAccessorsFor(projectSchema)
                for (scriptPlugin in scriptPlugins) {
                    writeContentAddressableImplicitImportFor(
                        projectSchema.packageName,
                        scriptPlugin
                    )
                }
            }
    }

    private
    fun resolvePluginGraphOf(projectPlugins: List<PrecompiledScriptPlugin>): Sequence<ScriptPluginPlugins> {

        val scriptPluginsById = scriptPluginPluginsFor(projectPlugins).associateBy {
            it.scriptPlugin.id
        }

        val pluginGraph = plugins.associate {
            it.id to pluginsAppliedBy(it, scriptPluginsById)
        }

        return reduceGraph(pluginGraph).asSequence().mapNotNull { (id, plugins) ->
            scriptPluginsById[id]?.copy(plugins = plugins.toList())
        }
    }

    private
    fun pluginsAppliedBy(scriptPlugin: PrecompiledScriptPlugin, scriptPluginsById: Map<String, ScriptPluginPlugins>) =
        scriptPluginsById[scriptPlugin.id]?.plugins ?: emptyList()

    private
    fun scriptPluginPluginsFor(projectPlugins: List<PrecompiledScriptPlugin>) = sequence {
        val loader = createPluginsClassLoader()
        try {
            for (plugin in projectPlugins) {
                loader.scriptPluginPluginsFor(plugin)?.let {
                    yield(it)
                }
            }
        } finally {
            stoppable(loader).stop()
        }
    }

    private
    fun ClassLoader.scriptPluginPluginsFor(plugin: PrecompiledScriptPlugin): ScriptPluginPlugins? {

        // The compiled script class won't be present for precompiled script plugins
        // which don't include a `plugins` block
        if (getResource(compiledScriptClassFile(plugin)) == null) {
            return null
        }

        val pluginRequests = collectPluginRequestsOf(plugin)
        validatePluginRequestsOf(plugin, pluginRequests)
        return ScriptPluginPlugins(
            plugin,
            pluginRequests.map { it.id.id }
        )
    }

    private
    fun validatePluginRequestsOf(plugin: PrecompiledScriptPlugin, requests: PluginRequests) {
        val validationErrors = requests.mapNotNull { validationErrorFor(it) }
        if (validationErrors.isNotEmpty()) {
            throw LocationAwareException(
                IllegalArgumentException(validationErrors.joinToString("\n")),
                plugin.scriptFile.path,
                requests.first().lineNumber
            )
        }
    }

    private
    fun validationErrorFor(pluginRequest: PluginRequestInternal): String? {
        if (pluginRequest.version != null) {
            return "Invalid plugin request $pluginRequest. Plugin requests from precompiled scripts must not include a version number. Please remove the version from the offending request and make sure the module containing the requested plugin '${pluginRequest.id}' is an implementation dependency of $projectDesc."
        }
        // TODO:kotlin-dsl validate apply false
        return null
    }

    private
    fun ClassLoader.collectPluginRequestsOf(plugin: PrecompiledScriptPlugin): PluginRequests =
        pluginRequestCollectorFor(plugin).run {

            loadClass(plugin.compiledScriptTypeName)
                .getConstructor(PluginDependenciesSpec::class.java)
                .newInstance(createSpec(1))

            pluginRequests
        }

    private
    fun pluginRequestCollectorFor(plugin: PrecompiledScriptPlugin) =
        PluginRequestCollector(scriptSourceFor(plugin))

    private
    fun scriptSourceFor(plugin: PrecompiledScriptPlugin) =
        TextResourceScriptSource(
            textFileResourceLoader.loadFile(
                "Precompiled script plugin",
                plugin.scriptFile
            )
        )

    private
    fun compiledScriptClassFile(plugin: PrecompiledScriptPlugin) =
        plugin.compiledScriptTypeName.replace('.', '/') + ".class"

    private
    fun selectProjectPlugins() = plugins.filter { it.scriptType == KotlinScriptType.PROJECT }

    private
    fun createPluginsClassLoader(): ClassLoader =
        URLClassLoader(
            compiledPluginsClassPath().asURLArray,
            classLoaderScopeRegistry.coreAndPluginsScope.localClassLoader
        )

    private
    fun compiledPluginsClassPath() =
        DefaultClassPath.of(compiledPluginsBlocksDir.get().asFile) + classPath

    private
    fun projectSchemaImpliedByPluginGroups(
        pluginGroupsPerRequests: Map<List<String>, List<PrecompiledScriptPlugin>>
    ): Map<HashedProjectSchema, List<PrecompiledScriptPlugin>> {

        val schemaBuilder = SyntheticProjectSchemaBuilder(
            gradleUserHomeDir = gradleUserHomeDir,
            rootProjectDir = uniqueTempDirectory(),
            rootProjectClassPath = (classPathFiles + runtimeClassPathFiles).files,
            projectSchemaProvider = projectSchemaProvider
        )
        return pluginGroupsPerRequests.flatMap { (uniquePluginRequests, scriptPlugins) ->
            try {
                val schema = schemaBuilder.schemaFor(pluginRequestsFor(uniquePluginRequests, scriptPlugins.first()))
                val hashedSchema = HashedProjectSchema(schema)
                scriptPlugins.map { hashedSchema to it }
            } catch (error: Throwable) {
                reportProjectSchemaError(scriptPlugins, error)
                emptyList<Pair<HashedProjectSchema, PrecompiledScriptPlugin>>()
            }
        }.groupBy(
            { (schema, _) -> schema },
            { (_, plugin) -> plugin }
        )
    }

    private
    fun uniqueTempDirectory() = Files.createTempDirectory(temporaryDir.toPath(), "project-").toFile()

    private
    fun pluginRequestsFor(pluginIds: List<String>, plugin: PrecompiledScriptPlugin): PluginRequests =
        pluginRequestCollectorFor(plugin).run {
            createSpec(1).apply {
                pluginIds.forEach {
                    id(it)
                }
            }
            pluginRequests
        }

    private
    fun reportProjectSchemaError(plugins: List<PrecompiledScriptPlugin>, error: Throwable) {
        logger.warn(
            plugins.joinToString(
                prefix = "Failed to generate type-safe Gradle model accessors for the following precompiled script plugins:\n",
                separator = "\n",
                postfix = "\n"
            ) { " - " + projectRelativePathOf(it) },
            error
        )
    }

    private
    fun projectRelativePathOf(scriptPlugin: PrecompiledScriptPlugin) =
        scriptPlugin.scriptFile.toRelativeString(projectLayout.projectDirectory.asFile)

    private
    fun IO.writeTypeSafeAccessorsFor(hashedSchema: HashedProjectSchema) {
        buildAccessorsFor(
            hashedSchema.schema,
            classPath,
            sourceCodeOutputDir.get().asFile,
            null,
            hashedSchema.packageName,
            AccessorFormats.internal
        )
    }

    private
    fun IO.writeContentAddressableImplicitImportFor(packageName: String, scriptPlugin: PrecompiledScriptPlugin) {
        writeFile(implicitImportFileFor(scriptPlugin), "$packageName.*".toByteArray())
    }

    private
    fun implicitImportFileFor(scriptPlugin: PrecompiledScriptPlugin): File =
        metadataOutputDir.get().asFile.resolve(scriptPlugin.hashString)
}


internal
class SyntheticProjectSchemaBuilder(
    gradleUserHomeDir: File,
    rootProjectDir: File,
    rootProjectClassPath: Collection<File>,
    private val projectSchemaProvider: ProjectSchemaProvider
) {

    private
    val rootProject = buildRootProject(gradleUserHomeDir, rootProjectDir, rootProjectClassPath)

    fun schemaFor(plugins: PluginRequests): TypedProjectSchema =
        projectSchemaProvider.schemaFor(childProjectWith(plugins))

    private
    fun childProjectWith(pluginRequests: PluginRequests): Project {

        val project = ProjectBuilder.builder()
            .withParent(rootProject)
            .withProjectDir(rootProject.projectDir.resolve("schema"))
            .build()

        applyPluginsTo(project, pluginRequests)

        return project
    }

    private
    fun buildRootProject(
        gradleUserHomeDir: File,
        projectDir: File,
        rootProjectClassPath: Collection<File>
    ): Project {

        val project = ProjectBuilder.builder()
            .withGradleUserHomeDir(gradleUserHomeDir)
            .withProjectDir(projectDir)
            .build()
            .withEmptyGradleProperties()

        addScriptClassPathDependencyTo(project, rootProjectClassPath)

        applyPluginsTo(project, PluginRequests.EMPTY)

        return project
    }

    private
    fun Project.withEmptyGradleProperties(): Project {
        gradle.run {
            require(this is GradleInternal)
            services[GradlePropertiesController::class.java].run {
                require(this is DefaultGradlePropertiesController)
                overrideWith(EmptyGradleProperties)
            }
        }
        return this
    }

    private
    object EmptyGradleProperties : GradleProperties {
        override fun find(propertyName: String?) = null
        override fun mergeProperties(properties: Map<String, String>) = properties.toMap()
    }

    private
    fun addScriptClassPathDependencyTo(project: Project, rootProjectClassPath: Collection<File>) {
        val scriptHandler = project.buildscript as ScriptHandlerInternal
        scriptHandler.addScriptClassPathDependency(
            DefaultSelfResolvingDependency(
                project
                    .serviceOf<FileCollectionFactory>()
                    .fixed("precompiled-script-plugins-accessors-classpath", rootProjectClassPath)
            )
        )
    }

    private
    fun applyPluginsTo(project: Project, pluginRequests: PluginRequests) {
        val targetProjectScope = (project as ProjectInternal).classLoaderScope
        project.serviceOf<PluginRequestApplicator>().applyPlugins(
            pluginRequests,
            project.buildscript,
            project.pluginManager,
            targetProjectScope
        )
    }
}


internal
data class HashedProjectSchema(
    val schema: TypedProjectSchema,
    val hash: HashCode = hashCodeFor(schema)
) {
    val packageName by lazy {
        "gradle.kotlin.dsl.accessors._$hash"
    }

    override fun hashCode(): Int = hash.hashCode()

    override fun equals(other: Any?): Boolean = other is HashedProjectSchema && hash == other.hash
}


private
data class ScriptPluginPlugins(
    val scriptPlugin: PrecompiledScriptPlugin,
    val plugins: List<String>
)
