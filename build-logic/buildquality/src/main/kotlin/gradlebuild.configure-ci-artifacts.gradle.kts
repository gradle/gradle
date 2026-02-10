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
import gradlebuild.testcleanup.TestFilesCleanupService
import me.champeau.gradle.japicmp.JapicmpTask

if (BuildEnvironment.isCiServer && project.name != "gradle-kotlin-dsl-accessors") {
    val testFilesCleanupService = project.gradle.sharedServices.registerIfAbsent("testFilesCleanupBuildService", TestFilesCleanupService::class.java) {
        throw IllegalStateException("Must be already created")
    }

    project.tasks.withType<ValidatePlugins>().forEach {
        testFilesCleanupService.get().addTaskReports(it.path, it.validatePluginsReports())
    }

    project.tasks.withType<FindBrokenInternalLinks>().forEach {
        testFilesCleanupService.get().addTaskReports(it.path, it.findBrokenInternalLinksReports())
    }
    project.tasks.withType<DistributionTest>().forEach {
        testFilesCleanupService.get().addTaskReports(it.path, it.distributionReports())
    }
    project.tasks.withType<JapicmpTask>().forEach {
        testFilesCleanupService.get().addTaskReports(it.path, it.japicmpReports())
    }
    project.tasks.withType<PerformanceTest>().forEach {
        testFilesCleanupService.get().addTaskReports(it.path, it.performanceTestReports())
    }
}

fun ValidatePlugins.validatePluginsReports() = listOf(outputFile)

fun FindBrokenInternalLinks.findBrokenInternalLinksReports() = listOf(reportFile)

fun DistributionTest.distributionReports() = listOf(
    gradleInstallationForTest.gradleUserHomeDir.dir("test-kit-daemon"),
    gradleInstallationForTest.gradleUserHomeDir.dir("kotlin-compiler-daemon"),
    gradleInstallationForTest.daemonRegistry,
)

fun JapicmpTask.japicmpReports() = listOf(richReport.get().destinationDir.file(richReport.get().reportName))

fun PerformanceTest.performanceTestReports() = listOf(reportDir.parentFile)
