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
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.internal.BiAction;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
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
import org.gradle.model.internal.inspect.BiActionBackedModelAction;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.DefaultBinaryContainer;

import javax.inject.Inject;
import java.util.List;

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
        String descriptor = getClass().getName() + ".apply()";
        final ModelRuleDescriptor ruleDescriptor = new SimpleModelRuleDescriptor(descriptor);
        final ModelPath binariesPath = ModelPath.path("binaries");
        BridgedCollections.dynamicTypes(
                modelRegistry,
                binariesPath,
                descriptor,
                ModelType.of(DefaultBinaryContainer.class),
                ModelType.of(DefaultBinaryContainer.class),
                ModelType.of(BinarySpec.class),
                binaries,
                Named.Namer.INSTANCE,
                BridgedCollections.itemDescriptor(descriptor)
        );

        final ModelAction<?> eachBinaryAction = BiActionBackedModelAction.single(ModelReference.of(BinarySpec.class), ruleDescriptor, ModelReference.of(ITaskFactory.class), new BiAction<BinarySpec, ITaskFactory>() {
            @Override
            public void execute(BinarySpec binary, ITaskFactory taskFactory) {
                if (!((BinarySpecInternal) binary).isLegacyBinary()) {
                    TaskInternal binaryLifecycleTask = taskFactory.create(binary.getName(), DefaultTask.class);
                    binaryLifecycleTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                    binaryLifecycleTask.setDescription(String.format("Assembles %s.", binary));
                    binary.setBuildTask(binaryLifecycleTask);
                }
            }
        });

        ModelReference<BinaryContainer> binaryContainerReference = ModelReference.of(binariesPath, BinaryContainer.class);
        modelRegistry.configure(ModelActionRole.Defaults, DirectNodeModelAction.of(binaryContainerReference, ruleDescriptor, new Action<MutableModelNode>() {
            @Override
            public void execute(MutableModelNode binariesNode) {
                binariesNode.applyToAllLinks(ModelActionRole.Finalize, eachBinaryAction);
            }
        }));

        SimpleModelRuleDescriptor copyTasksDescriptor = new SimpleModelRuleDescriptor("LanguageBasePlugin.apply().copyTasks");
        modelRegistry.configure(ModelActionRole.Mutate, DirectNodeModelAction.of(TaskContainerInternal.MODEL_PATH, copyTasksDescriptor, binaryContainerReference, new BiAction<MutableModelNode, BinaryContainer>() {
            @Override
            public void execute(MutableModelNode modelNode, BinaryContainer binaryContainer) {
                for (BinarySpec binarySpec : binaryContainer) {
                    if (((BinarySpecInternal) binarySpec).isLegacyBinary()) {
                        continue;
                    }

                    for (Task task : binarySpec.getTasks()) {
                        addTaskNode(modelNode, binariesPath, binarySpec.getName(), task.getName(), getTaskType(task));
                    }
                    Task buildTask = binarySpec.getBuildTask();
                    if (buildTask != null) {
                        addBuildTaskNode(modelNode, binariesPath, binarySpec.getName(), buildTask.getName(), getTaskType(buildTask));
                    }
                }
            }

            public ModelType<? extends Task> getTaskType(Task buildTask) {
                // Unpack decoration layer
                return Cast.uncheckedCast(ModelType.of(buildTask.getClass().getSuperclass()));
            }

            public <T extends Task> void addTaskNode(MutableModelNode modelNode, ModelPath binariesPath, String binaryName, final String taskName, final ModelType<T> type) {
                ModelPath path = TaskContainerInternal.MODEL_PATH.child(taskName);
                ModelReference<T> reference = ModelReference.of(path, type);
                ModelCreator creator = ModelCreators.of(reference, new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    @Override
                    public void execute(MutableModelNode modelNode, List<ModelView<?>> modelViews) {
                        BinarySpec binary = ModelViews.getInstance(modelViews.get(0), BinarySpec.class);
                        modelNode.setPrivateData(type, type.cast(binary.getTasks().get(taskName)));
                    }
                })
                        .descriptor("LanguageBasePlugin.apply().addTaskNode." + taskName)
                        .withProjection(new UnmanagedModelProjection<T>(type))
                        .inputs(ModelReference.of(binariesPath.child(binaryName), BinarySpec.class))
                        .build();

                modelNode.addLink(creator);
            }

            public <T extends Task> void addBuildTaskNode(MutableModelNode modelNode, ModelPath binariesPath, String binaryName, String taskName, final ModelType<T> type) {
                ModelPath path = TaskContainerInternal.MODEL_PATH.child(taskName);
                ModelReference<T> reference = ModelReference.of(path, type);
                ModelCreator creator = ModelCreators.of(reference, new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    @Override
                    public void execute(MutableModelNode modelNode, List<ModelView<?>> modelViews) {
                        BinarySpec binary = ModelViews.getInstance(modelViews.get(0), BinarySpec.class);
                        T buildTask = type.cast(binary.getBuildTask());
                        modelNode.setPrivateData(type, buildTask);
                    }
                })
                        .descriptor("LanguageBasePlugin.apply().addBuildTaskNode." + taskName)
                        .inputs(ModelReference.of(binariesPath.child(binaryName), BinarySpec.class))
                        .withProjection(new UnmanagedModelProjection<T>(type))
                        .build();

                modelNode.addLink(creator);
            }
        }));
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

        @Model
        ProjectSourceSet sources(ExtensionContainer extensions) {
            return extensions.getByType(ProjectSourceSet.class);
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
