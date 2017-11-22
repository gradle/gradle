/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.kotlin.dsl.resolver

import org.gradle.api.artifacts.transform.ArtifactTransform
import java.io.File
import java.util.zip.ZipFile

/**
 * This dependency transform is responsible for extracting the sources from
 * a downloaded ZIP of the Gradle sources, and will return the list of main sources
 * subdirectories for all subprojects.
 */
class ExtractGradleSourcesTransform : ArtifactTransform() {
    fun Any?.discard() = Unit

    override
    fun transform(input: File): List<File> {
        ZipFile(input).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                zip.getInputStream(entry).use { input ->
                    val out = File(outputDirectory, entry.name)
                    if (!out.parentFile.exists()) {
                        out.parentFile.mkdirs()
                    }
                    if (entry.isDirectory) {
                        out.mkdir().discard()
                    } else {
                        out.outputStream().use { output ->
                            input.copyTo(output)
                        }.discard()
                    }

                }
            }
        }

        return sourceDirectories()
    }

    private
    fun sourceDirectories() = outputDirectory.walk().filter(this::isSourceDirectory).toList()

    private
    fun isSourceDirectory(file: File) =
        file.isDirectory && file.parentFile.name == "main" && file.parentFile.parentFile.name == "src"
}
