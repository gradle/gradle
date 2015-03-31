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

import com.google.common.collect.Lists;
import org.gradle.api.*;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.BiAction;
import org.gradle.internal.BiActions;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.text.TreeFormatter;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultProjectSourceSet;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.internal.BridgedCollections;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.DefaultBinaryContainer;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
        applyRules(modelRegistry, binaries);
    }

    private static void applyRules(ModelRegistry modelRegistry, DefaultBinaryContainer binaries) {
        final String descriptor = LanguageBasePlugin.class.getName() + ".apply()";
        final ModelRuleDescriptor ruleDescriptor = new SimpleModelRuleDescriptor(descriptor);
        ModelPath binariesPath = ModelPath.path("binaries");
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

        modelRegistry.configure(ModelActionRole.Defaults, DirectNodeModelAction.of(ModelReference.of(binariesPath), ruleDescriptor, new Action<MutableModelNode>() {
            @Override
            public void execute(MutableModelNode binariesNode) {
                binariesNode.applyToAllLinks(ModelActionRole.Finalize, BiActionBackedModelAction.single(ModelReference.of(BinarySpec.class), ruleDescriptor, ModelReference.of(ITaskFactory.class), new BiAction<BinarySpec, ITaskFactory>() {
                    @Override
                    public void execute(BinarySpec binary, ITaskFactory taskFactory) {
                        if (!((BinarySpecInternal) binary).isLegacyBinary()) {
                            TaskInternal binaryLifecycleTask = taskFactory.create(binary.getName(), DefaultTask.class);
                            binaryLifecycleTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                            binaryLifecycleTask.setDescription(String.format("Assembles %s.", binary));
                            binary.setBuildTask(binaryLifecycleTask);
                        }
                    }
                }));

                binariesNode.applyToAllLinks(ModelActionRole.Initialize, DirectNodeModelAction.of(ModelReference.of(BinarySpec.class), new SimpleModelRuleDescriptor(descriptor + ".tasks"), new Action<MutableModelNode>() {
                    @Override
                    public void execute(MutableModelNode modelNode) {
                        ModelPath binaryPath = modelNode.getPath();
                        ModelPath taskNodePath = binaryPath.child("__tasks");
                        ModelType<Collection<Task>> taskCollectionType = ModelTypes.collectionOf(Task.class);
                        ModelReference<Collection<Task>> tasksNodeReference = ModelReference.of(taskNodePath, taskCollectionType);
                        modelNode.addLink(ModelCreators.of(tasksNodeReference, BiActions.doNothing())
                                        .withProjection(new UnmanagedModelProjection<Collection<Task>>(taskCollectionType))
                                        .descriptor(descriptor + ".createTasksNode")
                                        .build()
                        );
                        MutableModelNode link = modelNode.getLink(taskNodePath.getName());
                        assert link != null;
                        link.setPrivateData(taskCollectionType, modelNode.getPrivateData(ModelType.of(BinarySpec.class)).getTasks());
                    }
                }));
            }
        }));
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

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

        @Mutate
        void attachBinariesToAssembleLifecycle(@Path("tasks.assemble") Task assemble, BinaryContainer binaries) {
            List<BinarySpecInternal> notBuildable = Lists.newArrayList();
            boolean hasBuildableBinaries = false;
            for (BinarySpecInternal binary : binaries.withType(BinarySpecInternal.class)) {
                if (!binary.isLegacyBinary()) {
                    if (binary.isBuildable()) {
                        assemble.dependsOn(binary);
                        hasBuildableBinaries = true;
                    } else {
                        notBuildable.add(binary);
                    }
                }
            }
            if (!hasBuildableBinaries && !notBuildable.isEmpty()) {
                assemble.doFirst(new CheckForNotBuildableBinariesAction(notBuildable));
            }
        }

        private static class CheckForNotBuildableBinariesAction implements Action<Task> {
            private final List<BinarySpecInternal> notBuildable;

            public CheckForNotBuildableBinariesAction(List<BinarySpecInternal> notBuildable) {
                this.notBuildable = notBuildable;
            }

            @Override
            public void execute(Task task) {
                Set<? extends Task> taskDependencies = task.getTaskDependencies().getDependencies(task);

                if (taskDependencies.isEmpty()) {
                    TreeFormatter formatter = new TreeFormatter();
                    formatter.node("No buildable binaries found");
                    formatter.startChildren();
                    for (BinarySpecInternal binary : notBuildable) {
                        formatter.node(binary.getName());
                        formatter.startChildren();
                        binary.getBuildAbility().explain(formatter);
                        formatter.endChildren();
                    }
                    formatter.endChildren();
                    throw new GradleException(formatter.toString());
                }
            }
        }
    }
}
