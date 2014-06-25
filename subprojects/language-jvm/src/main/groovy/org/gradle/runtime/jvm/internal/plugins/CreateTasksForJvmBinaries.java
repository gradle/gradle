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
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.model.ModelRule;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.jvm.internal.JvmLibraryBinaryInternal;

public class CreateTasksForJvmBinaries extends ModelRule {
    void createTasks(TaskContainer tasks, BinaryContainer binaries) {
        for (JvmLibraryBinaryInternal binary : binaries.withType(JvmLibraryBinaryInternal.class)) {
            Task jarTask = createJarTask(tasks, binary);
            binary.builtBy(jarTask);
            binary.getTasks().add(jarTask);
        }
    }

    private Task createJarTask(TaskContainer tasks, JvmLibraryBinaryInternal binary) {
        Jar jar = tasks.create(binary.getNamingScheme().getTaskName("create"), Jar.class);
        jar.setDescription(String.format("Creates the binary file for %s.", binary.getDisplayName()));
        jar.from(binary.getClassesDir());

        jar.setDestinationDir(binary.getJarFile().getParentFile());
        jar.setArchiveName(binary.getJarFile().getName());

        return jar;
    }
}
