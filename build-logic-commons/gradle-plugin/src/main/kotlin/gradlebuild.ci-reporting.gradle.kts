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

import gradlebuild.testcleanup.TestFilesCleanupProjectState
import gradlebuild.testcleanup.TestFilesCleanupService
import gradlebuild.testcleanup.extension.TestFileCleanUpExtension
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.support.serviceOf

/**
 * When run from a Continuous Integration environment, we only want to archive a subset of reports, mostly for
 * failing tasks only, to not use up unnecessary disk space on Team City. This also improves the performance of
 * artifact publishing by reducing the artifacts and packaging reports that consist of multiple files.
 *
 * Reducing the number of reports also makes it easier to find the important ones when analysing a failed build in
 * TeamCity.
 */
val testFileCleanUpExtension = project.extensions.create<TestFileCleanUpExtension>("testFilesCleanup").apply {
    reportOnly.convention(false)
}

if ("CI" in System.getenv() && project.name != "gradle-kotlin-dsl-accessors") {
    val testFilesCleanupService = project.gradle.sharedServices.registerIfAbsent("testFilesCleanupBuildService", TestFilesCleanupService::class.java) {
        require(project.path == ":") { "Must be applied to root project first, now: ${project.path}" }
        parameters.taskPathToReports.set(mutableMapOf<String, List<File>>())
        parameters.testPathToBinaryResultsDirs.set(mutableMapOf<String, File>())
        parameters.rootBuildDir.set(project.layout.buildDirectory)
    }
    project.gradle.serviceOf<BuildEventsListenerRegistry>().onTaskCompletion(testFilesCleanupService)
    testFilesCleanupService.get().parameters.projectStates.put(project.path, TestFilesCleanupProjectState(project.path, project.layout.buildDirectory.get().asFile, testFileCleanUpExtension.reportOnly))

    val taskPathToReports = testFilesCleanupService.get().parameters.taskPathToReports
    val testPathToBinaryResultsDirs = testFilesCleanupService.get().parameters.testPathToBinaryResultsDirs

    project.tasks.withType<Test>().configureEach {
        testPathToBinaryResultsDirs.put(path, binaryResultsDirectory.asFile)
    }

    project.tasks.configureEach {
        val reports = mutableListOf<Any>()
        if (this is Test) {
            reports.add(traceJson())
        }
        if (this is Reporting<*>) {
            reports.add(genericHtmlReports())
        }
        val existingReports: List<Any> = taskPathToReports.getting(path).orElse(emptyList()).get()
        taskPathToReports.put(path, reports + existingReports)
    }
}
// e.g. build/test-results/embeddedIntegTest/trace.json
fun Test.traceJson() = project.layout.buildDirectory.file("test-results/$name/trace.json").get().asFile

fun Reporting<*>.genericHtmlReports() = reports["html"].outputLocation
