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
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.plugins.dsl.KotlinDslPluginOptions
import org.gradle.kotlin.dsl.precompile.v1.PrecompiledPluginsBlock
import org.gradle.kotlin.dsl.provider.PrecompiledScriptPluginsSupport
import org.gradle.kotlin.dsl.provider.gradleKotlinDslJarsOf
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin


/**
 * Exposes `*.gradle.kts` scripts from regular Kotlin source-sets as binary Gradle plugins.
 *
 * @see PrecompiledScriptPluginsSupport
 */
abstract class PrecompiledScriptPlugins : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        apply<KotlinBaseApiPlugin>()
        apply<ScriptingGradleSubplugin>()
        plugins.withType(KotlinBaseApiPlugin::class.java) { kotlinBaseApiPlugin ->

            val target = Target(project)

            registerCompileKotlinPluginsBlocks(kotlinBaseApiPlugin)

            if (serviceOf<PrecompiledScriptPluginsSupport>().enableOn(target)) {

                dependencies {
                    "kotlinCompilerPluginClasspath"(gradleKotlinDslJarsOf(project))
                    "kotlinCompilerPluginClasspath"(gradleApi())
                }
            }
        }
    }

    private fun Project.registerCompileKotlinPluginsBlocks(
        kotlinBaseApiPlugin: KotlinBaseApiPlugin
    ) {
        val taskName = "compilePluginsBlocks"
        val pluginDependencyScope = configurations.dependencyScope("${taskName}PluginClasspath")
        val pluginClasspath = configurations.resolvable("${taskName}PluginClasspathElements") {
            it.extendsFrom(pluginDependencyScope.get())
        }
        dependencies {
            pluginDependencyScope.name(kotlin("scripting-compiler-embeddable"))
        }

        kotlinBaseApiPlugin.registerKotlinJvmCompileTask(
            taskName = taskName,
            compilerOptions = kotlinBaseApiPlugin.createCompilerJvmOptions(),
            explicitApiMode = provider { ExplicitApiMode.Disabled },
        ).configure { task ->
            task.enabled = false
            task.multiPlatformEnabled.set(false)
            task.libraries.from(sourceSets["main"].compileClasspath)
            task.pluginClasspath.from(pluginClasspath.get())
            task.compilerOptions.moduleName.set("gradle-kotlin-dsl-plugins-blocks")
            task.compilerOptions.freeCompilerArgs.addAll(listOf("-script-templates", PrecompiledPluginsBlock::class.qualifiedName!!))
        }
    }

    private
    class Target(override val project: Project) : PrecompiledScriptPluginsSupport.Target {

        override val kotlinSourceDirectorySet: SourceDirectorySet
            get() = project.sourceSets["main"].kotlin
    }
}


val Project.kotlinDslPluginOptions: KotlinDslPluginOptions
    get() = extensions.getByType()


private
val Project.sourceSets: SourceSetContainer
    get() = extensions.getByType()


private
val SourceSet.kotlin: SourceDirectorySet
    get() = extensions.getByName("kotlin") as SourceDirectorySet
