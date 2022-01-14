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

package gradlebuild.kotlindsl.compiler.tasks

import gradlebuild.basics.repoRoot
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault


/**
 * Used to validate that :kotlinCompilerEmbeddable dependencies are aligned with the original dependencies.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class CheckKotlinCompilerEmbeddableDependencies : DefaultTask() {

    /**
     * Current classpath to be validated.
     */
    @get:Classpath
    abstract val current: ConfigurableFileCollection

    /**
     * Expected classpath for a `kotlin-compiler-embeddable` dependency.
     */
    @get:Classpath
    abstract val expected: ConfigurableFileCollection

    @OutputFile
    val receiptFile = project.layout.buildDirectory.file("receipts/$name/receipt.txt")

    @get:Console
    val buildScriptPath = project.buildFile.relativeTo(project.repoRoot().asFile)

    @TaskAction
    @Suppress("unused")
    fun check() {
        receiptFile.get().asFile.delete()
        val currentFiles = current.files.map { it.name }.sorted()
        val expectedFiles = expected.files.map { it.name }.filterNot { it.startsWith("kotlin-compiler-embeddable-") }.sorted()
        require(currentFiles == expectedFiles) {
            var message = "$path dependencies to kotlin-compiler-embeddable dependencies are wrong\n\nexpected:\n\n"
            message += expectedFiles.joinToString(separator = "\n", postfix = "\n\ngot:\n\n") { "  $it" }
            message += currentFiles.joinToString(separator = "\n", postfix = "\n\n") { "  $it" }
            message += "Please fix dependency declarations in $buildScriptPath"
            message
        }
        receiptFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("OK")
        }
    }
}
