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
import gradlebuild.basics.BuildEnvironment
import gradlebuild.docs.FindBrokenInternalLinks
import gradlebuild.integrationtests.tasks.DistributionTest
import gradlebuild.performance.tasks.PerformanceTest
import gradlebuild.testcleanup.extension.TestFilesCleanupBuildServiceRootExtension
import gradlebuild.binarycompatibility.JapicmpTask
import org.jlleitschuh.gradle.ktlint.tasks.GenerateReportsTask

if (BuildEnvironment.isCiServer && project.name != "gradle-kotlin-dsl-accessors") {
    val globalExtension = rootProject.extensions.getByType<TestFilesCleanupBuildServiceRootExtension>()
    project.gradle.taskGraph.whenReady {
        val allTasks = this@whenReady.allTasks
        val taskPathToReports = allTasks.associate { it.path to it.customReports() + it.attachedReportLocations() }.filter { it.value.isNotEmpty() }
        globalExtension.taskPathToReports = taskPathToReports
    }
}

fun Task.customReports(): List<File> = when (this) {
    is ValidatePlugins -> listOf(outputFile.get().asFile)
    is FindBrokenInternalLinks -> listOf(reportFile.get().asFile)
    is DistributionTest -> listOf(
        gradleInstallationForTest.gradleUserHomeDir.dir("test-kit-daemon").get().asFile,
        gradleInstallationForTest.gradleUserHomeDir.dir("kotlin-compiler-daemon").get().asFile,
        gradleInstallationForTest.daemonRegistry.get().asFile
    )
    is GenerateReportsTask -> listOf(reportsOutputDirectory.get().asFile)
    else -> emptyList()
}

fun Task.attachedReportLocations() = when (this) {
    is JapicmpTask -> listOf(richReport.get().destinationDir.get().asFile.resolve(richReport.get().reportName.get()))
    is PerformanceTest -> listOf(reportDir.parentFile)
    else -> emptyList()
}
