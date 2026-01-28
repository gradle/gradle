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

import gradlebuild.testcleanup.extension.TestFileCleanUpExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf
import java.io.File

class TestFilesCleanupPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val testFilesCleanupExtension = project.extensions.create<TestFileCleanUpExtension>("testFilesCleanup").apply {
            reportOnly.convention(false)
        }
        project.gradle.taskGraph.whenReady {
            val testFilesCleanupService = project.gradle.sharedServices.registerIfAbsent("testFilesCleanupBuildService-" + project.name, TestFilesCleanupService::class.java) {
                parameters.rootBuildDir.set(File("/Users/bo/Projects/gradle/build"))
                parameters.testFilesCleanupExtension.set(testFilesCleanupExtension)
                parameters.projectPath.set(project.path)
                parameters.projectBuildDir.set(project.layout.buildDirectory)
            }
            project.gradle.serviceOf<BuildEventsListenerRegistry>().onTaskCompletion(testFilesCleanupService)
        }
    }
}
