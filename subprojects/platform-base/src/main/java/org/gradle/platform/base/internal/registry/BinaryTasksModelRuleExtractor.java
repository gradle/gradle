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
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.inspect.*;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryTasks;
import org.gradle.platform.base.plugins.BinaryBasePlugin;

import java.util.ArrayList;
import java.util.List;

import static org.gradle.model.internal.core.NodePredicate.allLinks;

public class BinaryTasksModelRuleExtractor extends AbstractAnnotationDrivenComponentModelRuleExtractor<BinaryTasks> {
    private static final ModelType<BinarySpec> BINARY_SPEC = ModelType.of(BinarySpec.class);
    private static final ModelType<ITaskFactory> TASK_FACTORY = ModelType.of(ITaskFactory.class);
    private static final ModelType<Task> TASK = ModelType.of(Task.class);
    private static final ModelReference<BinaryContainer> BINARIES_CONTAINER = ModelReference.of("binaries", ModelType.of(BinaryContainer.class));

    @Nullable
    @Override
    public <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition, MethodModelRuleExtractionContext context) {
        return createRegistration(ruleDefinition, context);
    }

    private <R, S extends BinarySpec> ExtractedModelRule createRegistration(final MethodRuleDefinition<R, ?> ruleDefinition, RuleSourceValidationProblemCollector problems) {
        RuleMethodDataCollector dataCollector = new RuleMethodDataCollector();
        verifyMethodSignature(dataCollector, ruleDefinition, problems);
        if (problems.hasProblems()) {
            return null;
        }

        ModelType<S> binaryType = dataCollector.getParameterType(BINARY_SPEC);
        return new ExtractedBinaryTasksRule<S>(ruleDefinition, binaryType);
    }

    private void verifyMethodSignature(RuleMethodDataCollector taskDataCollector, MethodRuleDefinition<?, ?> ruleDefinition, RuleSourceValidationProblemCollector problems) {
        validateIsVoidMethod(ruleDefinition, problems);
        visitSubject(taskDataCollector, ruleDefinition, TASK, problems);
        visitDependency(taskDataCollector, ruleDefinition, BINARY_SPEC, problems);
    }

    private static class BinaryTaskRule<T extends BinarySpec> extends ModelMapBasedRule<T, T> {

        public BinaryTaskRule(ModelType<T> binaryType, MethodRuleDefinition<?, ?> ruleDefinition) {
            super(ModelReference.of(binaryType), binaryType, ruleDefinition, ModelReference.of(TASK_FACTORY));
        }

        @Override
        protected void execute(ModelRuleInvoker<?> invoker, final T binary, List<ModelView<?>> inputs) {
            NamedEntityInstantiator<Task> taskFactory = Cast.uncheckedCast(ModelViews.getInstance(inputs.get(0), TASK_FACTORY));
            ModelMap<Task> cast = DomainObjectCollectionBackedModelMap.wrap(
                    "tasks",
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

            invoke(invoker, inputsWithBinary, cast, binary, binary);
        }
    }

    private static class ExtractedBinaryTasksRule<T extends BinarySpec>  extends AbstractExtractedModelRule {
        private final ModelType<T> binaryType;

        public ExtractedBinaryTasksRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<T> binaryType) {
            super(ruleDefinition);
            this.binaryType = binaryType;
        }

        @Override
        public void apply(MethodModelRuleApplicationContext context, MutableModelNode target) {
            MethodRuleDefinition<?, ?> ruleDefinition = getRuleDefinition();
            final BinaryTaskRule<T> binaryTaskRule = new BinaryTaskRule<T>(binaryType, ruleDefinition);
            final ModelAction binaryTaskAction = context.contextualize(binaryTaskRule);
            context.getRegistry().configure(ModelActionRole.Defaults, DirectNodeNoInputsModelAction.of(
                    BINARIES_CONTAINER,
                    ruleDefinition.getDescriptor(),
                    new Action<MutableModelNode>() {
                        @Override
                        public void execute(MutableModelNode modelNode) {
                            modelNode.applyTo(allLinks(), ModelActionRole.Finalize, binaryTaskAction);
                        }
                    }
            ));
        }

        @Override
        public List<? extends Class<?>> getRuleDependencies() {
            return ImmutableList.of(BinaryBasePlugin.class);
        }
    }
}
