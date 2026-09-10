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

package gradlebuild.repackaging

import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject


/**
 * Unpacks a set of archives (the jars, or the sources jars, of the modules a project repackages —
 * see `gradlebuild.distribution.repackaged.api-java`) into one directory, dropping their
 * `META-INF/`. Two archives contributing the same path is an error: repackaged modules must not
 * overlap.
 *
 * A task class rather than a `Sync` with `zipTree` sources so that the archives are an ordinary
 * file-collection input — resolved when the task graph is stored — and the unpacking runs off the
 * injected [ArchiveOperations] at execution time; a copy spec fed with mapped `zipTree` providers
 * would carry the mapping lambda (and with it the build script) into the configuration cache.
 */
@CacheableTask
abstract class ExtractArchivesForRepackaging : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archives: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    protected abstract val archiveOperations: ArchiveOperations

    @get:Inject
    protected abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun extract() {
        val trees = archives.files.map { archiveOperations.zipTree(it) }
        fileSystemOperations.sync {
            trees.forEach { tree ->
                from(tree) {
                    exclude("META-INF/**")
                }
            }
            into(outputDirectory)
            duplicatesStrategy = DuplicatesStrategy.FAIL
        }
    }
}
