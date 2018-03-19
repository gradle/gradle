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
import org.gradle.api.file.FileTree
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet

import org.gradle.kotlin.dsl.*

import org.gradle.kotlin.dsl.precompile.PrecompiledInitScript
import org.gradle.kotlin.dsl.precompile.PrecompiledProjectScript
import org.gradle.kotlin.dsl.precompile.PrecompiledScriptDependenciesResolver
import org.gradle.kotlin.dsl.precompile.PrecompiledSettingsScript

import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.serviceOf

import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin

import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import java.io.File


/*
 * Exposes `*.gradle.kts` scripts from regular Kotlin source-sets as binary Gradle plugins.
 */
open class PrecompiledScriptPlugins : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        enableScriptCompilation()

        plugins.withType<JavaGradlePluginPlugin> {
            exposeScriptsAsGradlePlugins()
        }
    }
}


private
fun Project.enableScriptCompilation() {

    afterEvaluate {

        tasks {

            "compileKotlin"(KotlinCompile::class) {
                kotlinOptions {
                    freeCompilerArgs += listOf(
                        "-script-templates", scriptTemplates,
                        // Propagate implicit imports and other settings
                        "-Xscript-resolver-environment=${resolverEnvironment()}"
                    )
                }
            }
        }
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
fun Project.resolverEnvironment() =
    (PrecompiledScriptDependenciesResolver.EnvironmentProperties.kotlinDslImplicitImports
        + "=\"" + implicitImports().joinToString(separator = ":") + "\"")


private
fun Project.implicitImports(): List<String> =
    serviceOf<ImplicitImports>().list


private
fun Project.exposeScriptsAsGradlePlugins() {

    val scriptSourceFiles = pluginSourceSet.allSource.matching {
        it.include("**/*.gradle.kts")
    }

    val scriptPlugins by lazy {
        scriptSourceFiles.map(::ScriptPlugin)
    }

    declareScriptPlugins(scriptPlugins)

    generatePluginAdaptersFor(scriptPlugins, scriptSourceFiles)
}


private
val Project.pluginSourceSet
    get() = gradlePlugin.pluginSourceSet


private
val Project.gradlePlugin
    get() = the<GradlePluginDevelopmentExtension>()


private
fun Project.declareScriptPlugins(scriptPlugins: List<ScriptPlugin>) {

    tasks {

        val inferGradlePluginDeclarations by creating {
            doLast {
                project.configure<GradlePluginDevelopmentExtension> {
                    for (scriptPlugin in scriptPlugins) {
                        plugins.create(scriptPlugin.id) {
                            it.id = scriptPlugin.id
                            it.implementationClass = scriptPlugin.implementationClass
                        }
                    }
                }
            }
        }

        getByName("pluginDescriptors") {
            it.dependsOn(inferGradlePluginDeclarations)
        }
    }
}


private
fun Project.generatePluginAdaptersFor(scriptPlugins: List<ScriptPlugin>, scriptSourceFiles: FileTree) {

    tasks {

        val generatedSourcesDir = layout.buildDirectory.dir("generated-sources/kotlin-dsl-plugins/kotlin")
        val sourceSet = sourceSets["main"]
        sourceSet.kotlin.srcDir(generatedSourcesDir)

        val generateScriptPluginAdapters by creating {
            inputs.files(scriptSourceFiles)
            outputs.dir(generatedSourcesDir)
            doLast {
                for (scriptPlugin in scriptPlugins) {
                    scriptPlugin.writeScriptPluginAdapterTo(generatedSourcesDir.get().asFile)
                }
            }
        }

        getByName("compileKotlin") {
            it.dependsOn(generateScriptPluginAdapters)
        }
    }
}


internal
fun ScriptPlugin.writeScriptPluginAdapterTo(outputDir: File) =
    File(outputDir, "$implementationClass.kt").writeText(
        """
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class $implementationClass : Plugin<Project> {
                override fun apply(target: Project) {
                    Class
                        .forName("$compiledScriptTypeName")
                        .getDeclaredConstructor(Project::class.java)
                        .newInstance(target)
                }
            }
        """.replaceIndent())


private
val Project.sourceSets
    get() = project.the<JavaPluginConvention>().sourceSets


private
val SourceSet.kotlin: SourceDirectorySet
    get() = withConvention(KotlinSourceSet::class) { kotlin }
