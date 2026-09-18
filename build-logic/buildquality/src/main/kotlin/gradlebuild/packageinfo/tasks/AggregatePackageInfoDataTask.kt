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
import gradlebuild.packageinfo.model.PackageEntry
import gradlebuild.packageinfo.model.PackageInfoFile
import gradlebuild.packageinfo.model.ProjectPackageInfoData
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Merges the per-project outputs of [GeneratePackageInfoDataTask] into build-wide, package-keyed data.
 *
 * Project-relative paths are rebased onto each project's directory, so all paths in the output are relative to the
 * settings directory.
 */
@CacheableTask
abstract class AggregatePackageInfoDataTask : DefaultTask() {

    /**
     * The per-project JSON files to merge.
     *
     * Fingerprinted without paths: the files are all named `package-info.json` and live under per-project build
     * directories, so only their contents are meaningful.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val projectData: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun action() {
        val gson = Gson()
        // Sorted, so the output does not depend on the order the per-project files are resolved in.
        val projectsByPackage = sortedMapOf<String, MutableSet<String>>()
        val packageInfoByPackage = sortedMapOf<String, MutableList<PackageInfoFile>>()

        for (file in projectData.files) {
            val data = file.reader().use { gson.fromJson(it, ProjectPackageInfoData::class.java) }
            for ((packageName, relativePaths) in data.packages) {
                projectsByPackage.getOrPut(packageName) { sortedSetOf() }.add(data.project)
                packageInfoByPackage.getOrPut(packageName) { mutableListOf() }
                    .addAll(relativePaths.map { PackageInfoFile(data.project, rebase(data.projectDir, it)) })
            }
        }

        val aggregated = projectsByPackage.mapValues { (packageName, projects) ->
            PackageEntry(
                projects.toList(),
                packageInfoByPackage.getValue(packageName).sortedBy { it.path }
            )
        }
        outputFile.get().asFile.writeText(gson.toJson(aggregated))
    }

    /** A project located at the settings directory has an empty [projectDir]; do not turn its paths absolute. */
    private fun rebase(projectDir: String, path: String) =
        if (projectDir.isEmpty()) path else "$projectDir/$path"
}
