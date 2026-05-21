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

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.model.Each;
import org.gradle.model.Finalize;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.NamedEntityInstantiator;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;
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
public abstract class BinaryBasePlugin implements Plugin<Project> {

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
            for (BinarySpecInternal binary : binaries.withType(BinarySpecInternal.class)) {
                TaskContainerInternal tasksInternal = (TaskContainerInternal) tasks;
                if (binary.isLegacyBinary()) {
                    continue;
                }
                tasksInternal.addAllInternal(binary.getTasks());
                Task buildTask = binary.getBuildTask();
                if (buildTask != null) {
                    tasksInternal.addInternal(buildTask);
                }
            }
        }

        @Finalize
        public void defineBuildLifecycleTask(@Each BinarySpecInternal binary, NamedEntityInstantiator<Task> taskInstantiator) {
            if (binary.isLegacyBinary()) {
                return;
            }
            TaskInternal binaryLifecycleTask = taskInstantiator.create(binary.getProjectScopedName(), DefaultTask.class);
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
