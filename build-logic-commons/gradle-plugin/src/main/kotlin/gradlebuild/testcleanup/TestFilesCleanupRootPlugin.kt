/*
 * Copyright 2022 the original author or authors.
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

package gradlebuild.testcleanup

import gradlebuild.testcleanup.extension.TestFilesCleanupBuildServiceRootExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.reporting.Reporting
import org.gradle.api.tasks.testing.Test
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf
import java.io.File


class TestFilesCleanupRootPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        require(project.rootProject == project) { "This plugin should be applied to root project!" }
        val globalExtension = project.extensions.create<TestFilesCleanupBuildServiceRootExtension>("testFilesCleanupRoot")
        project.gradle.taskGraph.whenReady {
            val testFilesCleanupService = project.gradle.sharedServices.registerIfAbsent("testFilesCleanupBuildService", TestFilesCleanupService::class.java) {
                parameters.rootBuildDir = project.layout.buildDirectory
                parameters.projectStates.putAll(globalExtension.projectStates)
                parameters.testPathToBinaryResultsDirs = allTasks.filterIsInstance<Test>().associate { it.path to it.binaryResultsDirectory.get().asFile }

                val taskPathToReports = this@whenReady.allTasks
                    .associate { it.path to it.genericHtmlReports() + it.findTraceJson() }
                    .filter { it.value.isNotEmpty() }

                parameters.taskPathToReports = globalExtension.taskPathToReports.map { taskPathToReportsInExtension ->
                    (taskPathToReportsInExtension.keys + taskPathToReports.keys).associateWith {
                        taskPathToReportsInExtension.getOrDefault(it, emptyList()) + taskPathToReports.getOrDefault(it, emptyList())
                    }
                }
            }
            project.gradle.serviceOf<BuildEventsListenerRegistry>().onTaskCompletion(testFilesCleanupService)
        }
    }

    private
    fun Task.findTraceJson(): List<File> {
        if (this !is Test) {
            return emptyList()
        }
        // e.g. build/test-results/embeddedIntegTest/trace.json
        return listOf(project.layout.buildDirectory.file("test-results/$name/trace.json").get().asFile)
    }

    private
    fun Task.genericHtmlReports() = when (this) {
        is Reporting<*> -> listOf(this.reports["html"].outputLocation.get().asFile)
        else -> emptyList()
    }
}
