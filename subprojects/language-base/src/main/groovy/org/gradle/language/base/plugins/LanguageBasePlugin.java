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
package org.gradle.language.base.plugins;

import org.gradle.api.*;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultProjectSourceSet;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.base.internal.DefaultBinaryContainer;
import org.gradle.runtime.base.internal.BinarySpecInternal;

import javax.inject.Inject;

/**
 * Base plugin for language support.
 *
 * Adds a {@link org.gradle.runtime.base.BinaryContainer} named {@code binaries} to the
 * project. Adds a {@link org.gradle.language.base.ProjectSourceSet} named {@code sources} to the project.
 *
 * For each binary instance added to the binaries container, registers a lifecycle task to create that binary.
 */
@Incubating
public class LanguageBasePlugin implements Plugin<Project> {

    private final Instantiator instantiator;

    @Inject
    public LanguageBasePlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void apply(final Project target) {
        target.getPlugins().apply(LifecycleBasePlugin.class);

        target.getExtensions().create("sources", DefaultProjectSourceSet.class, instantiator);
        target.getExtensions().create("binaries", DefaultBinaryContainer.class, instantiator);
    }

    /**
     * Model rules.
     */
    @SuppressWarnings("UnusedDeclaration")
    @RuleSource
    static class Rules {

        @Model
        ProjectSourceSet sources(ExtensionContainer extensions) {
            return extensions.getByType(ProjectSourceSet.class);
        }

        @Model
        BinaryContainer binaries(ExtensionContainer extensions) {
            return extensions.getByType(BinaryContainer.class);
        }

        @Mutate
        void createLifecycleTaskForBinary(TaskContainer tasks, BinaryContainer binaries) {

            Task assembleTask = tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME);
            for (BinarySpecInternal binary : binaries.withType(BinarySpecInternal.class)) {
                if (!binary.isLegacyBinary()) {
                    Task binaryLifecycleTask = tasks.create(binary.getNamingScheme().getLifecycleTaskName());
                    binaryLifecycleTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                    binaryLifecycleTask.setDescription(String.format("Assembles %s.", binary));
                    binary.setBuildTask(binaryLifecycleTask);

                    if (binary.isBuildable()) {
                        assembleTask.dependsOn(binary);
                    }
                }
            }
        }
    }
}
