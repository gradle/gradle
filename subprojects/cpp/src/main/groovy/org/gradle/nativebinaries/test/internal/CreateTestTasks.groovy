/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.test.internal
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.language.base.BinaryContainer
import org.gradle.model.ModelRule
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal
import org.gradle.nativebinaries.tasks.InstallExecutable
import org.gradle.nativebinaries.test.TestSuiteExecutableBinary
import org.gradle.nativebinaries.test.tasks.RunTestExecutable

public class CreateTestTasks extends ModelRule {
    private final Project project

    CreateTestTasks(Project project) {
        this.project = project
    }

    @SuppressWarnings("unused")
    void create(TaskContainer tasks, BinaryContainer binaries) {
        binaries.withType(TestSuiteExecutableBinary).each { testBinary ->
            def binary = testBinary as ProjectNativeBinaryInternal
            def namingScheme = binary.namingScheme

            // TODO:DAZ Need a better model for accessing tasks related to binary
            def installTask = binary.tasks.withType(InstallExecutable).find()

            def runTask = tasks.create(namingScheme.getTaskName("run"), RunTestExecutable)
            runTask.setDescription("Runs the " + binary.getDisplayName())
            runTask.inputs.files(installTask.outputs.files)

            runTask.conventionMapping.testExecutable = { installTask.runScript }
            runTask.conventionMapping.outputDir = { project.file("${project.buildDir}/test-results/${namingScheme.outputDirectoryBase}") }
        }
    }
}
