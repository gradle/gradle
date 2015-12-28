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
import org.gradle.api.Nullable;
import org.gradle.api.Task;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.internal.Cast;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.*;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryTasks;

import java.util.ArrayList;
import java.util.List;

import static org.gradle.model.internal.core.NodePredicate.allLinks;

public class BinaryTasksModelRuleExtractor extends AbstractAnnotationDrivenComponentModelRuleExtractor<BinaryTasks> {
    @Nullable
    @Override
    public <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition, MethodModelRuleExtractionContext context) {
        return createRegistration(ruleDefinition, context);
    }

    private <R, S extends BinarySpec> ExtractedModelRule createRegistration(final MethodRuleDefinition<R, ?> ruleDefinition, ValidationProblemCollector problems) {
        RuleMethodDataCollector dataCollector = new RuleMethodDataCollector();
        verifyMethodSignature(dataCollector, ruleDefinition, problems);
        if (problems.hasProblems()) {
            return null;
        }

        final Class<S> binaryType = dataCollector.getParameterType(BinarySpec.class);
        return new ExtractedBinaryTasksRule<R, S>(ruleDefinition, binaryType);
    }

    private void verifyMethodSignature(RuleMethodDataCollector taskDataCollector, MethodRuleDefinition<?, ?> ruleDefinition, ValidationProblemCollector problems) {
        validateIsVoidMethod(ruleDefinition, problems);
        visitSubject(taskDataCollector, ruleDefinition, Task.class, problems);
        visitDependency(taskDataCollector, ruleDefinition, ModelType.of(BinarySpec.class), problems);
    }

    private static class BinaryTaskRule<R, T extends BinarySpec> extends ModelMapBasedRule<R, Task, T, T> {

        public BinaryTaskRule(Class<T> binaryType, MethodRuleDefinition<R, ?> ruleDefinition, ModelRuleInvoker<R> ruleInvoker) {
            super(ModelReference.of(binaryType), binaryType, ruleDefinition, ruleInvoker, ModelReference.of(ITaskFactory.class));
        }

        // TODO:DAZ Clean this up, and remove DomainObjectCollectionBackedModelMap
        protected void execute(final MutableModelNode modelNode, final T binary, List<ModelView<?>> inputs) {
            NamedEntityInstantiator<Task> taskFactory = Cast.uncheckedCast(ModelViews.getInstance(inputs.get(0), ITaskFactory.class));
            ModelMap<Task> cast = DomainObjectCollectionBackedModelMap.wrap(
                    Task.class,
                    binary.getTasks(),
                    taskFactory,
                    new Task.Namer(),
                    new Action<Task>() {
                        @Override
                        public void execute(Task task) {
                            binary.getTasks().add(task);
                            binary.builtBy(task);
                        }
                    });

            List<ModelView<?>> inputsWithBinary = new ArrayList<ModelView<?>>(inputs.size());
            inputsWithBinary.addAll(inputs.subList(1, inputs.size()));
            inputsWithBinary.add(InstanceModelView.of(getSubject().getPath(), getSubject().getType(), binary));

            invoke(inputsWithBinary, cast, binary, binary);
        }
    }

    private static class ExtractedBinaryTasksRule<R, S extends BinarySpec> implements ExtractedModelRule {
        private final MethodRuleDefinition<R, ?> ruleDefinition;
        private final Class<S> binaryType;

        public ExtractedBinaryTasksRule(MethodRuleDefinition<R, ?> ruleDefinition, Class<S> binaryType) {
            this.ruleDefinition = ruleDefinition;
            this.binaryType = binaryType;
        }

        @Override
        public ModelRuleDescriptor getDescriptor() {
            return ruleDefinition.getDescriptor();
        }

        @Override
        public void apply(MethodModelRuleApplicationContext context, MutableModelNode target) {
            final BinaryTaskRule<R, S> binaryTaskRule = new BinaryTaskRule<R, S>(binaryType, ruleDefinition, context.invokerFor(ruleDefinition));
            context.getRegistry().configure(ModelActionRole.Defaults, DirectNodeNoInputsModelAction.of(
                    ModelReference.of("binaries", BinaryContainer.class),
                    ruleDefinition.getDescriptor(),
                    new Action<MutableModelNode>() {
                        @Override
                        public void execute(MutableModelNode modelNode) {
                            modelNode.applyTo(allLinks(), ModelActionRole.Finalize, binaryTaskRule);
                        }
                    }
            ), target.getPath());
        }

        @Override
        public List<? extends Class<?>> getRuleDependencies() {
            return ImmutableList.of(ComponentModelBasePlugin.class);
        }
    }
}
