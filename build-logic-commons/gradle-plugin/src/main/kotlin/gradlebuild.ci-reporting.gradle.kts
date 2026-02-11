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

import gradlebuild.basics.FileLocationProvider
import gradlebuild.testcleanup.TestFilesCleanupService
import gradlebuild.testcleanup.extension.TestFileCleanUpExtension
import org.gradle.kotlin.dsl.support.serviceOf

/**
 * When run from a Continuous Integration environment, we only want to archive a subset of reports, mostly for
 * failing tasks only, to not use up unnecessary disk space on Team City. This also improves the performance of
 * artifact publishing by reducing the artifacts and packaging reports that consist of multiple files.
 *
 * Reducing the number of reports also makes it easier to find the important ones when analysing a failed build in
 * TeamCity.
 */
val testFilesCleanup = project.extensions.create<TestFileCleanUpExtension>("testFilesCleanup").apply {
    reportOnly.convention(false)
}

// TODO:isolated:incremental the service won't track up-to-date projects (project configuration skipped) that still run tests
if ("CI" in System.getenv() && project.name != "gradle-kotlin-dsl-accessors") {
    val testFilesCleanupServiceProvider = project.gradle.sharedServices.registerIfAbsent("testFilesCleanupBuildService", TestFilesCleanupService::class.java) {
        require(project.path == ":") { "Must be applied to root project first, now: ${project.path}" }
        parameters.rootBuildDir.set(project.layout.buildDirectory)
    }
    if (project.path == ":") {
        project.gradle.serviceOf<BuildEventsListenerRegistry>().onTaskCompletion(testFilesCleanupServiceProvider)
        project.gradle.taskGraph.whenReady {
            testFilesCleanupServiceProvider.get().onTaskGraphReady()
        }
    }
    val testFilesCleanupService = testFilesCleanupServiceProvider.get()
    testFilesCleanupService.addProjectState(project.path, project.layout.buildDirectory.asFile, testFilesCleanup.reportOnly)

    project.tasks.configureEach {
        if (this is Test) {
            testFilesCleanupService.addTestBinaryResultsDir(path, binaryResultsDirectory.asFile)
            testFilesCleanupService.addTaskReports(path, traceJson())
        }
        if (this is Reporting<*>) {
            testFilesCleanupService.addTaskReports(path, genericHtmlReports())
        }
    }
}

// e.g. build/test-results/embeddedIntegTest/trace.json
fun Test.traceJson(): List<FileLocationProvider> = listOf(project.layout.buildDirectory.file("test-results/$name/trace.json"))

fun Reporting<*>.genericHtmlReports(): List<FileLocationProvider> = listOfNotNull(reports.findByName("html")?.outputLocation)
