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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider

import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.plugins.precompiled.tasks.CompilePrecompiledScriptPluginPlugins
import org.gradle.kotlin.dsl.plugins.precompiled.tasks.ExtractPrecompiledScriptPluginPlugins
import org.gradle.kotlin.dsl.plugins.precompiled.tasks.GenerateExternalPluginSpecBuilders
import org.gradle.kotlin.dsl.plugins.precompiled.tasks.GenerateInternalPluginSpecBuilders
import org.gradle.kotlin.dsl.plugins.precompiled.tasks.GeneratePrecompiledScriptPluginAccessors
import org.gradle.kotlin.dsl.plugins.precompiled.tasks.GenerateScriptPluginAdapters

import org.gradle.kotlin.dsl.precompile.PrecompiledInitScript
import org.gradle.kotlin.dsl.precompile.PrecompiledProjectScript
import org.gradle.kotlin.dsl.precompile.PrecompiledScriptDependenciesResolver
import org.gradle.kotlin.dsl.precompile.PrecompiledSettingsScript

import org.gradle.kotlin.dsl.provider.gradleKotlinDslJarsOf

import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.serviceOf

import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin

import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


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
 *  - [GenerateInternalPluginSpecBuilders] - generates plugin spec builders for the _Project_ script plugins defined
 *  in the current module
 *  - [GenerateExternalPluginSpecBuilders] - generates plugin spec builders for the plugins in the compile classpath
 *  - [CompilePrecompiledScriptPluginPlugins] - compiles the extracted `plugins` blocks along with the internal
 *  and external plugin spec builders
 *  - [GeneratePrecompiledScriptPluginAccessors] - uses the compiled `plugins` block of each precompiled script plugin
 *  to compute its [HashedProjectSchema] and emit the corresponding type-safe accessors
 *
 * ## Todo
 *  - DONE type-safe plugin spec accessors for plugins in the precompiled script plugin classpath
 *  - [ ] limit the set of type-safe accessors visible to a precompiled script plugin to
 *        those provided by the plugins in its `plugins` block
 *  - [ ] emit help message when a precompiled script plugin includes a version in its `plugins` block
 *  - [ ] validate plugin ids against declared plugin dependencies (that comes for free)
 */
class PrecompiledScriptPlugins : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        val scriptPlugins = collectScriptPlugins()

        enableScriptCompilationOf(scriptPlugins)

        plugins.withType<JavaGradlePluginPlugin> {
            exposeScriptsAsGradlePlugins(scriptPlugins)
        }
    }
}


