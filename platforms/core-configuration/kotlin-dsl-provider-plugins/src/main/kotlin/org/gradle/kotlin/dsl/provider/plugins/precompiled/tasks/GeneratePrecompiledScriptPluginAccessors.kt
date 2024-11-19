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

import org.gradle.StartParameter
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.artifacts.DependencyManagementServices
import org.gradle.api.internal.artifacts.dependencies.DefaultFileCollectionDependency
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal.ClassPathNotation
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.initialization.ScriptClassPathResolver
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.initialization.BuildLayoutParameters
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.internal.Try
import org.gradle.internal.build.NestedRootBuildRunner.createNestedBuildTree
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier
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
import org.gradle.kotlin.dsl.provider.plugins.precompiled.PrecompiledScriptException
import org.gradle.kotlin.dsl.provider.plugins.precompiled.PrecompiledScriptPlugin
import org.gradle.kotlin.dsl.provider.plugins.precompiled.scriptPluginFilesOf
import org.gradle.kotlin.dsl.support.KotlinScriptType
import org.gradle.kotlin.dsl.support.get
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.support.useToRun
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.internal.PluginRequestApplicator
import org.gradle.plugin.use.internal.PluginRequestCollector
import org.gradle.util.internal.TextUtil.normaliseFileSeparators
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import javax.inject.Inject


internal
const val STRICT_MODE_SYSTEM_PROPERTY_NAME = "org.gradle.kotlin.dsl.precompiled.accessors.strict"


