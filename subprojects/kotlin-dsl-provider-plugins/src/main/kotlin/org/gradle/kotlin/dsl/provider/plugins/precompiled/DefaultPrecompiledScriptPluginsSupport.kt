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
package org.gradle.kotlin.dsl.provider.plugins.precompiled


import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.plugins.DefaultPluginManager
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.classpath.Instrumented.fileCollectionObserved
import org.gradle.internal.deprecation.Documentation
import org.gradle.internal.fingerprint.classpath.ClasspathFingerprinter
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.precompile.v1.PrecompiledInitScript
import org.gradle.kotlin.dsl.precompile.v1.PrecompiledProjectScript
import org.gradle.kotlin.dsl.precompile.v1.PrecompiledSettingsScript
import org.gradle.kotlin.dsl.provider.PrecompiledScriptPluginsSupport
import org.gradle.kotlin.dsl.provider.inClassPathMode
import org.gradle.kotlin.dsl.provider.plugins.precompiled.DefaultPrecompiledScriptPluginsSupport.Companion.PRECOMPILED_SCRIPT_MANUAL
import org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks.CompilePrecompiledScriptPluginPlugins
import org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks.ConfigurePrecompiledScriptDependenciesResolver
import org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks.ExtractPrecompiledScriptPluginPlugins
import org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks.GenerateExternalPluginSpecBuilders
import org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks.GeneratePrecompiledScriptPluginAccessors
import org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks.GenerateScriptPluginAdapters
import org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks.HashedProjectSchema
import org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks.resolverEnvironmentStringFor
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
import java.io.File
import java.util.function.Consumer
import javax.inject.Inject


/**
 * Exposes `*.gradle.kts` scripts from regular Kotlin source-sets as binary Gradle plugins.
 *
 * ## Defining the plugin target
 *
 * Precompiled script plugins can target one of the following Gradle model types, [Gradle], [Settings] or [Project].
 *
 * The target of a given script plugin is defined via its file name suffix in the following manner:
 *  - the `.init.gradle.kts` file name suffix defines a [Gradle] script plugin
 *  - the `.settings.gradle.kts` file name suffix defines a [Settings] script plugin
 *  - and finally, the simpler `.gradle.kts` file name suffix  defines a [Project] script plugin
 *
 * ## Defining the plugin id
 *
 * The Gradle plugin id for a precompiled script plugin is defined via its file name
 * plus optional package declaration in the following manner:
 *  - for a script without a package declaration, the plugin id is simply the file name without the
 *  related plugin target suffix (see above)
 *  - for a script containing a package declaration, the plugin id is the declared package name dot the file name without the
 *  related plugin target suffix (see above)
 *
 * For a concrete example, take the definition of a precompiled [Project] script plugin id of
 * `my.project.plugin`. Given the two rules above, there are two conventional ways to do it:
 *  * by naming the script `my.project.plugin.gradle.kts` and including no package declaration
 *  * by naming the script `plugin.gradle.kts` and including a package declaration of `my.project`:
 *    ```kotlin
 *    // plugin.gradle.kts
 *    package my.project
 *
 *    // ... plugin implementation ...
 *    ```
 * ## Applying plugins
 * Precompiled script plugins can apply plugins much in the same way as regular scripts can, using one
 * of the many `apply` method overloads or, in the case of [Project] scripts, via the `plugins` block.
 *
 * And just as regular [Project] scripts can take advantage of
 * [type-safe model accessors](https://docs.gradle.org/current/userguide/kotlin_dsl.html#type-safe-accessors)
 * to model elements contributed by plugins applied via the `plugins` block, so can precompiled [Project] script plugins:
 * ```kotlin
 * // java7-project.gradle.kts
 *
 * plugins {
 *     java
 * }
 *
 * java { // type-safe model accessor to the `java` extension contributed by the `java` plugin
 *     sourceCompatibility = JavaVersion.VERSION_1_7
 *     targetCompatibility = JavaVersion.VERSION_1_7
 * }
 * ```
 * ## Implementation Notes
 * External plugin dependencies are declared as regular artifact dependencies but a more
 * semantic preserving model could be introduced in the future.
 *
 * ### Type-safe accessors
 * The process of generating type-safe accessors for precompiled script plugins is carried out by the
 * following tasks:
 *  - [ExtractPrecompiledScriptPluginPlugins] - extracts the `plugins` block of every precompiled script plugin and
 *  saves it to a file with the same name in the output directory
 *  - [GenerateExternalPluginSpecBuilders] - generates plugin spec builders for the plugins in the compile classpath
 *  - [CompilePrecompiledScriptPluginPlugins] - compiles the extracted `plugins` blocks along with the internal
 *  and external plugin spec builders
 *  - [GeneratePrecompiledScriptPluginAccessors] - uses the compiled `plugins` block of each precompiled script plugin
 *  to compute its [HashedProjectSchema] and emit the corresponding type-safe accessors
 */
