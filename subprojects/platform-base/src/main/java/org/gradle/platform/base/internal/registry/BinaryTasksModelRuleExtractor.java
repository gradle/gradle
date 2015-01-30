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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.internal.Cast;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryTasks;
import org.gradle.platform.base.InvalidModelException;

import java.util.List;

public class BinaryTasksModelRuleExtractor extends AbstractAnnotationDrivenComponentModelRuleExtractor<BinaryTasks> {

    public <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition) {
        return createRegistration(ruleDefinition);
    }

    private <R, S extends BinarySpec> ExtractedModelRule createRegistration(MethodRuleDefinition<R, ?> ruleDefinition) {
        try {
            RuleMethodDataCollector dataCollector = new RuleMethodDataCollector();
            verifyMethodSignature(dataCollector, ruleDefinition);

            final Class<S> binaryType = dataCollector.getParameterType(BinarySpec.class);

            final BinaryTaskRule<R, S> binaryTaskRule = new BinaryTaskRule<R, S>(binaryType, ruleDefinition);
            ImmutableList<ModelType<?>> dependencies = ImmutableList.<ModelType<?>>of(ModelType.of(ComponentModelBasePlugin.class));
            return new ExtractedModelMutator(ModelActionRole.Defaults, dependencies, DirectNodeModelAction.of(ModelReference.of("binaries"), new SimpleModelRuleDescriptor("binaries*.create()"), new Action<MutableModelNode>() {
                @Override
                public void execute(MutableModelNode modelNode) {
                    modelNode.applyToAllLinks(ModelActionRole.Finalize, binaryTaskRule);
                }
            }));
        } catch (InvalidModelException e) {
            throw invalidModelRule(ruleDefinition, e);
        }
    }

    private void verifyMethodSignature(RuleMethodDataCollector taskDataCollector, MethodRuleDefinition<?, ?> ruleDefinition) {
        assertIsVoidMethod(ruleDefinition);
        visitCollectionBuilderSubject(taskDataCollector, ruleDefinition, Task.class);
        visitDependency(taskDataCollector, ruleDefinition, ModelType.of(BinarySpec.class));
    }

    //TODO extract common general method reusable by all AnnotationRuleDefinitionHandler
    protected InvalidModelRuleDeclarationException invalidModelRule(MethodRuleDefinition<?, ?> ruleDefinition, InvalidModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(" is not a valid BinaryTask model rule method.");
        return new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    private class BinaryTaskRule<R, T extends BinarySpec> extends CollectionBuilderBasedRule<R, Task, T, T> {

        public BinaryTaskRule(Class<T> binaryType, MethodRuleDefinition<R, ?> ruleDefinition) {
            super(ModelReference.of(binaryType), binaryType, ruleDefinition, ModelReference.of(ITaskFactory.class));
        }

        public void execute(MutableModelNode modelNode, T binary, List<ModelView<?>> inputs) {
            ITaskFactory taskFactory = ModelViews.assertType(inputs.get(0), ModelType.of(ITaskFactory.class)).getInstance();
            NamedEntityInstantiator<Task> instantiator = new Instantiator(binary, taskFactory);
            DefaultCollectionBuilder<Task> collectionBuilder = new DefaultCollectionBuilder<Task>(
                    ModelType.of(Task.class),
                    instantiator,
                    binary.getTasks(),
                    getDescriptor(),
                    modelNode
            ) {
                //eagerly instantiate created tasks so that they get attached to their respective binary specs in the instantiator
                @Override
                public void create(String name) {
                    super.create(name);
                    get(name);
                }

                @Override
                public void create(String name, Action<? super Task> configAction) {
                    super.create(name, configAction);
                    get(name);
                }

                @Override
                public <S extends Task> void create(String name, Class<S> type) {
                    super.create(name, type);
                    get(name);
                }

                @Override
                public <S extends Task> void create(String name, Class<S> type, Action<? super S> configAction) {
                    super.create(name, type, configAction);
                    get(name);
                }
            };

            invoke(inputs, collectionBuilder, binary, taskFactory);
        }
    }

    private class Instantiator implements NamedEntityInstantiator<Task> {
        private final BinarySpec binarySpec;
        private final ITaskFactory taskFactory;

        public Instantiator(BinarySpec binarySpec, ITaskFactory taskFactory) {
            this.binarySpec = binarySpec;
            this.taskFactory = taskFactory;
        }

        public <U extends Task> U create(String name, Class<U> type) {
            Class<? extends TaskInternal> castType = Cast.uncheckedCast(type);
            TaskInternal taskInternal = doCreate(name, castType);
            return Cast.uncheckedCast(taskInternal);
        }

        public <U extends TaskInternal> U doCreate(String name, Class<U> type) {
            U task = taskFactory.create(name, type);
            binarySpec.builtBy(task);
            binarySpec.getTasks().add(task);
            return task;
        }
    }
}
