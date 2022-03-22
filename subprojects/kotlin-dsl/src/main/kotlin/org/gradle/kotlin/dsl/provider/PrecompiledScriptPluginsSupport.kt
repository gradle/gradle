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

package org.gradle.kotlin.dsl.provider

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.TaskProvider

import java.io.File
import java.util.function.Consumer


interface PrecompiledScriptPluginsSupport {

    @Deprecated("Use enableOn(Target)")
    fun enableOn(
        project: Project,
        kotlinSourceDirectorySet: SourceDirectorySet,
        kotlinCompileTask: TaskProvider<out Task>,
        kotlinCompilerArgsConsumer: Consumer<List<String>>
    )

    fun enableOn(target: Target): Boolean

    fun collectScriptPluginFilesOf(project: Project): List<File>

    interface Target {
        val project: Project
        val kotlinSourceDirectorySet: SourceDirectorySet
        @Deprecated("No longer used.")
        val kotlinCompileTask: TaskProvider<out Task>
        @Deprecated("No longer used.")
        fun applyKotlinCompilerArgs(args: List<String>)
    }
}
