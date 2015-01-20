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
import org.gradle.language.base.internal.tasks.AssembleBinariesTask;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.collection.internal.PolymorphicDomainObjectContainerModelProjection;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.DefaultBinaryContainer;

import javax.inject.Inject;

/**
 * Base plugin for language support.
 *
 * Adds a {@link org.gradle.platform.base.BinaryContainer} named {@code binaries} to the project. Adds a {@link org.gradle.language.base.ProjectSourceSet} named {@code sources} to the project.
 *
 * For each binary instance added to the binaries container, registers a lifecycle task to create that binary.
 */
@Incubating
public class LanguageBasePlugin implements Plugin<Project> {

    private final Instantiator instantiator;
    private final ModelRegistry modelRegistry;

    @Inject
    public LanguageBasePlugin(Instantiator instantiator, ModelRegistry modelRegistry) {
        this.instantiator = instantiator;
        this.modelRegistry = modelRegistry;
    }

    public void apply(final Project target) {
        target.getPluginManager().apply(LifecycleBasePlugin.class);
        target.getExtensions().create("sources", DefaultProjectSourceSet.class);

        DefaultBinaryContainer binaries = target.getExtensions().create("binaries", DefaultBinaryContainer.class, instantiator);
        modelRegistry.create(
                PolymorphicDomainObjectContainerModelProjection.bridgeNamedDomainObjectCollection(
                        ModelType.of(DefaultBinaryContainer.class),
                        ModelType.of(DefaultBinaryContainer.class),
                        ModelType.of(BinarySpec.class),
                        ModelPath.path("binaries"),
                        binaries,
                        getClass().getName() + ".apply()"),
                ModelPath.ROOT
        );
    }

    /**
     * Model rules.
     */
    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Model
        ProjectSourceSet sources(ExtensionContainer extensions) {
            return extensions.getByType(ProjectSourceSet.class);
        }

        @Mutate
        void createLifecycleTaskForBinary(TaskContainer tasks, BinaryContainer binaries) {
            for (BinarySpecInternal binary : binaries.withType(BinarySpecInternal.class)) {
                if (!binary.isLegacyBinary()) {
                    Task binaryLifecycleTask = tasks.create(binary.getName());
                    binaryLifecycleTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                    binaryLifecycleTask.setDescription(String.format("Assembles %s.", binary));
                    binary.setBuildTask(binaryLifecycleTask);
                }
            }
        }

        @Mutate
        void attachBinariesToAssembleLifecycle(CollectionBuilder<Task> tasks, final BinaryContainer binaries) {
            tasks.named("assemble", new Action<Task>() {
                @Override
                public void execute(Task assemble) {
                    for (BinarySpecInternal binary : binaries.withType(BinarySpecInternal.class)) {
                        if (!binary.isLegacyBinary()) {
                            if (binary.isBuildable()) {
                                assemble.dependsOn(binary);
                            } else {
                                ((AssembleBinariesTask)assemble).notBuildable(binary);
                            }
                        }
                    }
                }
            });
        }
    }
}