class DefaultPrecompiledScriptPluginsSupport : PrecompiledScriptPluginsSupport {

    companion object {
        val PRECOMPILED_SCRIPT_MANUAL = Documentation.userManual("custom_plugins", "sec:precompiled_plugins")!!
    }

    override fun enableOn(target: PrecompiledScriptPluginsSupport.Target): Boolean = target.project.run {

        val scriptPluginFiles = collectScriptPluginFiles()
        if (scriptPluginFiles.isEmpty()) {
            return false
        }

        val scriptPlugins = scriptPluginFiles.map(::PrecompiledScriptPlugin)
        enableScriptCompilationOf(
            scriptPlugins,
            target.kotlinSourceDirectorySet
        )

        plugins.withType<JavaGradlePluginPlugin> {
            exposeScriptsAsGradlePlugins(
                scriptPlugins,
                target.kotlinSourceDirectorySet
            )
        }
        return true
    }

    @Deprecated("Use enableOn(Target)")
    override fun enableOn(
        project: Project,
        kotlinSourceDirectorySet: SourceDirectorySet,
        kotlinCompileTask: TaskProvider<out Task>,
        kotlinCompilerArgsConsumer: Consumer<List<String>>
    ) {
        enableOn(object : PrecompiledScriptPluginsSupport.Target {
            override val project
                get() = project

            override val kotlinSourceDirectorySet
                get() = kotlinSourceDirectorySet

            @Deprecated("No longer used.", ReplaceWith(""))
            override val kotlinCompileTask
                get() = error("No longer used.")

            @Deprecated("No longer used.", ReplaceWith(""))
            override fun applyKotlinCompilerArgs(args: List<String>) =
                error("No longer used.")
        })
    }

    override fun collectScriptPluginFilesOf(project: Project): List<File> =
        project.collectScriptPluginFiles().toList()
}


