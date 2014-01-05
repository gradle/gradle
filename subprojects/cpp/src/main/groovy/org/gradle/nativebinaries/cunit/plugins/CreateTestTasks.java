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

package org.gradle.nativebinaries.cunit.plugins;

import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.BinaryContainer;
import org.gradle.model.ModelRule;
import org.gradle.nativebinaries.cunit.TestSuiteExecutableBinary;
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal;

class CreateTestTasks extends ModelRule {

    // TODO:DAZ Needs to run installed image (depend on task, and execute task output)
    @SuppressWarnings("unused")
    void create(TaskContainer tasks, BinaryContainer binaries) {
        for (TestSuiteExecutableBinary testBinary : binaries.withType(TestSuiteExecutableBinary.class)) {
            ProjectNativeBinaryInternal binary = (ProjectNativeBinaryInternal) testBinary;
            String taskName = binary.getNamingScheme().getTaskName("run");
            Exec runTask = tasks.create(taskName, Exec.class);
            runTask.setDescription("Runs the " + binary.getDisplayName());
            runTask.dependsOn(binary);
            runTask.setExecutable(binary.getPrimaryOutput());
        }
    }
}
