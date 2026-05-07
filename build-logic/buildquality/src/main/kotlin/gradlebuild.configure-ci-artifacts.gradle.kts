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
import gradlebuild.basics.FileLocationProvider
import gradlebuild.docs.FindBrokenInternalLinks
import gradlebuild.integrationtests.tasks.DistributionTest
import gradlebuild.performance.tasks.PerformanceTest
import gradlebuild.testcleanup.TestFilesCleanupService
import me.champeau.gradle.japicmp.JapicmpTask

if (BuildEnvironment.isCiServer && project.name != "gradle-kotlin-dsl-accessors") {
    val testFilesCleanupService = project.gradle.sharedServices.registerIfAbsent("testFilesCleanupBuildService", TestFilesCleanupService::class.java) {
        throw IllegalStateException("Must be already created")
    }

    project.tasks.withType<ValidatePlugins>().configureEach {
        testFilesCleanupService.get().addTaskReports(path, validatePluginsReports())
    }

    project.tasks.withType<FindBrokenInternalLinks>().configureEach {
        testFilesCleanupService.get().addTaskReports(path, findBrokenInternalLinksReports())
    }
    project.tasks.withType<DistributionTest>().configureEach {
        testFilesCleanupService.get().addTaskReports(path, distributionReports())
    }
    project.tasks.withType<JapicmpTask>().configureEach {
        testFilesCleanupService.get().addTaskReports(path, japicmpReports())
    }
    project.tasks.withType<PerformanceTest>().configureEach {
        testFilesCleanupService.get().addTaskReports(path, performanceTestReports())
    }
}

fun ValidatePlugins.validatePluginsReports(): List<FileLocationProvider> = listOf(outputFile)

fun FindBrokenInternalLinks.findBrokenInternalLinksReports(): List<FileLocationProvider> = listOf(reportFile)

fun DistributionTest.distributionReports(): List<FileLocationProvider> = listOf(
    gradleInstallationForTest.gradleUserHomeDir.dir("test-kit-daemon"),
    gradleInstallationForTest.gradleUserHomeDir.dir("kotlin-compiler-daemon"),
    gradleInstallationForTest.daemonRegistry,
)

fun JapicmpTask.japicmpReports(): List<FileLocationProvider> = listOf(richReport.flatMap { it.destinationDir.file(it.reportName) })

fun PerformanceTest.performanceTestReports(): List<FileLocationProvider> = listOf(layout.file(provider { reportDir.parentFile }))
