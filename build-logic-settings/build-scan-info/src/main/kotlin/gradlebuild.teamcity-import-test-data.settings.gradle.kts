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

import gradlebuild.EmitTeamCityImportDataBuildService
import gradlebuild.registerTaskExecutionObserver
import org.gradle.api.initialization.ProjectDescriptor
import java.io.File

/**
 * Register a build service that instructs TeamCity to import the JUnit XML of every Test task that did not run its
 * actions, so that the test data is reported even when the task is UP-TO-DATE or FROM-CACHE.
 *
 * Only needed when TeamCity splits the tests of a build across parallel batches.
 */
if (System.getenv("TEAMCITY_PARALLEL_TESTS_ENABLED") != null) {
    // The projects of the build are only known once the settings script has been evaluated.
    gradle.settingsEvaluated {
        val repoRoot = rootDir.toPath()
        val relativeProjectDirs = mutableMapOf<String, String>()
        fun collect(project: ProjectDescriptor) {
            relativeProjectDirs[project.path] = repoRoot.relativize(project.projectDir.toPath()).toString().replace(File.separatorChar, '/')
            project.children.forEach(::collect)
        }
        collect(rootProject)

        registerTaskExecutionObserver(EmitTeamCityImportDataBuildService::class.java) {
            projectPathToRelativeProjectDir.putAll(relativeProjectDirs)
        }
    }
}
