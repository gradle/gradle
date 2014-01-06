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

package org.gradle.nativebinaries.test.internal;

import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.BinaryContainer;
import org.gradle.model.ModelRule;
import org.gradle.nativebinaries.test.TestSuiteExecutableBinary;
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal;
import org.gradle.nativebinaries.tasks.InstallExecutable;

public class CreateTestTasks extends ModelRule {

    @SuppressWarnings("unused")
    void create(TaskContainer tasks, BinaryContainer binaries) {
        for (TestSuiteExecutableBinary testBinary : binaries.withType(TestSuiteExecutableBinary.class)) {
            ProjectNativeBinaryInternal binary = (ProjectNativeBinaryInternal) testBinary;
            // TODO:DAZ Need a better model for accessing tasks related to binary
            InstallExecutable installTask = binary.getTasks().withType(InstallExecutable.class).iterator().next();
            String taskName = binary.getNamingScheme().getTaskName("run");

            Exec runTask = tasks.create(taskName, Exec.class);
            runTask.setDescription("Runs the " + binary.getDisplayName());
            runTask.dependsOn(installTask);
            runTask.setExecutable(installTask.getRunScript());
        }
    }
}