private
fun Project.enableScriptCompilationOf(
    scriptPlugins: List<PrecompiledScriptPlugin>,
    kotlinSourceDirectorySet: SourceDirectorySet
) {

    val extractedPluginsBlocks = buildDir("kotlin-dsl/plugins-blocks/extracted")

    val compiledPluginsBlocks = buildDir("kotlin-dsl/plugins-blocks/compiled")

    val accessorsMetadata = buildDir("kotlin-dsl/precompiled-script-plugins-metadata/accessors")

    val pluginSpecBuildersMetadata = buildDir("kotlin-dsl/precompiled-script-plugins-metadata/plugin-spec-builders")

    val compileClasspath: FileCollection = sourceSets["main"].compileClasspath

    tasks {

        val extractPrecompiledScriptPluginPlugins by registering(ExtractPrecompiledScriptPluginPlugins::class) {
            plugins = scriptPlugins
            outputDir.set(extractedPluginsBlocks)
        }

        val (generateExternalPluginSpecBuilders, externalPluginSpecBuilders) =
            codeGenerationTask<GenerateExternalPluginSpecBuilders>(
                "external-plugin-spec-builders",
                "generateExternalPluginSpecBuilders",
                kotlinSourceDirectorySet
            ) {
                classPathFiles.from(compileClasspath)
                sourceCodeOutputDir.set(it)
                metadataOutputDir.set(pluginSpecBuildersMetadata)
            }

        val compilePluginsBlocks by registering(CompilePrecompiledScriptPluginPlugins::class) {

            dependsOn(extractPrecompiledScriptPluginPlugins)
            sourceDir(extractedPluginsBlocks)

            dependsOn(generateExternalPluginSpecBuilders)
            sourceDir(externalPluginSpecBuilders)

            classPathFiles.from(compileClasspath)
            outputDir.set(compiledPluginsBlocks)
        }

        val (generatePrecompiledScriptPluginAccessors, _) =
            codeGenerationTask<GeneratePrecompiledScriptPluginAccessors>(
                "accessors",
                "generatePrecompiledScriptPluginAccessors",
                kotlinSourceDirectorySet
            ) {
                dependsOn(compilePluginsBlocks)
                classPathFiles.from(compileClasspath)
                runtimeClassPathFiles.from(configurations["runtimeClasspath"])
                sourceCodeOutputDir.set(it)
                metadataOutputDir.set(accessorsMetadata)
                compiledPluginsBlocksDir.set(compiledPluginsBlocks)
                strict.set(
                    providers
                        .systemProperty("org.gradle.kotlin.dsl.precompiled.accessors.strict")
                        .map(java.lang.Boolean::parseBoolean)
                        .orElse(false)
                )
                plugins = scriptPlugins
            }

        compileKotlin {

            dependsOn(generatePrecompiledScriptPluginAccessors)

            inputs.files(compileClasspath)
                .withNormalizer(ClasspathNormalizer::class.java)
                .withPropertyName("compileClasspath")
            inputs.dir(accessorsMetadata)
                .withPathSensitivity(PathSensitivity.RELATIVE)
                .ignoreEmptyDirectories()
                .withPropertyName("accessorsMetadata")
            inputs.property("kotlinDslScriptTemplates", scriptTemplates)

            configureScriptResolverEnvironmentOnDoFirst(
                objects,
                serviceOf(),
                compileClasspath,
                accessorsMetadata
            )
        }

        if (inClassPathMode()) {

            val configurePrecompiledScriptDependenciesResolver by registering(ConfigurePrecompiledScriptDependenciesResolver::class) {
                dependsOn(generatePrecompiledScriptPluginAccessors)
                metadataDir.set(accessorsMetadata)
                classPathFiles.from(compileClasspath)
                onConfigure { resolverEnvironment ->
                    objects.withInstance<TaskContainerScope>() {
                        configureScriptResolverEnvironment(resolverEnvironment)
                    }
                }
            }

            registerBuildScriptModelTask(
                configurePrecompiledScriptDependenciesResolver
            )
        }
    }
}


private
fun Task.configureScriptResolverEnvironmentOnDoFirst(
    objects: ObjectFactory,
    implicitImports: ImplicitImports,
    compileClasspath: FileCollection,
    accessorsMetadata: Provider<Directory>
) {
    doFirst {
        objects.withInstance<ResolverEnvironmentScope>() {
            configureScriptResolverEnvironment(
                resolverEnvironmentStringFor(
                    implicitImports,
                    classpathFingerprinter,
                    compileClasspath,
                    accessorsMetadata.get().asFile
                )
            )
        }
    }
}


private
inline fun <reified T> ObjectFactory.withInstance(block: T.() -> Unit) {
    with(newInstance(), block)
}


private
fun TaskContainerScope.configureScriptResolverEnvironment(resolverEnvironment: String) {
    taskContainer.compileKotlin {
        val scriptCompilerArgs = listOf(
            "-script-templates", scriptTemplates,
            // Propagate implicit imports and other settings
            "-Xscript-resolver-environment=$resolverEnvironment"
        )
        withGroovyBuilder {
            getProperty("kotlinOptions").withGroovyBuilder {
                @Suppress("unchecked_cast")
                val freeCompilerArgs: List<String> = getProperty("freeCompilerArgs") as List<String>
                setProperty("freeCompilerArgs", freeCompilerArgs + scriptCompilerArgs)
            }
        }
    }
}


private
fun TaskContainer.compileKotlin(action: Task.() -> Unit) {
    named("compileKotlin") {
        it.action()
    }
}


private
interface TaskContainerScope {
    @get:Inject
    val taskContainer: TaskContainer
}


private
interface ResolverEnvironmentScope : TaskContainerScope {
    @get:Inject
    val classpathFingerprinter: ClasspathFingerprinter
}


private
fun Project.registerBuildScriptModelTask(
    modelTask: TaskProvider<out Task>
) {
    rootProject.tasks.named(KotlinDslModelsParameters.PREPARATION_TASK_NAME) {
        it.dependsOn(modelTask)
    }
}


