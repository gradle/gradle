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

package org.gradle.gradlebuild.packaging

import build.extractParameterNamesIndexFrom

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import org.gradle.build.ReproduciblePropertiesWriter

import java.io.File


@CacheableTask
open class ParameterNamesResourceTask : DefaultTask() {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    val sources = project.files()

    @OutputFile
    val destinationFile = project.objects.fileProperty()

    @TaskAction
    fun generate() =
        generateParameterNamesResource(
            sources.files,
            destinationFile.get().asFile
        )

    private
    fun generateParameterNamesResource(sources: Set<File>, destinationFile: File) {
        val index = extractParameterNamesIndexFrom(sources)
        destinationFile.parentFile.mkdirs()
        ReproduciblePropertiesWriter.store(index, destinationFile)
    }
}
