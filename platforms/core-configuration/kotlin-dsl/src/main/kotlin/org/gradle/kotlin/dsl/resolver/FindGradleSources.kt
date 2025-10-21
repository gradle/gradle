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

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.internal.file.PathTraversalChecker.safePathName
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile


/**
 * This dependency transform is responsible for extracting the sources from
 * a downloaded ZIP of the Gradle sources, and will return the list of main sources
 * subdirectories for all subprojects.
 *
 * This transforms should not be split into multiple ones given the amount of files because
 * this would add lots of inputs processing time.
 */
@DisableCachingByDefault(because = "Not worth caching")
internal
abstract class FindGradleSources : TransformAction<TransformParameters.None> {

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        ZipFile(input.get().asFile).use { zip ->
            val it = zip.entries()
            if (!it.hasMoreElements()) {
                // Zip contains no files.
                return
            }

            // We assume the zip contains a single root directory
            val first = it.nextElement()
            val slashIndex = first.name.indexOf('/')
            if (slashIndex < 0) {
                // Zip contains no root directory.
                return
            }
            val rootPrefix = first.name.substring(0, slashIndex + 1)

            val projectOutputs = ProjectSourceOutputs(outputs)
            processEntry(rootPrefix, projectOutputs, first, zip)
            while (it.hasMoreElements()) {
                processEntry(rootPrefix, projectOutputs, it.nextElement(), zip)
            }
        }
    }

    private fun processEntry(prefix: String, outputs: ProjectSourceOutputs, entry: ZipEntry, zip: ZipFile) {
        val rootOffset = consume(prefix, 0, entry)
        if (rootOffset == -1) {
            // Entry not under root directory.
            return
        }

        val subprojectsOffset = consume("subprojects/", rootOffset, entry)
        if (subprojectsOffset != -1) {
            processProjectEntry(subprojectsOffset, outputs, entry, zip)
            return
        }

        val platformsOffset = consume("platforms/", rootOffset, entry)
        if (platformsOffset != -1) {
            val platformDirOffset = consumeDir(platformsOffset, entry)
            if (platformDirOffset != -1) {
                processProjectEntry(platformDirOffset, outputs, entry, zip)
            }
            return
        }
    }

    private fun processProjectEntry(offset: Int, outputs: ProjectSourceOutputs, entry: ZipEntry, zip: ZipFile) {
        // Offset marks the beginning of the project directory name
        val projectNameOffset = consumeDir(offset, entry)
        if (projectNameOffset == -1) {
            return
        }

        val srcMainOffset = consume("src/main/", projectNameOffset, entry)
        if (srcMainOffset == -1) {
            return
        }

        val sourceDirectoryOffset = consumeDir(srcMainOffset, entry)
        if (sourceDirectoryOffset == -1 || entry.isDirectory) {
            return
        }

        val projectName = entry.name.substring(offset, projectNameOffset - 1)
        val projectSrcOutputDir = outputs.dir(projectName)
        val output = projectSrcOutputDir.resolve(safePathName(entry.name.substring(sourceDirectoryOffset)))

        zip.getInputStream(entry).writeTo(output)
    }

    private fun InputStream.writeTo(output: File) {
        output.parentFile.mkdirs()
        use { input ->
            output.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    fun consumeDir(offset: Int, entry: ZipEntry): Int {
        val dirIndex = entry.name.indexOf('/', offset)
        if (dirIndex >= 0) {
            return dirIndex + 1
        }

        return -1
    }

    fun consume(toSkip: String, offset: Int, entry: ZipEntry): Int {
        if (entry.name.startsWith(toSkip, offset)) {
            return offset + toSkip.length
        }

        return -1
    }

    class ProjectSourceOutputs(private val outputs: TransformOutputs, private val dirs: MutableMap<String, File> = mutableMapOf()) {
        fun dir(name: String): File {
            val dir = dirs[name]
            if (dir != null) {
                return dir
            }
            return outputs.dir(name).also { dirs[name] = it }
        }
    }

}