private
val scriptTemplates by lazy {
    listOf(
        // treat *.settings.gradle.kts files as Settings scripts
        PrecompiledSettingsScript::class.qualifiedName!!,
        // treat *.init.gradle.kts files as Gradle scripts
        PrecompiledInitScript::class.qualifiedName!!,
        // treat *.gradle.kts files as Project scripts
        PrecompiledProjectScript::class.qualifiedName!!
    ).joinToString(separator = ",")
}


private
fun Project.exposeScriptsAsGradlePlugins(scriptPlugins: List<PrecompiledScriptPlugin>, kotlinSourceDirectorySet: SourceDirectorySet) {

    scriptPlugins.forEach { validateScriptPlugin(it) }

    declareScriptPlugins(scriptPlugins)

    generatePluginAdaptersFor(scriptPlugins, kotlinSourceDirectorySet)
}


private
fun Project.collectScriptPluginFiles(): Set<File> =
    gradlePlugin.pluginSourceSet.allSource.matching {
        it.include("**/*.gradle.kts")
    }.filter {
        it.isFile
    }.also {
        // Declare build configuration input
        fileCollectionObserved(it, "Kotlin DSL")
    }.files


private
val Project.gradlePlugin
    get() = the<GradlePluginDevelopmentExtension>()


private
fun Project.validateScriptPlugin(scriptPlugin: PrecompiledScriptPlugin) {

    if (scriptPlugin.id == DefaultPluginManager.CORE_PLUGIN_NAMESPACE || scriptPlugin.id.startsWith(DefaultPluginManager.CORE_PLUGIN_PREFIX)) {
        throw GradleException(
            String.format(
                "The precompiled plugin (%s) cannot start with '%s' or be in the '%s' package.\n\n%s", this.relativePath(scriptPlugin.scriptFile),
                DefaultPluginManager.CORE_PLUGIN_NAMESPACE, DefaultPluginManager.CORE_PLUGIN_NAMESPACE,
                PRECOMPILED_SCRIPT_MANUAL.consultDocumentationMessage()
            )
        )
    }
    val existingPlugin = plugins.findPlugin(scriptPlugin.id)
    if (existingPlugin != null && existingPlugin.javaClass.getPackage().name.startsWith(DefaultPluginManager.CORE_PLUGIN_PREFIX)) {
        throw GradleException(
            String.format(
                "The precompiled plugin (%s) conflicts with the core plugin '%s'. Rename your plugin.\n\n%s", this.relativePath(scriptPlugin.scriptFile), scriptPlugin.id, PRECOMPILED_SCRIPT_MANUAL.consultDocumentationMessage()
            )
        )
    }
}


private
fun Project.declareScriptPlugins(scriptPlugins: List<PrecompiledScriptPlugin>) {

    configure<GradlePluginDevelopmentExtension> {
        for (scriptPlugin in scriptPlugins) {
            plugins.create(scriptPlugin.id) {
                it.id = scriptPlugin.id
                it.implementationClass = scriptPlugin.implementationClass
            }
        }
    }
}


private
fun Project.generatePluginAdaptersFor(scriptPlugins: List<PrecompiledScriptPlugin>, kotlinSourceDirectorySet: SourceDirectorySet) {

    codeGenerationTask<GenerateScriptPluginAdapters>(
        "plugins",
        "generateScriptPluginAdapters",
        kotlinSourceDirectorySet
    ) {
        plugins = scriptPlugins
        outputDirectory.set(it)
    }
}


private
inline fun <reified T : Task> Project.codeGenerationTask(
    purpose: String,
    taskName: String,
    kotlinSourceDirectorySet: SourceDirectorySet,
    noinline configure: T.(Provider<Directory>) -> Unit
) = buildDir("generated-sources/kotlin-dsl-$purpose/kotlin").let { outputDir ->
    val task = tasks.register(taskName, T::class.java) {
        it.configure(outputDir)
    }
    kotlinSourceDirectorySet.srcDir(files(outputDir).builtBy(task))
    task to outputDir
}


private
fun Project.buildDir(path: String) = layout.buildDirectory.dir(path)


private
val Project.sourceSets
    get() = project.the<SourceSetContainer>()