private
fun Project.enableScriptCompilationOf(scriptPlugins: List<PrecompiledScriptPlugin>) {

    dependencies {
        "kotlinCompilerPluginClasspath"(gradleKotlinDslJarsOf(project))
        "kotlinCompilerPluginClasspath"(gradleApi())
    }

    val extractedPluginsBlocks = buildDir("kotlin-dsl/plugins-blocks/extracted")

    val compiledPluginsBlocks = buildDir("kotlin-dsl/plugins-blocks/compiled")

    val generatedMetadata = buildDir("precompiled-script-plugins")

    val compileClasspath = compileClasspath()

    tasks {

        val extractPrecompiledScriptPluginPlugins by registering(ExtractPrecompiledScriptPluginPlugins::class) {
            plugins = scriptPlugins
            outputDir.set(extractedPluginsBlocks)
        }

        val (generateInternalPluginSpecBuilders, internalPluginSpecBuilders) =
            codeGenerationTask<GenerateInternalPluginSpecBuilders>(
                "internal-plugin-spec-builders",
                "generateInternalPluginSpecBuilders"
            ) {
                plugins = scriptPlugins
                sourceCodeOutputDir.set(it)
            }

        val (generateExternalPluginSpecBuilders, externalPluginSpecBuilders) =
            codeGenerationTask<GenerateExternalPluginSpecBuilders>(
                "external-plugin-spec-builders",
                "generateExternalPluginSpecBuilders"
            ) {
                classPathFiles = compileClasspath
                sourceCodeOutputDir.set(it)
            }

        val compilePluginsBlocks by registering(CompilePrecompiledScriptPluginPlugins::class) {

            dependsOn(extractPrecompiledScriptPluginPlugins)
            sourceDir(extractedPluginsBlocks)

            dependsOn(generateInternalPluginSpecBuilders)
            sourceDir(internalPluginSpecBuilders)

            dependsOn(generateExternalPluginSpecBuilders)
            sourceDir(externalPluginSpecBuilders)

            classPathFiles = compileClasspath
            outputDir.set(compiledPluginsBlocks)
        }

        val (generatePrecompiledScriptPluginAccessors, generatedAccessors) =
            codeGenerationTask<GeneratePrecompiledScriptPluginAccessors>(
                "accessors",
                "generatePrecompiledScriptPluginAccessors"
            ) {
                dependsOn(compilePluginsBlocks)
                classPathFiles = compileClasspath
                sourceCodeOutputDir.set(it)
                metadataOutputDir.set(generatedMetadata)
                compiledPluginsBlocksDir.set(compiledPluginsBlocks)
                plugins = scriptPlugins
            }

        val configurePrecompiledScriptPluginImports by registering {
            inputs.files(
                project.files(generatedMetadata).builtBy(generatePrecompiledScriptPluginAccessors)
            )
        }

        val compileKotlin by existing(KotlinCompile::class) {
            dependsOn(configurePrecompiledScriptPluginImports)
        }

        configurePrecompiledScriptPluginImports {
            doLast {

                val metadataDir = generatedMetadata.get().asFile
                require(metadataDir.isDirectory)

                val precompiledScriptPluginImports =
                    metadataDir.listFiles().map {
                        it.name to it.readLines()
                    }

                val resolverEnvironment = resolverEnvironmentStringFor(
                    listOf(
                        PrecompiledScriptDependenciesResolver.EnvironmentProperties.kotlinDslImplicitImports to implicitImports()
                    ) + precompiledScriptPluginImports
                )

                compileKotlin.get().apply {
                    kotlinOptions {
                        freeCompilerArgs += listOf(
                            "-script-templates", scriptTemplates,
                            // Propagate implicit imports and other settings
                            "-Xscript-resolver-environment=$resolverEnvironment"
                        )
                    }
                }
            }
        }
    }
}


private
fun Project.compileClasspath() = sourceSets["main"].compileClasspath


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
fun resolverEnvironmentStringFor(properties: Iterable<Pair<String, List<String>>>): String =
    properties.joinToString(separator = ",") { (key, values) ->
        "$key=\"${values.joinToString(":")}\""
    }


internal
fun Project.implicitImports(): List<String> =
    serviceOf<ImplicitImports>().list


private
fun Project.exposeScriptsAsGradlePlugins(scriptPlugins: List<PrecompiledScriptPlugin>) {

    declareScriptPlugins(scriptPlugins)

    generatePluginAdaptersFor(scriptPlugins)
}


private
fun Project.collectScriptPlugins(): List<PrecompiledScriptPlugin> =
    pluginSourceSet.allSource.matching {
        it.include("**/*.gradle.kts")
    }.map(::PrecompiledScriptPlugin)


private
val Project.pluginSourceSet
    get() = gradlePlugin.pluginSourceSet


private
val Project.gradlePlugin
    get() = the<GradlePluginDevelopmentExtension>()


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
fun Project.generatePluginAdaptersFor(scriptPlugins: List<PrecompiledScriptPlugin>) {

    codeGenerationTask<GenerateScriptPluginAdapters>("plugins", "generateScriptPluginAdapters") {
        plugins = scriptPlugins
        outputDirectory.set(it)
    }
}


private
inline fun <reified T : Task> Project.codeGenerationTask(
    purpose: String,
    taskName: String,
    noinline configure: T.(Provider<Directory>) -> Unit
) = buildDir("generated-sources/kotlin-dsl-$purpose/kotlin").let { outputDir ->
    val task = tasks.register(taskName, T::class.java) {
        it.configure(outputDir)
    }
    sourceSets["main"].kotlin.srcDir(files(outputDir).builtBy(task))
    task to outputDir
}


private
fun Project.generatedSourceDirFor(purpose: String): Provider<Directory> =
    buildDir("generated-sources/kotlin-dsl-$purpose/kotlin").also {
        sourceSets["main"].kotlin.srcDir(it)
    }


private
fun Project.buildDir(path: String) = layout.buildDirectory.dir(path)


private
val Project.sourceSets
    get() = project.the<SourceSetContainer>()


private
val SourceSet.kotlin: SourceDirectorySet
    get() = withConvention(KotlinSourceSet::class) { kotlin }
