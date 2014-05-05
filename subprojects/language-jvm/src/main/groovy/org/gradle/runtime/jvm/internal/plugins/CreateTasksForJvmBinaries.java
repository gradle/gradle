/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.runtime.jvm.internal.plugins;

import org.gradle.api.Task;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.jvm.internal.JvmLibraryBinaryInternal;
import org.gradle.model.ModelRule;

public class CreateTasksForJvmBinaries extends ModelRule {
    void createTasks(TaskContainer tasks, BinaryContainer binaries) {
        for (JvmLibraryBinaryInternal binary : binaries.withType(JvmLibraryBinaryInternal.class)) {
            Task jarTask = createJarTask(tasks, binary);
            binary.builtBy(jarTask);
        }
    }

    private Task createJarTask(TaskContainer tasks, JvmLibraryBinaryInternal binary) {
        // TODO:DAZ This should be a Jar task: need to move 'jar' and related infrastructure out of 'plugins' project
        Zip jarTask = tasks.create(binary.getNamingScheme().getTaskName("create"), Zip.class);
        jarTask.setDescription(String.format("Creates the binary file for %s.", binary.getDisplayName()));
        return jarTask;
    }
}
