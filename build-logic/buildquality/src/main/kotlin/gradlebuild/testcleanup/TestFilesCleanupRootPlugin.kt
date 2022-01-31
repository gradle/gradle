/*
 * Copyright 2021 the original author or authors.
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

import gradlebuild.classycle.tasks.Classycle
import gradlebuild.cleanup.tasks.KillLeakingJavaProcesses
import gradlebuild.docs.FindBrokenInternalLinks
import gradlebuild.integrationtests.tasks.DistributionTest
import gradlebuild.performance.tasks.PerformanceTest
import gradlebuild.testcleanup.extension.TestFilesCleanupBuildServiceRootExtension
import me.champeau.gradle.japicmp.JapicmpTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.reporting.Reporting
import org.gradle.api.tasks.testing.Test
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.plugin.devel.tasks.ValidatePlugins
import org.jlleitschuh.gradle.ktlint.tasks.GenerateReportsTask
import java.io.File


class TestFilesCleanupRootPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        require(project.rootProject == project) { "This plugin should be applied to root project!" }
        val globalExtension = project.extensions.create<TestFilesCleanupBuildServiceRootExtension>("testFilesCleanupRoot")
        project.gradle.taskGraph.whenReady {
            val testFilesCleanupService = project.gradle.sharedServices.registerIfAbsent("testFilesCleanupBuildService", TestFilesCleanupService::class.java) {
                parameters.rootBuildDir.set(project.buildDir)
                parameters.projectStates.putAll(globalExtension.projectStates)
                parameters.cleanupRunnerStep.set(this@whenReady.allTasks.filterIsInstance<KillLeakingJavaProcesses>().isNotEmpty())

                val allTasks = this@whenReady.allTasks
                val taskPathToGenericHtmlReports = allTasks.associate { it.path to it.failedTaskGenericHtmlReports() }.filter { it.value.isNotEmpty() }
                val taskPathToAttachedReports = allTasks.associate { it.path to it.attachedReportLocations() }.filter { it.value.isNotEmpty() }
                val taskPathToCustomReports = allTasks.associate { it.path to it.customReports() }.filter { it.value.isNotEmpty() }
                val taskPathToTDTraceJsons = allTasks.associate { it.path to it.findTraceJson() }.filter { it.value.isNotEmpty() }
                val testPathToBinaryResultsDirs = allTasks.filterIsInstance<Test>().associate { it.path to it.binaryResultsDirectory.get().asFile }

                val relevantTaskPaths = taskPathToGenericHtmlReports.keys + taskPathToAttachedReports.keys +
                    taskPathToCustomReports.keys + taskPathToTDTraceJsons.keys + testPathToBinaryResultsDirs.keys
                val relevantTaskPathToProjectPath = allTasks.filter { relevantTaskPaths.contains(it.path) }.associate { it.path to it.project.path }

                parameters.relevantTaskPathToProjectPath.putAll(relevantTaskPathToProjectPath)
                parameters.taskPathToGenericHtmlReports.putAll(taskPathToGenericHtmlReports)
                parameters.taskPathToAttachedReports.putAll(taskPathToAttachedReports)
                parameters.taskPathToCustomReports.putAll(taskPathToCustomReports)
                parameters.taskPathToTDTraceJsons.putAll(taskPathToTDTraceJsons)
                parameters.testPathToBinaryResultsDirs.putAll(testPathToBinaryResultsDirs)
            }
            project.gradle.serviceOf<BuildEventsListenerRegistry>().onTaskCompletion(testFilesCleanupService)
        }
    }

    private
    fun Task.findTraceJson(): List<File> {
        if (this !is Test) {
            return emptyList()
        }
        // build/test-results/embeddedIntegTest/trace.json
        val traceJson = project.buildDir.resolve("test-results/$name/trace.json")
        return if (traceJson.isFile) {
            listOf(traceJson)
        } else {
            emptyList()
        }
    }

    private
    fun Task.failedTaskGenericHtmlReports() = when (this) {
        is Reporting<*> -> listOf(this.reports["html"].outputLocation.get().asFile)
        else -> emptyList()
    }

    private
    fun Task.customReports(): List<File> = when (this) {
        is ValidatePlugins -> listOf(outputFile.get().asFile)
        is Classycle -> listOf(reportFile.get().asFile)
        is FindBrokenInternalLinks -> listOf(reportFile.get().asFile)
        is DistributionTest -> listOf(
            gradleInstallationForTest.gradleUserHomeDir.dir("test-kit-daemon").get().asFile,
            gradleInstallationForTest.gradleUserHomeDir.dir("kotlin-compiler-daemon").get().asFile,
            gradleInstallationForTest.daemonRegistry.get().asFile
        )
        is GenerateReportsTask -> listOf(reportsOutputDirectory.get().asFile)
        else -> emptyList()
    }

    private
    fun Task.attachedReportLocations() = when (this) {
        is JapicmpTask -> listOf(richReport.destinationDir.resolve(richReport.reportName))
        is PerformanceTest -> listOf(reportDir.parentFile)
        else -> emptyList()
    }
}