@CacheableTask
abstract class GeneratePrecompiledScriptPluginAccessors @Inject internal constructor(

    private
    val projectLayout: ProjectLayout,

    private
    val classLoaderScopeRegistry: ClassLoaderScopeRegistry,

    private
    val asyncIOScopeFactory: AsyncIOScopeFactory,

    private
    val textFileResourceLoader: TextFileResourceLoader

) : ClassPathSensitiveCodeGenerationTask() {

    private
    val projectDesc = project.toString()

    @get:InputFiles
    @get:Classpath
    val runtimeClassPathFiles: FileCollection
        get() = runtimeClassPathArtifactCollection.get().artifactFiles

    /**
     * Tracked via [runtimeClassPathFiles].
     */
    @get:Internal
    internal
    abstract val runtimeClassPathArtifactCollection: Property<ArtifactCollection>

    @get:OutputDirectory
    abstract val metadataOutputDir: DirectoryProperty

    @get:InputDirectory
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compiledPluginsBlocksDir: DirectoryProperty

    @get:Internal
    internal
    abstract val plugins: ListProperty<PrecompiledScriptPlugin>

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @Suppress("unused")
    internal
    val scriptFiles: Provider<Set<File>>
        get() = scriptPluginFilesOf(plugins)

    @get:Input
    @Deprecated("Will be removed in Gradle 9.0")
    abstract val strict: Property<Boolean>

    init {
        outputs.doNotCacheIf(
            "Generated accessors can only be cached in strict mode."
        ) {
            @Suppress("DEPRECATION")
            !strict.get()
        }
    }

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

        val projectScriptPlugins = selectProjectScriptPlugins()
        if (projectScriptPlugins.isNotEmpty()) {
            asyncIOScopeFactory.newScope().useToRun {
                generateTypeSafeAccessorsFor(projectScriptPlugins)
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
    fun IO.generateTypeSafeAccessorsFor(projectScriptPlugins: List<PrecompiledScriptPlugin>) {
        resolvePluginGraphOf(projectScriptPlugins)
            .groupBy(
                { it.appliedPlugins },
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
    fun resolvePluginGraphOf(projectScriptPlugins: List<PrecompiledScriptPlugin>): Sequence<ScriptPluginPlugins> {

        val scriptPluginsById = scriptPluginPluginsFor(projectScriptPlugins).associateBy {
            it.scriptPlugin.id
        }

        val pluginGraph = plugins.get().associate {
            it.id to pluginsAppliedBy(it, scriptPluginsById)
        }

        return reduceGraph(pluginGraph).asSequence().mapNotNull { (id, plugins) ->
            scriptPluginsById[id]?.copy(appliedPlugins = plugins.toList())
        }
    }

    private
    fun pluginsAppliedBy(scriptPlugin: PrecompiledScriptPlugin, scriptPluginsById: Map<String, ScriptPluginPlugins>) =
        scriptPluginsById[scriptPlugin.id]?.appliedPlugins ?: emptyList()

    private
    fun scriptPluginPluginsFor(projectScriptPlugins: List<PrecompiledScriptPlugin>) = sequence {
        val loader = createPluginsClassLoader()
        try {
            for (plugin in projectScriptPlugins) {
                yield(loader.scriptPluginPluginsFor(plugin))
            }
        } finally {
            stoppable(loader).stop()
        }
    }

    private
    fun ClassLoader.scriptPluginPluginsFor(plugin: PrecompiledScriptPlugin): ScriptPluginPlugins =
        withCapturedOutputOnError(
            {
                if (getResource(compiledScriptClassFile(plugin)) == null) {
                    // The compiled script class won't be present for precompiled script plugins
                    // which don't include a `plugins` block
                    ScriptPluginPlugins(plugin, emptyList())
                } else {
                    val pluginRequests = collectPluginRequestsOf(plugin)
                    validatePluginRequestsOf(plugin, pluginRequests)
                    ScriptPluginPlugins(
                        plugin,
                        pluginRequests.map { it.id.id }
                    )
                }
            },
            { (error, stdout, stderr) ->
                throw PrecompiledScriptException(
                    buildString {
                        append("Failed to collect plugin requests of '${projectRelativePathOf(plugin)}'")
                        appendStdoutStderr(stdout, stderr)
                    },
                    error
                )
            }
        )

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
            return "Invalid plugin request $pluginRequest. Plugin requests from precompiled scripts must not include a version number. " +
                "Please remove the version from the offending request and make sure the module containing the requested plugin '${pluginRequest.id}' is an implementation dependency of $projectDesc."
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
    fun selectProjectScriptPlugins() = plugins.get().filter { it.scriptType == KotlinScriptType.PROJECT }

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
    ): Map<HashedProjectSchema, List<PrecompiledScriptPlugin>> =

        pluginGroupsPerRequests.flatMap { (uniquePluginRequests, scriptPlugins) ->
            withCapturedOutputOnError(
                {
                    val schema = projectSchemaFor(pluginRequestsOf(scriptPlugins.first(), uniquePluginRequests)).get()
                    val hashed = HashedProjectSchema(schema)
                    scriptPlugins.map { hashed to it }
                },
                { (error, stdout, stderr) ->
                    reportProjectSchemaError(scriptPlugins, stdout, stderr, error)
                    emptyList()
                }
            )
        }.groupBy(
            { (schema, _) -> schema },
            { (_, plugin) -> plugin }
        )

    /**
     * Computes the [project schema][TypedProjectSchema] implied by the given plugins by applying
     * them to a synthetic root project in the context of a nested build.
     */
    private
    fun projectSchemaFor(plugins: PluginRequests): Try<TypedProjectSchema> {
        val projectDir = uniqueTempDirectory()
        val startParameter = projectSchemaBuildStartParameterFor(projectDir)
        return createNestedBuildTree("$path:${projectDir.name}", startParameter, services).run { controller ->
            controller.withEmptyBuild { settings ->
                Try.ofFailable {
                    val gradle = settings.gradle
                    val baseScope = classLoaderScopeRegistry.coreAndPluginsScope.createChild("accessors-classpath", null).apply {
                        // we export the build logic classpath to the base scope here so that all referenced plugins
                        // can be resolved in the root project scope created below.
                        export(buildLogicClassPath(gradle))
                        lock()
                    }
                    val rootProjectScope = baseScope.createChild("accessors-root-project", null)
                    settings.rootProject.name = "gradle-kotlin-dsl-accessors"
                    val projectState = gradle.serviceOf<ProjectStateRegistry>().registerProject(gradle.owner, settings.rootProject as DefaultProjectDescriptor)
                    projectState.createMutableModel(rootProjectScope, baseScope)
                    val rootProject = projectState.mutableModel
                    gradle.rootProject = rootProject
                    gradle.defaultProject = rootProject
                    rootProject.projectEvaluationBroadcaster.beforeEvaluate(rootProject)
                    rootProject.run {
                        applyPlugins(plugins)
                        serviceOf<ProjectSchemaProvider>().schemaFor(this, classLoaderScope)!!
                    }
                }
            }
        }
    }

    private
    fun projectSchemaBuildStartParameterFor(projectDir: File): ProjectSchemaBuildStartParameter =
        services.get<StartParameterInternal>().let { startParameter ->
            ProjectSchemaBuildStartParameter(
                BuildLayoutParameters(
                    startParameter.gradleHomeDir,
                    startParameter.gradleUserHomeDir,
                    projectDir,
                    projectDir,
                    null,
                    null
                ),
                startParameter.isOffline,
            )
        }

    /**
     * A [StartParameter] subclass that provides no init scripts.
     */
    private
    class ProjectSchemaBuildStartParameter(
        buildLayout: BuildLayoutParameters,
        offline: Boolean,
    ) : StartParameterInternal(buildLayout) {

        init {
            // Dry run in case a callback tries to access the task graph.
            isDryRun = true
            isOffline = offline
            doNotSearchUpwards()
            useEmptySettings()
        }

        override fun getAllInitScripts(): List<File> = emptyList()
        override fun newInstance(): StartParameterInternal = throw UnsupportedOperationException()
        override fun newBuild(): StartParameterInternal = throw UnsupportedOperationException()
    }

    private
    fun buildLogicClassPath(gradle: Gradle): ClassPath {
        // Ideally we would pass already instrumented classpath to a task and then just export it to the classloader.
        // But since we do some artifact transform caching via BuildService,
        // that would add some complexity when wiring GeneratePrecompiledScriptPluginAccessors task.
        val dependencyManagementServices = gradle.serviceOf<DependencyManagementServices>()
        val dependencyResolutionServices = dependencyManagementServices.newDetachedResolver(
            StandaloneDomainObjectContext.PLUGINS
        )

        val dependencies = dependencyResolutionServices.dependencyHandler
        val configurations = dependencyResolutionServices.configurationContainer
        val fileCollectionFactory = gradle.serviceOf<FileCollectionFactory>()
        val configuration = createBuildLogicClassPathConfiguration(dependencies, configurations, fileCollectionFactory)

        val resolver = gradle.serviceOf<ScriptClassPathResolver>()
        val resolutionContext = resolver.prepareDependencyHandler(dependencies)
        resolver.prepareClassPath(configuration, resolutionContext)
        return resolver.resolveClassPath(configuration, resolutionContext)
    }

    private
    fun createBuildLogicClassPathConfiguration(
        dependencyHandler: DependencyHandler,
        configurations: ConfigurationContainer,
        fileCollectionFactory: FileCollectionFactory
    ): Configuration {
        val dependencies = runtimeClassPathArtifactCollection.get().artifacts.map {
            when (val componentIdentifier = it.id.componentIdentifier) {
                is OpaqueComponentIdentifier -> DefaultFileCollectionDependency(
                    componentIdentifier,
                    fileCollectionFactory.fixed(it.file)
                )
                is ProjectComponentIdentifier -> DefaultFileCollectionDependency(
                    OpaqueComponentIdentifier(ClassPathNotation.LOCAL_PROJECT_AS_OPAQUE_DEPENDENCY),
                    fileCollectionFactory.fixed(componentIdentifier.displayName, it.file)
                )
                else -> {
                    dependencyHandler.create(fileCollectionFactory.fixed(it.file))
                }
            }
        }.toTypedArray()
        @Suppress("SpreadOperator")
        return configurations.detachedConfiguration(*dependencies)
    }

    private
    fun uniqueTempDirectory() =
        Files.createTempDirectory(temporaryDir.toPath(), "accessors").toFile()

    private
    fun pluginRequestsOf(plugin: PrecompiledScriptPlugin, pluginIds: List<String>): PluginRequests =
        pluginRequestCollectorFor(plugin).run {
            createSpec(1).apply {
                pluginIds.forEach {
                    id(it)
                }
            }
            pluginRequests
        }

    private
    fun reportProjectSchemaError(plugins: List<PrecompiledScriptPlugin>, stdout: String, stderr: String, error: Throwable) {
        @Suppress("DEPRECATION")
        if (strict.get()) throw PrecompiledScriptException(failedToGenerateAccessorsFor(plugins, stdout, stderr), error)
        else logger.warn(failedToGenerateAccessorsFor(plugins, stdout, stderr), error)
    }

    private
    fun failedToGenerateAccessorsFor(plugins: List<PrecompiledScriptPlugin>, stdout: String, stderr: String): String =
        buildString {
            append(plugins.joinToString(
                prefix = "Failed to generate type-safe Gradle model accessors for the following precompiled script plugins:\n",
                separator = "\n",
            ) { " - " + projectRelativePathOf(it) })
            appendStdoutStderr(stdout, stderr)
        }

    private
    fun StringBuilder.appendStdoutStderr(stdout: String, stderr: String) {
        if (stdout.isNotBlank()) {
            append("\nStandard output:\n${stdout.trim().prependIndent()}")
        }
        if (stderr.isNotBlank()) {
            append("\nStandard error:\n${stderr.trim().prependIndent()}")
        }
    }

    private
    fun projectRelativePathOf(scriptPlugin: PrecompiledScriptPlugin) =
        normaliseFileSeparators(scriptPlugin.scriptFile.toRelativeString(projectLayout.projectDirectory.asFile))

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
    val appliedPlugins: List<String>
)


private
fun ProjectInternal.applyPlugins(pluginRequests: PluginRequests) {
    serviceOf<PluginRequestApplicator>().applyPlugins(
        pluginRequests,
        buildscript,
        pluginManager,
        classLoaderScope
    )
}


private
fun <T> withCapturedOutputOnError(block: () -> T, onError: (ErrorWithCapturedOutput) -> T): T {
    val outCapture = ThreadLocalCapturePrintStream(System.out)
    val errCapture = ThreadLocalCapturePrintStream(System.err)
    return try {
        val previousOut = System.out
        val previousErr = System.err
        try {
            System.setOut(outCapture)
            System.setErr(errCapture)
            block()
        } finally {
            System.out.flush()
            System.setOut(previousOut)
            outCapture.stop()
            System.err.flush()
            System.setErr(previousErr)
            errCapture.stop()
        }
    } catch (error: Throwable) {
        onError(ErrorWithCapturedOutput(error, outCapture.captureOutput.toString(), errCapture.captureOutput.toString()))
    }
}


/**
 * Captures output for the current thread, forward output to the original stream for other threads.
 */
private
class ThreadLocalCapturePrintStream(originalOutput: PrintStream) : PrintStream(originalOutput) {

    val captureOutput = ByteArrayOutputStream()

    private
    var isCapturing: ThreadLocal<Boolean>? = ThreadLocal.withInitial { false }

    init {
        isCapturing!!.set(true)
    }

    override fun write(buf: ByteArray, off: Int, len: Int) = safely {
        if (doCapture) captureOutput.write(buf, off, len)
        else super.write(buf, off, len)
    }

    override fun write(b: Int) = safely {
        if (doCapture) captureOutput.write(b)
        else super.write(b)
    }

    override fun flush() = safely {
        captureOutput.flush()
        super.flush()
    }

    fun stop() = safely {
        isCapturing!!.remove()
        // Give a chance for other threads' weak references to the local to be GCed if any
        isCapturing = null
    }

    private
    fun safely(block: () -> Unit) =
        try {
            synchronized(this, block)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
        }

    private
    val doCapture: Boolean
        get() = isCapturing != null && isCapturing!!.get()
}


private
data class ErrorWithCapturedOutput(val error: Throwable, val stdout: String, val stderr: String)
