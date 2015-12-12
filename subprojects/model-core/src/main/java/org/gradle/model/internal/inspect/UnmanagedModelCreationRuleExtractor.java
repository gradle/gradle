/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.inspect;

import org.gradle.internal.BiAction;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

public class UnmanagedModelCreationRuleExtractor extends AbstractModelCreationRuleExtractor {
    @Override
    public boolean isSatisfiedBy(MethodRuleDefinition<?, ?> element) {
        return super.isSatisfiedBy(element) && !element.getReturnType().equals(ModelType.of(Void.TYPE));
    }

    @Override
    protected <R, S> void buildRegistration(MethodRuleDefinition<R, S> ruleDefinition, ModelPath modelPath, ModelRegistrations.Builder registration, ValidationProblemCollector problems) {
        ModelType<R> modelType = ruleDefinition.getReturnType();
        List<ModelReference<?>> references = ruleDefinition.getReferences();
        ModelRuleDescriptor descriptor = ruleDefinition.getDescriptor();

        BiAction<MutableModelNode, List<ModelView<?>>> transformer = new ModelRuleInvokerBackedTransformer<R>(modelType, ruleDefinition.getRuleInvoker(), descriptor);
        registration.action(ModelActionRole.Create, references, transformer);
        registration.withProjection(new UnmanagedModelProjection<R>(modelType, true, true));
    }

    public String getDescription() {
        return String.format("%s and returning a model element", super.getDescription());
    }

    private static class ModelRuleInvokerBackedTransformer<T> implements BiAction<MutableModelNode, List<ModelView<?>>> {

        private final ModelType<T> type;
        private final ModelRuleDescriptor descriptor;
        private final ModelRuleInvoker<T> ruleInvoker;

        private ModelRuleInvokerBackedTransformer(ModelType<T> type, ModelRuleInvoker<T> ruleInvoker, ModelRuleDescriptor descriptor) {
            this.type = type;
            this.descriptor = descriptor;
            this.ruleInvoker = ruleInvoker;
        }

        public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
            T instance;
            if (inputs.size() == 0) {
                instance = ruleInvoker.invoke();
            } else {
                Object[] args = new Object[inputs.size()];
                for (int i = 0; i < inputs.size(); i++) {
                    args[i] = inputs.get(i).getInstance();
                }

                instance = ruleInvoker.invoke(args);
            }
            if (instance == null) {
                throw new ModelRuleExecutionException(descriptor, "rule returned null");
            }
            modelNode.setPrivateData(type, instance);
        }
    }
}
