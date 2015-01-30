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
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultProjectSourceSet;
import org.gradle.language.base.internal.tasks.AssembleBinariesTask;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.collection.internal.BridgedCollections;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
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
    private final ITaskFactory taskFactory;

    @Inject
    public LanguageBasePlugin(Instantiator instantiator, ModelRegistry modelRegistry, ITaskFactory taskFactory) {
        this.instantiator = instantiator;
        this.modelRegistry = modelRegistry;
        this.taskFactory = taskFactory;
    }

    public void apply(final Project target) {
        target.getPluginManager().apply(LifecycleBasePlugin.class);
        target.getExtensions().create("sources", DefaultProjectSourceSet.class);

        DefaultBinaryContainer binaries = target.getExtensions().create("binaries", DefaultBinaryContainer.class, instantiator);
        String descriptor = getClass().getName() + ".apply()";
        final ModelRuleDescriptor ruleDescriptor = new SimpleModelRuleDescriptor(descriptor);
        ModelPath binariesPath = ModelPath.path("binaries");
        modelRegistry.create(
                BridgedCollections.dynamicTypes(
                        ModelType.of(DefaultBinaryContainer.class),
                        ModelType.of(DefaultBinaryContainer.class),
                        ModelType.of(BinarySpec.class),
                        binariesPath,
                        binaries,
                        Named.Namer.INSTANCE,
                        descriptor,
                        BridgedCollections.itemDescriptor(descriptor)
                ),
                ModelPath.ROOT
        );

        modelRegistry.apply(ModelPath.ROOT, ModelActionRole.Defaults, DirectNodeModelAction.of(ModelReference.of(binariesPath), ruleDescriptor, new Action<MutableModelNode>() {
            @Override
            public void execute(MutableModelNode binariesNode) {
                binariesNode.applyToAllLinks(ModelActionRole.Finalize, ActionBackedModelAction.of(ModelReference.of(BinarySpec.class), ruleDescriptor, new Action<BinarySpec>() {
                    @Override
                    public void execute(BinarySpec binary) {
                        if (!((BinarySpecInternal) binary).isLegacyBinary()) {
                            TaskInternal binaryLifecycleTask = taskFactory.create(binary.getName(), DefaultTask.class);
                            binaryLifecycleTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                            binaryLifecycleTask.setDescription(String.format("Assembles %s.", binary));
                            binary.setBuildTask(binaryLifecycleTask);
                        }
                    }
                }));
            }
        }));
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

        @Model
        ITaskFactory taskFactory(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(ITaskFactory.class);
        }

        @Model
        ProjectSourceSet sources(ExtensionContainer extensions) {
            return extensions.getByType(ProjectSourceSet.class);
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

        /**
         * Rules.
         */
        static class AssembleRule extends RuleSource {
            @Mutate
            void addDependency(Task assemble, BinaryContainer binaries) {
                for (BinarySpecInternal binary : binaries.withType(BinarySpecInternal.class)) {
                    if (!binary.isLegacyBinary()) {
                        if (binary.isBuildable()) {
                            assemble.dependsOn(binary);
                        } else {
                            ((AssembleBinariesTask) assemble).notBuildable(binary);
                        }
                    }
                }
            }
        }

        @Mutate
        void attachBinariesToAssembleLifecycle(CollectionBuilder<Task> tasks) {
            tasks.named("assemble", AssembleRule.class);
        }
    }
}
