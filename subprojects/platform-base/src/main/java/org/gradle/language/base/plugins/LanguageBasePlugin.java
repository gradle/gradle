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
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.BiAction;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.text.TreeFormatter;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultProjectSourceSet;
import org.gradle.language.base.internal.model.ComponentSpecInitializer;
import org.gradle.language.base.internal.model.LanguageSourceSetNodeInitializer;
import org.gradle.language.base.internal.registry.DefaultLanguageRegistry;
import org.gradle.language.base.internal.registry.LanguageRegistration;
import org.gradle.language.base.internal.registry.LanguageRegistry;
import org.gradle.model.*;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.extract.ConstructableTypesRegistry;
import org.gradle.model.internal.manage.schema.extract.DefaultConstructableTypesRegistry;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;

/**
 * Base plugin for language support.
 *
 * Adds a {@link BinarySpec} container named {@code binaries} to the project. Adds a {@link org.gradle.language.base.ProjectSourceSet} named {@code sources} to the project.
 *
 * For each binary instance added to the binaries container, registers a lifecycle task to create that binary.
 */
@Incubating
public class LanguageBasePlugin implements Plugin<Project> {

    private final ModelRegistry modelRegistry;

    @Inject
    public LanguageBasePlugin(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    public void apply(final Project target) {
        target.getPluginManager().apply(LifecycleBasePlugin.class);
        applyRules(modelRegistry);
    }

    private void applyRules(ModelRegistry modelRegistry) {
        final String descriptor = LanguageBasePlugin.class.getSimpleName() + ".apply()";
        final ModelRuleDescriptor ruleDescriptor = new SimpleModelRuleDescriptor(descriptor);

        modelRegistry.configure(ModelActionRole.Defaults, DirectNodeNoInputsModelAction.of(ModelReference.of("binaries"), ruleDescriptor, new Action<MutableModelNode>() {
            @Override
            public void execute(MutableModelNode binariesNode) {
                binariesNode.applyToAllLinks(ModelActionRole.Finalize, InputUsingModelAction.single(ModelReference.of(BinarySpec.class), ruleDescriptor, ModelReference.of(ITaskFactory.class), new BiAction<BinarySpec, ITaskFactory>() {
                    @Override
                    public void execute(BinarySpec binary, ITaskFactory taskFactory) {
                        BinarySpecInternal binarySpecInternal = (BinarySpecInternal) binary;
                        if (!binarySpecInternal.isLegacyBinary()) {
                            TaskInternal binaryLifecycleTask = taskFactory.create(binarySpecInternal.getProjectScopedName(), DefaultTask.class);
                            binaryLifecycleTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                            binaryLifecycleTask.setDescription(String.format("Assembles %s.", binary));
                            binary.setBuildTask(binaryLifecycleTask);
                        }
                    }
                }));
            }
        }));

        modelRegistry.getRoot().applyToAllLinksTransitive(ModelActionRole.Defaults,
            DirectNodeNoInputsModelAction.of(
                ModelReference.of(BinarySpec.class),
                new SimpleModelRuleDescriptor(descriptor),
                ComponentSpecInitializer.binaryAction()));
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Service
        ModelSchemaStore schemaStore(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(ModelSchemaStore.class);
        }

        @Service
        ManagedProxyFactory proxyFactory(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(ManagedProxyFactory.class);
        }

        @Service
        ConstructableTypesRegistry constructableTypesRegistry() {
            return new DefaultConstructableTypesRegistry();
        }

        @Model
        void binaries(BinaryContainer binaries) {}

        @Mutate
        // Path needed to avoid closing root scope before `NodeInitializerRegistry` can be finalized
        void registerSourceSetNodeInitializers(ConstructableTypesRegistry constructableTypesRegistry, ServiceRegistry serviceRegistry, LanguageRegistry languageRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);

            for (LanguageRegistration<?> languageRegistration : languageRegistry) {
                ModelType<?> sourceSetType = ModelType.of(languageRegistration.getSourceSetType());
                LanguageSourceSetNodeInitializer languageSourceSetNodeInitializer = new LanguageSourceSetNodeInitializer(languageRegistry, sourceSetType);
                constructableTypesRegistry.registerConstructableType(sourceSetType, languageSourceSetNodeInitializer);
            }
        }

        @Mutate
        // Path needed to avoid closing root scope before `NodeInitializerRegistry` can be finalized
        void registerNodeInitializerExtractionStrategies(NodeInitializerRegistry nodeInitializerRegistry, ConstructableTypesRegistry constructableTypesRegistry) {
            nodeInitializerRegistry.registerStrategy(constructableTypesRegistry);
        }

        @Model
        ProjectSourceSet sources(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(Instantiator.class).newInstance(DefaultProjectSourceSet.class);
        }

        @Service
        LanguageRegistry languages() {
            return new DefaultLanguageRegistry();
        }

        @Mutate
        void copyBinaryTasksToTaskContainer(TaskContainer tasks, ModelMap<BinarySpec> binaries) {
            for (BinarySpec binary : binaries) {
                tasks.addAll(binary.getTasks());
                Task buildTask = binary.getBuildTask();
                if (buildTask != null) {
                    tasks.add(buildTask);
                }
            }
        }

        @Mutate
        void attachBinariesToAssembleLifecycle(@Path("tasks.assemble") Task assemble, ModelMap<BinarySpecInternal> binaries) {
            List<BinarySpecInternal> notBuildable = Lists.newArrayList();
            boolean hasBuildableBinaries = false;
            for (BinarySpecInternal binary : binaries) {
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
                        formatter.node(binary.getDisplayName());
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
