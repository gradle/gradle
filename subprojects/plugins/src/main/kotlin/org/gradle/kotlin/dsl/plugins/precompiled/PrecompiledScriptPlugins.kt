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
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

import org.gradle.kotlin.dsl.*

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

import java.io.File


/*
 * Exposes `*.gradle.kts` scripts from regular Kotlin source-sets as binary Gradle plugins.
 */
class PrecompiledScriptPlugins : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        enableScriptCompilation()

        plugins.withType<JavaGradlePluginPlugin> {
            exposeScriptsAsGradlePlugins()
        }
    }
}


private
fun Project.enableScriptCompilation() {

    dependencies {
        "kotlinCompilerPluginClasspath"(gradleKotlinDslJarsOf(project))
        "kotlinCompilerPluginClasspath"(gradleApi())
    }

    tasks.named<KotlinCompile>("compileKotlin") {
        kotlinOptions {
            freeCompilerArgs += listOf(
                "-script-templates", scriptTemplates,
                // Propagate implicit imports and other settings
                "-Xscript-resolver-environment=${resolverEnvironment()}"
            )
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

    val scriptPlugins =
        scriptSourceFiles.map(::ScriptPlugin)

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
fun Project.generatePluginAdaptersFor(scriptPlugins: List<ScriptPlugin>, scriptSourceFiles: FileTree) {

    val generatedSourcesDir = layout.buildDirectory.dir("generated-sources/kotlin-dsl-plugins/kotlin")
    sourceSets["main"].kotlin.srcDir(generatedSourcesDir)

    val generateScriptPluginAdapters by tasks.registering {
        inputs.files(scriptSourceFiles)
        outputs.dir(generatedSourcesDir)
        doLast {
            val outputDir = generatedSourcesDir.get().asFile
            for (scriptPlugin in scriptPlugins) {
                scriptPlugin.writeScriptPluginAdapterTo(outputDir)
            }
        }
    }

    tasks.named("compileKotlin") {
        it.dependsOn(generateScriptPluginAdapters)
    }
}


internal
fun ScriptPlugin.writeScriptPluginAdapterTo(outputDir: File) {

    val (packageDir, packageDeclaration) =
        packageName?.let { packageName ->
            packageDir(outputDir, packageName) to "package $packageName"
        } ?: outputDir to ""

    val outputFile =
        packageDir.resolve("$simplePluginAdapterClassName.kt")

    outputFile.writeText("""

        $packageDeclaration

        /**
         * Precompiled [$scriptFileName][$compiledScriptTypeName] script plugin.
         *
         * @see $compiledScriptTypeName
         */
        class $simplePluginAdapterClassName : org.gradle.api.Plugin<$targetType> {
            override fun apply(target: $targetType) {
                try {
                    Class
                        .forName("$compiledScriptTypeName")
                        .getDeclaredConstructor($targetType::class.java)
                        .newInstance(target)
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    throw e.targetException
                }
            }
        }

    """.replaceIndent().trim() + "\n")
}


private
fun packageDir(outputDir: File, packageName: String) =
    outputDir.mkdir(packageName.replace('.', '/'))


private
fun File.mkdir(relative: String) =
    resolve(relative).apply { mkdirs() }


private
val Project.sourceSets
    get() = project.the<SourceSetContainer>()


private
val SourceSet.kotlin: SourceDirectorySet
    get() = withConvention(KotlinSourceSet::class) { kotlin }
