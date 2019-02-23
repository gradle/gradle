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

package org.gradle.kotlin.dsl.plugins.precompiled.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.internal.AbstractTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugin

import java.io.File


/**
 * Generates plugin spec builders for the _Project_ script plugins defined in the current module.
 */
@CacheableTask
open class GenerateInternalPluginSpecBuilders : DefaultTask() {

    @get:Internal
    internal
    lateinit var plugins: List<PrecompiledScriptPlugin>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @Suppress("unused")
    internal
    val scriptFiles: Set<File>
        get() = scriptPluginFilesOf(plugins)

    @get:OutputDirectory
    var sourceCodeOutputDir = directoryProperty()

    @TaskAction
    fun generate() {
        // TODO()
    }
}


internal
fun AbstractTask.directoryProperty() = project.objects.directoryProperty()


internal
fun AbstractTask.sourceDirectorySet(name: String, displayName: String) = project.objects.sourceDirectorySet(name, displayName)
