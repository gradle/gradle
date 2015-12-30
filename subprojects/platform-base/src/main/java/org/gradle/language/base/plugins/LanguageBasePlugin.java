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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.BiAction;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultProjectSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetFactory;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.model.ComponentSpecInitializer;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.model.*;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.extract.FactoryBasedNodeInitializerExtractionStrategy;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.*;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;

import javax.inject.Inject;

import static org.gradle.model.internal.core.NodePredicate.allDescendants;
import static org.gradle.model.internal.core.NodePredicate.allLinks;

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
        final String baseDescriptor = LanguageBasePlugin.class.getSimpleName() + "#";

        modelRegistry.configure(ModelActionRole.Defaults, DirectNodeNoInputsModelAction.of(ModelReference.of("binaries"),
                new SimpleModelRuleDescriptor(baseDescriptor + "attachBuildTasks"), new Action<MutableModelNode>() {
            @Override
            public void execute(MutableModelNode binariesNode) {
                binariesNode.applyTo(allLinks(), ModelActionRole.Finalize, InputUsingModelAction.single(ModelReference.of(BinarySpecInternal.class),
                                new SimpleModelRuleDescriptor(baseDescriptor + "attachBuildTask"), ModelReference.of(ITaskFactory.class), new BiAction<BinarySpecInternal, ITaskFactory>() {
                    @Override
                    public void execute(BinarySpecInternal binary, ITaskFactory taskFactory) {
                        if (!binary.isLegacyBinary()) {
                            TaskInternal binaryLifecycleTask = taskFactory.create(binary.getProjectScopedName(), DefaultTask.class);
                            binaryLifecycleTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                            binaryLifecycleTask.setDescription(String.format("Assembles %s.", binary));
                            binary.setBuildTask(binaryLifecycleTask);
                        }
                    }
                }));
            }
        }));

        modelRegistry.getRoot().applyTo(allDescendants(), ModelActionRole.Defaults,
            DirectNodeNoInputsModelAction.of(
                ModelReference.of(BinarySpec.class),
                new SimpleModelRuleDescriptor(baseDescriptor + ComponentSpecInitializer.class.getSimpleName() + ".binaryAction()"),
                ComponentSpecInitializer.binaryAction()));
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Hidden
        @Model
        LanguageSourceSetFactory languageSourceSetFactory(ServiceRegistry serviceRegistry) {
            return new LanguageSourceSetFactory("sourceSets", serviceRegistry.get(FileResolver.class));
        }

        @Model
        void binaries(BinaryContainer binaries) {
        }

        @BinaryType
        void registerBaseBinarySpec(BinaryTypeBuilder<BinarySpec> builder) {
            builder.defaultImplementation(BaseBinarySpec.class);
            builder.internalView(BinarySpecInternal.class);
        }

        @LanguageType
        void registerBaseLanguageSourceSet(LanguageTypeBuilder<LanguageSourceSet> builder) {
            builder.defaultImplementation(BaseLanguageSourceSet.class);
            builder.internalView(LanguageSourceSetInternal.class);
        }

        @Mutate
        void registerSourceSetNodeInitializer(NodeInitializerRegistry nodeInitializerRegistry, LanguageSourceSetFactory languageSourceSetFactory) {
            nodeInitializerRegistry.registerStrategy(new FactoryBasedNodeInitializerExtractionStrategy<LanguageSourceSet>(languageSourceSetFactory));
        }

        @Model
        ProjectSourceSet sources(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(Instantiator.class).newInstance(DefaultProjectSourceSet.class);
        }

        @Validate
        void validateLanguageSourceSetRegistrations(LanguageSourceSetFactory instanceFactory) {
            instanceFactory.validateRegistrations();
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
    }
}
