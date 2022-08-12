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
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider

import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.provider.PrecompiledScriptPluginsSupport
import org.gradle.kotlin.dsl.provider.gradleKotlinDslJarsOf
import org.gradle.kotlin.dsl.support.serviceOf

import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet


/**
 * Exposes `*.gradle.kts` scripts from regular Kotlin source-sets as binary Gradle plugins.
 *
 * @see PrecompiledScriptPluginsSupport
 */
class PrecompiledScriptPlugins : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        if (serviceOf<PrecompiledScriptPluginsSupport>().enableOn(Target(project))) {

            dependencies {
                "kotlinCompilerPluginClasspath"(gradleKotlinDslJarsOf(project))
                "kotlinCompilerPluginClasspath"(gradleApi())
            }
        }
    }

    private
    class Target(override val project: Project) : PrecompiledScriptPluginsSupport.Target {

        override val kotlinSourceDirectorySet: SourceDirectorySet
            get() = project.sourceSets["main"].kotlin

        @Deprecated("No longer used.", ReplaceWith(""))
        override val kotlinCompileTask: TaskProvider<out Task>
            get() = error("No longer used")

        @Deprecated("No longer used.", ReplaceWith(""))
        override fun applyKotlinCompilerArgs(args: List<String>) =
            error("No longer used.")
    }
}


private
val Project.sourceSets
    get() = project.the<SourceSetContainer>()


private
val SourceSet.kotlin: SourceDirectorySet
    get() = @Suppress("deprecation") withConvention(KotlinSourceSet::class) { kotlin }
