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
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.Validate;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.extract.FactoryBasedNodeInitializerExtractionStrategy;
import org.gradle.model.internal.registry.ModelReferenceNode;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryType;
import org.gradle.platform.base.BinaryTypeBuilder;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.binary.internal.BaseBinaryRules;
import org.gradle.platform.base.binary.internal.BinarySpecFactory;
import org.gradle.platform.base.internal.BinarySpecInternal;

import javax.inject.Inject;

import static org.gradle.model.internal.core.NodePredicate.allDescendants;

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
    private final ModelRegistry modelRegistry;

    @Inject
    public BinaryBasePlugin(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    public void apply(final Project target) {
        target.getPluginManager().apply(LifecycleBasePlugin.class);
        applyRules(modelRegistry);
    }

    private void applyRules(ModelRegistry modelRegistry) {
        SimpleModelRuleDescriptor ruleDescriptor = new SimpleModelRuleDescriptor(BinaryBasePlugin.class.getSimpleName() + ".apply()");
        modelRegistry.getRoot().applyTo(allDescendants(ModelNodes.withType(BinarySpec.class)), ModelActionRole.Defaults,
                DirectNodeNoInputsModelAction.of(ModelReference.of(BinarySpec.class),
                        ruleDescriptor,
                        new Action<MutableModelNode>() {
                            @Override
                            public void execute(MutableModelNode binaryNode) {
                                if (binaryNode instanceof ModelReferenceNode) {
                                    return;
                                }
                                binaryNode.applyToSelf(BaseBinaryRules.class);
                            }
                        }));
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Model
        void binaries(BinaryContainer binaries) {
        }

        @Hidden
        @Model
        BinarySpecFactory binarySpecFactory(ServiceRegistry serviceRegistry, ITaskFactory taskFactory) {
            return new BinarySpecFactory("binaries", serviceRegistry.get(Instantiator.class), taskFactory);
        }

        @Mutate
        void registerNodeInitializerExtractors(NodeInitializerRegistry nodeInitializerRegistry, BinarySpecFactory binarySpecFactory) {
            nodeInitializerRegistry.registerStrategy(new FactoryBasedNodeInitializerExtractionStrategy<BinarySpec>(binarySpecFactory));
        }

        @Validate
        void validateBinarySpecRegistrations(BinarySpecFactory instanceFactory) {
            instanceFactory.validateRegistrations();
        }

        @BinaryType
        void registerBaseBinarySpec(BinaryTypeBuilder<BinarySpec> builder) {
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
    }
}
