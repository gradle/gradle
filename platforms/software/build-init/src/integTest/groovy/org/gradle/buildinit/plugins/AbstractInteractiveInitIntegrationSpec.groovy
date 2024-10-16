/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.buildinit.plugins

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleHandle

abstract class AbstractInteractiveInitIntegrationSpec extends AbstractInitIntegrationSpec {
    def buildTypePrompt = "Select type of build to generate:"
    def dslPrompt = "Select build script DSL:"
    def incubatingPrompt = "Generate build using new APIs and behavior (some features may change in the next minor release)?"
    def basicType = "4: Basic (build structure only)"
    def basicTypeOption = 4
    def applicationOption = 1
    def defaultProjectName = "some-thing"
    def defaultFileName = "some-file"
    def projectNamePrompt = "Project name (default: $defaultProjectName)"
    def convertMavenBuildPrompt = "Found a Maven build. Generate a Gradle build from this?"
    def overwriteFilesPrompt = "Found existing files in the project directory: '${testDirectory.file(defaultProjectName)}'." + System.lineSeparator() + "Directory will be modified and existing files may be overwritten.  Continue? (default: no)"
    def javaOption = 1
    def languageSelectionOptions = [
        "Select implementation language:",
        "1: Java",
        "2: Kotlin",
        "3: Groovy",
        "4: Scala",
        "5: C++",
        "6: Swift"
    ]

    @Override
    String subprojectName() { 'app' }

    protected GradleHandle startInteractiveExecutorWithTasks(String... names) {
        executer.withForceInteractive(true)
        executer.withStdinPipe()
        executer.withTasks(names)
        executer.start()
    }

    protected ExecutionResult closeInteractiveExecutor(GradleHandle handle) {
        handle.stdinPipe.close()
        handle.waitForFinish()
    }

    protected void assertPromptedToOverwriteExistingFiles(GradleHandle handle) {
        assert handle.standardOutput.contains(overwriteFilesPrompt)
    }

    protected void assertBuildAborted(GradleHandle handle) {
        assert handle.errorOutput.contains("Aborting build initialization due to existing files in the project directory: '${testDirectory.file(defaultProjectName)}'.")
    }

    protected void assertSuggestedResolutionsToExistingFilesProblem(GradleHandle handle) {
        handle.errorOutput.with {
            assert it.contains("Remove any existing files in the project directory and run the init task again.")
            assert it.contains("Enable the --overwrite option to allow existing files to be overwritten.")
        }
    }
}
