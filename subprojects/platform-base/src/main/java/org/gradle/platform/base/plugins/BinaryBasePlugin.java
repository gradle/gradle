/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.platform.base.plugins;

import org.gradle.api.*;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.model.*;
import org.gradle.platform.base.*;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;

/**
 * Base plugin for binaries support.
 *
 * - Adds a {@link BinarySpec} container named {@code binaries} to the project.
 * - Registers the base {@link BinarySpec} type.
 * - For each {@link BinarySpec}, registers a lifecycle task to assemble that binary.
 * - For each {@link BinarySpec}, adds the binary's source sets as its default inputs.
 * - Links the tasks for each {@link BinarySpec} across to the tasks container.
 */
@Incubating
public class BinaryBasePlugin implements Plugin<Project> {

    @Override
    public void apply(final Project target) {
        target.getPluginManager().apply(ComponentBasePlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Model
        void binaries(BinaryContainer binaries) {
        }

        @ComponentType
        void registerBaseBinarySpec(TypeBuilder<BinarySpec> builder) {
            builder.defaultImplementation(BaseBinarySpec.class);
            builder.internalView(BinarySpecInternal.class);
        }

        @Mutate
        void copyBinaryTasksToTaskContainer(TaskContainer tasks, BinaryContainer binaries) {
            for (BinarySpec binary : binaries) {
                tasks.addAll(binary.getTasks());
                Task buildTask = binary.getBuildTask();
                if (buildTask != null) {
                    tasks.add(buildTask);
                }
            }
        }

        @Finalize
        public void defineBuildLifecycleTask(@Each BinarySpecInternal binary, ITaskFactory taskFactory) {
            if (binary.isLegacyBinary()) {
                return;
            }
            TaskInternal binaryLifecycleTask = taskFactory.create(binary.getProjectScopedName(), DefaultTask.class);
            binaryLifecycleTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
            binaryLifecycleTask.setDescription(String.format("Assembles %s.", binary));
            binary.setBuildTask(binaryLifecycleTask);
        }

        @Finalize
        void addSourceSetsOwnedByBinariesToTheirInputs(@Each BinarySpecInternal binary) {
            if (binary.isLegacyBinary()) {
                return;
            }
            binary.getInputs().addAll(binary.getSources().values());
        }
    }
}
