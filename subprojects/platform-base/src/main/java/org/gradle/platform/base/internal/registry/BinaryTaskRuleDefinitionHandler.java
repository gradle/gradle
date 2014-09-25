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
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.inspect.RuleSourceDependencies;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryTask;
import org.gradle.platform.base.InvalidComponentModelException;

import java.util.List;

public class BinaryTaskRuleDefinitionHandler extends AbstractAnnotationDrivenMethodComponentRuleDefinitionHandler<BinaryTask> {

    public <R> void register(final MethodRuleDefinition<R> ruleDefinition, final ModelRegistry modelRegistry, RuleSourceDependencies dependencies) {
        try {
            RuleMethodDataCollector dataCollector = new RuleMethodDataCollector();
            verifyMethodSignature(dataCollector, ruleDefinition);

            Class<? extends BinarySpec> binaryType =  dataCollector.getParameterType(BinarySpec.class);
            dependencies.add(ComponentModelBasePlugin.class);

            final ModelReference<CollectionBuilder<? extends Task>> tasks = ModelReference.of(ModelPath.path("tasks"), new ModelType<CollectionBuilder<? extends Task>>() {
            });

            modelRegistry.mutate(new BinaryTaskRule(tasks, binaryType, ruleDefinition, modelRegistry));

        } catch (InvalidComponentModelException e) {
            invalidModelRule(ruleDefinition, e);
        }
    }

    private <R> void verifyMethodSignature(RuleMethodDataCollector taskDataCollector, MethodRuleDefinition<R> ruleDefinition) {
        assertIsVoidMethod(ruleDefinition);
        visitCollectionBuilderSubject(taskDataCollector, ruleDefinition, Task.class);
        visitDependency(taskDataCollector, ruleDefinition, BinarySpec.class);
    }

    //TODO extract common general method reusable by all AnnotationRuleDefinitionHandler
    protected <R> void invalidModelRule(MethodRuleDefinition<R> ruleDefinition, InvalidComponentModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(" is not a valid BinaryTask model rule method.");
        throw new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    private class BinaryTaskRule<R, T extends BinarySpec> implements ModelMutator<CollectionBuilder<Task>> {

        private final ModelReference<CollectionBuilder<Task>> subject;
        private final Class<T> binaryType;
        private final MethodRuleDefinition<R> ruleDefinition;
        private final ModelRegistry modelRegistry;
        private final ImmutableList<ModelReference<?>> inputs;

        public BinaryTaskRule(ModelReference<CollectionBuilder<Task>> subject,
                                     Class<T> binaryType,
                                     MethodRuleDefinition<R> ruleDefinition, ModelRegistry modelRegistry) {
            this.subject = subject;
            this.binaryType = binaryType;
            this.ruleDefinition = ruleDefinition;
            this.modelRegistry = modelRegistry;
            this.inputs =  ImmutableList.<ModelReference<?>>of(ModelReference.of(ProjectIdentifier.class));

        }

        public ModelReference<CollectionBuilder<Task>> getSubject() {
            return subject;
        }

        public void mutate(CollectionBuilder<Task> object, Inputs inputs) {

        }

        public List<ModelReference<?>> getInputs() {
            return inputs;
        }


        public ModelRuleDescriptor getDescriptor() {
            return ruleDefinition.getDescriptor();
        }
    }
}
