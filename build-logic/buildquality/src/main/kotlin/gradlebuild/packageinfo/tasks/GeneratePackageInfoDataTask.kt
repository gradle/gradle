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

package gradlebuild.packageinfo.tasks

import com.google.gson.Gson
import gradlebuild.packageinfo.model.PACKAGE_INFO_FILE_NAME
import gradlebuild.packageinfo.model.ProjectPackageInfoData
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.SortedMap
import javax.inject.Inject

/**
 * Collects the packages owned by a single project, together with the `package-info.java` files that apply to them.
 *
 * A directory contributes a package if it directly contains at least one source file; `package-info.java` is one,
 * so a directory holding nothing but a `package-info.java` is still a package. Directories that only hold other
 * directories (`org/`, `org/gradle/`, ...) are intermediates and are skipped. Only source files count, so stray files
 * such as `.DS_Store`, which Gradle's file snapshotting excludes by default and which therefore do not affect the
 * task's fingerprint, cannot affect its output either.
 *
 * Packages without a `package-info.java` are reported with an empty list, so consumers can tell "no package-info"
 * apart from "no such package".
 *
 * The per-project outputs are combined by [AggregatePackageInfoDataTask].
 */
@CacheableTask
abstract class GeneratePackageInfoDataTask @Inject constructor(layout: ProjectLayout): DefaultTask() {
    /** Absolute path to project, used for path relativization */
    private val projectDir = layout.projectDirectory.asFile

    /** The Gradle path of the project, used to attribute packages to projects. */
    @get:Input
    abstract val projectPath: Property<String>

    /**
     * The project directory, relative to the settings directory.
     *
     * Recorded so the aggregator can rebase the project-relative paths below. Being relative, it keeps the cache
     * key independent of the checkout location.
     */
    @get:Input
    abstract val projectBaseDir: Property<String>

    /** The source roots to scan, e.g. `src/main/java`. Packages are named relative to these. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoots: ConfigurableFileCollection

    /**
     * The source roots, relative to the project directory.
     *
     * [sourceRoots] is fingerprinted relative to each root, which leaves the roots' own locations out of the
     * fingerprint, while the output records paths relative to the project directory. Without this input, moving a
     * `package-info.java` between two roots (or renaming a root) would leave the task up-to-date with stale paths.
     */
    @get:Input
    val sourceRootPaths: Provider<List<String>>
        get() = sourceRoots.elements.map { roots ->
            roots.map { it.asFile.relativeToProject() }.sorted()
        }

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun action() {
        // Sorted, so the output is deterministic regardless of file system iteration order.
        val packages = sortedMapOf<String, MutableList<String>>()

        for (sourceRoot in sourceRoots.files) {
            if (sourceRoot.isDirectory) {
                processSourceRoot(sourceRoot, packages)
            }
        }

        packages.values.forEach { it.sort() }
        val data = ProjectPackageInfoData(projectPath.get(), projectBaseDir.get(), packages)
        outputFile.get().asFile.writeText(Gson().toJson(data))
    }

    private fun processSourceRoot(sourceRoot: File, packages: SortedMap<String, MutableList<String>>) {
        // The root itself is not a package: files directly in it belong to the default package,
        // which cannot have a package-info.
        for (dir in sourceRoot.walkTopDown().filter { it.isDirectory && it != sourceRoot }) {
            val files = dir.listFiles()?.filter { it.isFile && it.extension in SOURCE_EXTENSIONS }.orEmpty()
            if (files.isEmpty()) {
                // An intermediate directory, not a package of its own.
                continue
            }
            val packageName = dir.toRelativeString(sourceRoot).replace(File.separatorChar, '.')
            val packageInfoFiles = packages.getOrPut(packageName) { mutableListOf() }
            files.filter { it.name == PACKAGE_INFO_FILE_NAME }
                .forEach { packageInfoFiles.add(it.relativeToProject()) }
        }
    }

    private fun File.relativeToProject() =
        toRelativeString(projectDir).replace(File.separatorChar, '/')

    private companion object {
        /** File extensions that make a directory a package. */
        val SOURCE_EXTENSIONS = setOf("java", "groovy", "kt")
    }
}
