/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal.registry;

import org.gradle.api.Task;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.DefaultCollectionBuilder;
import org.gradle.model.internal.core.NamedEntityInstantiator;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.inspect.RuleSourceDependencies;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryTasks;
import org.gradle.platform.base.InvalidComponentModelException;

public class BinaryTasksRuleDefinitionHandler extends AbstractAnnotationDrivenMethodComponentRuleDefinitionHandler<BinaryTasks> {

    public <R> void register(final MethodRuleDefinition<R> ruleDefinition, final ModelRegistry modelRegistry, RuleSourceDependencies dependencies) {
        doRegister(ruleDefinition, modelRegistry, dependencies);

    }

    private <R, S extends BinarySpec> void doRegister(MethodRuleDefinition<R> ruleDefinition, ModelRegistry modelRegistry, RuleSourceDependencies dependencies) {
        try {
            RuleMethodDataCollector dataCollector = new RuleMethodDataCollector();
            verifyMethodSignature(dataCollector, ruleDefinition);

            Class<S> binaryType = dataCollector.getParameterType(BinarySpec.class);
            dependencies.add(ComponentModelBasePlugin.class);

            final ModelReference<TaskContainer> tasks = ModelReference.of(ModelPath.path("tasks"), new ModelType<TaskContainer>() {
            });

            modelRegistry.mutate(new BinaryTaskRule<R, S>(tasks, binaryType, ruleDefinition));

        } catch (InvalidComponentModelException e) {
            invalidModelRule(ruleDefinition, e);
        }
    }

    private <R> void verifyMethodSignature(RuleMethodDataCollector taskDataCollector, MethodRuleDefinition<R> ruleDefinition) {
        assertIsVoidMethod(ruleDefinition);
        visitCollectionBuilderSubject(taskDataCollector, ruleDefinition, Task.class);
        visitDependency(taskDataCollector, ruleDefinition, ModelType.of(BinarySpec.class));
    }

    //TODO extract common general method reusable by all AnnotationRuleDefinitionHandler
    protected <R> void invalidModelRule(MethodRuleDefinition<R> ruleDefinition, InvalidComponentModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(" is not a valid BinaryTask model rule method.");
        throw new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    private class BinaryTaskRule<R, T extends BinarySpec> extends CollectionBuilderBasedRule<R, Task, T, TaskContainer> {

        private final Class<T> binaryType;

        public BinaryTaskRule(ModelReference<TaskContainer> subject, final Class<T> binaryType, MethodRuleDefinition<R> ruleDefinition) {
            super(subject, binaryType, ruleDefinition, ModelReference.of("binaries", BinaryContainer.class));
            this.binaryType = binaryType;
        }

        public void mutate(ModelNode modelNode, TaskContainer container, Inputs inputs) {
            BinaryContainer binaries = inputs.get(0, ModelType.of(BinaryContainer.class)).getInstance();
            for (T binary : binaries.withType(binaryType)) {
                NamedEntityInstantiator<Task> instantiator = new Instantiator(binary, container);
                DefaultCollectionBuilder<Task> collectionBuilder = new DefaultCollectionBuilder<Task>(
                        instantiator,
                        new SimpleModelRuleDescriptor("Project.<init>.tasks()"),
                        modelNode
                );

                invoke(inputs, collectionBuilder, binary, binaries);
            }
        }
    }

    private class Instantiator implements NamedEntityInstantiator<Task> {
        private final BinarySpec binarySpec;
        private final TaskContainer container;

        public Instantiator(BinarySpec binarySpec, TaskContainer container) {
            this.binarySpec = binarySpec;
            this.container = container;
        }

        public ModelType<Task> getType() {
            return ModelType.of(Task.class);
        }

        public Task create(String name) {
            Task task = container.create(name);
            binarySpec.builtBy(task);
            binarySpec.getTasks().add(task);
            return task;
        }

        public <U extends Task> U create(String name, Class<U> type) {
            U task = container.create(name, type);
            binarySpec.builtBy(task);
            binarySpec.getTasks().add(task);
            return task;
        }
    }
}
