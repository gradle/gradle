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

package org.gradle.gradlebuild

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction


/**
 * generates a file (outputFile) with a list of file paths from classpath
 */
open class BuildClassPath : DefaultTask() {
    @InputFiles
    val classpath = project.files()

    @OutputFile
    val outputFile = newOutputFile()

    @TaskAction
    fun buildClasspath() =
        outputFile.get().asFile.printWriter().use { wrt ->
            classpath.asFileTree.files.forEach {
                wrt.println(it.absolutePath)
            }
        }
}
