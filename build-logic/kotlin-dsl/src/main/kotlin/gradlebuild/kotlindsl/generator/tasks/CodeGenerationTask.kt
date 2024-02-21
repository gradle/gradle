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

package gradlebuild.kotlindsl.generator.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File


@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
abstract class CodeGenerationTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    protected
    abstract fun File.writeFiles()

    @Suppress("unused")
    @TaskAction
    fun run() {
        outputDir.get().asFile.apply {
            recreate()
            writeFiles()
        }
    }

    protected
    fun File.writeFile(relativePath: String, text: String) {
        resolve(relativePath).apply {
            parentFile.mkdirs()
            writeText(text)
        }
    }

    private
    fun File.recreate() {
        deleteRecursively()
        mkdirs()
    }
}
