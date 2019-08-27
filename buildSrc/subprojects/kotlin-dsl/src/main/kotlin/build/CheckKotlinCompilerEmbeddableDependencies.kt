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

    @OutputFile
    val receiptFile = project.buildDir.resolve("receipts/$name/receipt.txt")

    @TaskAction
    @Suppress("unused")
    fun check() {
        receiptFile.delete()
        val currentFiles = current.files.map { it.name }.sorted()
        val expectedFiles = expected.files.map { it.name }.filterNot { it.startsWith("kotlin-compiler-embeddable-") }.sorted()
        require(currentFiles == expectedFiles) {
            var message = "$path dependencies to kotlin-compiler-embeddable dependencies are wrong\n\nexpected:\n\n"
            message += expectedFiles.joinToString(separator = "\n", postfix = "\n\ngot:\n\n") { "  $it" }
            message += currentFiles.joinToString(separator = "\n", postfix = "\n\n") { "  $it" }
            message += "Please fix dependency declarations in ${project.buildFile.relativeTo(project.rootDir)}"
            message
        }
        receiptFile.apply {
            parentFile.mkdirs()
            writeText("OK")
        }
    }
}
