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

import gradlebuild.testcleanup.TestFilesCleanupRootPlugin
import gradlebuild.testcleanup.extension.TestFileCleanUpExtension
import gradlebuild.testcleanup.extension.TestFilesCleanupBuildServiceRootExtension
import gradlebuild.testcleanup.extension.TestFilesCleanupProjectState

/**
 * When run from a Continuous Integration environment, we only want to archive a subset of reports, mostly for
 * failing tasks only, to not use up unnecessary disk space on Team City. This also improves the performance of
 * artifact publishing by reducing the artifacts and packaging reports that consist of multiple files.
 *
 * Reducing the number of reports also makes it easier to find the important ones when analysing a failed build in
 * Team City.
 */

val testFilesCleanup = extensions.create<TestFileCleanUpExtension>("testFilesCleanup").apply {
    reportOnly.convention(false)
}

if ("CI" in System.getenv() && project.name != "gradle-kotlin-dsl-accessors") {
    rootProject.plugins.apply(TestFilesCleanupRootPlugin::class.java)
    val globalExtension = rootProject.extensions.getByType<TestFilesCleanupBuildServiceRootExtension>()

    val projectState = objects.newInstance(TestFilesCleanupProjectState::class.java)

    globalExtension.projectStates.put(path, projectState)
    projectState.projectBuildDir = buildDir
    projectState.projectPath = path
    projectState.reportOnly = testFilesCleanup.reportOnly
}
