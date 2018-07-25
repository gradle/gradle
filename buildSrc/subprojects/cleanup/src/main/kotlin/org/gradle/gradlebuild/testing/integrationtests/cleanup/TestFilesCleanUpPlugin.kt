/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.gradlebuild.testing.integrationtests.cleanup

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create


/**
 * Generate a report showing which tests in a subproject are leaving
 * files around.
 */
open class TestFilesCleanUpPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val testFilesCleanup = extensions.create<TestFileCleanUpExtension>("testFilesCleanup", objects)
        testFilesCleanup.policy.set(WhenNotEmpty.FAIL)

        tasks.register("verifyTestFilesCleanup", EmptyDirectoryCheck::class.java) {
            targetDirectory.set(layout.buildDirectory.dir("tmp/test files"))
            reportFile.set(layout.buildDirectory.file("reports/remains.txt"))
            policy.set(testFilesCleanup.policy)
        }
    }
}
