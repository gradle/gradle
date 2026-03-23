/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.packaging.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.EmptyFileVisitor
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

@CacheableTask
abstract class PathPrefixLister @Inject constructor(private val archiveOperations: ArchiveOperations): DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apiJar: RegularFileProperty

    @get:OutputFile
    abstract val pathPrefixFile: RegularFileProperty

    @TaskAction
    fun generatePackageList() {
        val packageSet = mutableSetOf<String>()
        archiveOperations.zipTree(apiJar.get().asFile).matching {
            include("**/*.class")
        }.visit(object: EmptyFileVisitor() {
            override fun visitFile(details: FileVisitDetails) {
                val parentPath = details.relativePath.parent
                if (parentPath != null) {
                    packageSet.add(parentPath.pathString)
                }
            }
        })

        pathPrefixFile.get().asFile.bufferedWriter().use { writer ->
            packageSet.sorted().forEach { packageName ->
                writer.write(packageName)
                writer.newLine()
            }
        }
    }
}
