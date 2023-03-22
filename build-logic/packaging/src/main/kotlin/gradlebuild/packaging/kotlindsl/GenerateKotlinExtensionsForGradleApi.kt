/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.packaging.kotlindsl

import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject


@CacheableTask
abstract class GenerateKotlinExtensionsForGradleApi : DefaultTask() {

    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @TaskAction
    fun action() {

        val gradleJars = classpath.files.filter { it.name.startsWith("gradle-") && !it.name.startsWith("gradle-kotlin-dsl-") }
        val apiMetadataJar = gradleJars.single { it.name.startsWith("gradle-api-metadata") }

        val tempJar: File = tempFileProvider.newTemporaryFile("gradle-kotlin-dsl-extensions.jar")

        // Generation code is to be moved to `build-logic`, hack access to it from the wrapper for prototyping purpose
        val generatorClass = Class.forName("org.gradle.kotlin.dsl.codegen.ApiExtensionsJarGenerator")
        val constructor = generatorClass.getConstructor(TemporaryFileProvider::class.java, Collection::class.java, File::class.java, Function0::class.java)
        constructor.isAccessible = true
        val generator = constructor.newInstance(tempFileProvider, gradleJars, apiMetadataJar, {})
        val generate = generatorClass.getMethod("generate", File::class.java)
        generate.isAccessible = true
        generate.invoke(generator, tempJar)

        fs.copy {
            from(archives.zipTree(tempJar))
            into(destinationDirectory)
        }

        // This patches the sources for the binary compatibility check until generation code is moved to `build-logic`
        destinationDirectory.get().asFile.walkTopDown().filter { it.extension == "kt" }.forEach { sourceFile ->
            sourceFile.writeText(buildString {
                sourceFile.readLines().forEach { line ->
                    appendLine(line)
                    if (line == " */") {
                        appendLine("@org.gradle.api.Generated")
                    }
                }
            })
        }
    }

    @get:Inject
    protected
    abstract val tempFileProvider: TemporaryFileProvider

    @get:Inject
    protected
    abstract val archives: ArchiveOperations

    @get:Inject
    protected
    abstract val fs: FileOperations
}
