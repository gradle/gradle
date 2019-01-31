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

package build

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.io.File


/**
 * Used to validate that :kotlinCompilerEmbeddable dependencies are aligned with the original dependencies.
 */
open class CheckKotlinCompilerEmbeddableDependencies : DefaultTask() {

    /**
     * Current classpath to be validated.
     */
    @Classpath
    val current = project.files()

    /**
     * Expected classpath for a `kotlin-compiler-embeddable` dependency.
     */
    @Classpath
    val expected = project.files()

    val outputFile: File
        @OutputFile get() = temporaryDir.resolve("output")

    @TaskAction
    @Suppress("unused")
    fun check() {
        outputFile.delete()
        val currentFiles = current.files.sorted()
        val expectedFiles = expected.files.filterNot { it.name.startsWith("kotlin-compiler-embeddable-") }.sorted()
        require(currentFiles == expectedFiles) {
            var message = "$path dependencies to kotlin-compiler-embeddable dependencies are wrong\n\nexpected:\n\n"
            message += expected.joinToString(separator = "\n", postfix = "\n\ngot:\n\n") { "  ${it.name}" }
            message += current.joinToString(separator = "\n", postfix = "\n\n") { "  ${it.name}" }
            message += "Please fix dependency declarations in ${project.buildFile.relativeTo(project.rootDir)}"
            message
        }
        outputFile.writeText("OK")
    }
}
