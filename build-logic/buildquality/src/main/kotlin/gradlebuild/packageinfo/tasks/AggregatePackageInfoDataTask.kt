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
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
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
 *
 * The task also verifies that the merged data covers exactly [expectedProjects]. The per-project files are collected
 * by reselecting a variant over a dependency graph, and reselection silently skips components that do not offer the
 * variant, so a project missing the producing plugin would otherwise just disappear from the data set.
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

    /** The paths of the projects that must each contribute to [projectData], and the only ones that may. */
    @get:Input
    abstract val expectedProjects: SetProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun action() {
        val gson = Gson()
        // Sorted, so the output does not depend on the order the per-project files are resolved in.
        val projectsByPackage = sortedMapOf<String, MutableSet<String>>()
        val packageInfoByPackage = sortedMapOf<String, MutableList<PackageInfoFile>>()
        val seenProjects = sortedSetOf<String>()

        for (file in projectData.files) {
            val data = file.reader().use { gson.fromJson(it, ProjectPackageInfoData::class.java) }
            seenProjects.add(data.project)
            for ((packageName, relativePaths) in data.packages) {
                projectsByPackage.getOrPut(packageName) { sortedSetOf() }.add(data.project)
                packageInfoByPackage.getOrPut(packageName) { mutableListOf() }
                    .addAll(relativePaths.map { PackageInfoFile(data.project, rebase(data.projectDir, it)) })
            }
        }

        verifyCoverage(seenProjects)

        val aggregated = projectsByPackage.mapValues { (packageName, projects) ->
            PackageEntry(
                projects.toList(),
                packageInfoByPackage.getValue(packageName).sortedBy { it.path }
            )
        }
        outputFile.get().asFile.writeText(gson.toJson(aggregated))
    }

    private fun verifyCoverage(seenProjects: Set<String>) {
        val expected = expectedProjects.get()
        val missing = expected - seenProjects
        val unexpected = seenProjects - expected
        if (missing.isEmpty() && unexpected.isEmpty()) {
            return
        }
        val message = mutableListOf("Package-info data does not cover the expected set of projects.")
        if (missing.isNotEmpty()) {
            message.add(
                "  Projects that ship in the distribution but produce no package-info data" +
                    " (do they apply gradlebuild.package-info-data?): ${missing.sorted().joinToString(", ")}"
            )
        }
        if (unexpected.isNotEmpty()) {
            message.add(
                "  Projects that produce package-info data but are not expected to ship in the distribution: " +
                    unexpected.sorted().joinToString(", ")
            )
        }
        throw GradleException(message.joinToString("\n"))
    }

    /** A project located at the settings directory has an empty [projectDir]; do not turn its paths absolute. */
    private fun rebase(projectDir: String, path: String) =
        if (projectDir.isEmpty()) path else "$projectDir/$path"
}
