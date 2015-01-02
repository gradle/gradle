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

package org.gradle.model.internal.inspect;

import net.jcip.annotations.ThreadSafe;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.lang.annotation.Annotation;
import java.util.List;

@ThreadSafe
public abstract class AbstractMutationRuleDefinitionHandler<T extends Annotation> extends AbstractAnnotationDrivenMethodRuleDefinitionHandler<T> {

    public <R> ModelRuleRegistration registration(MethodRuleDefinition<R> ruleDefinition, RuleSourceDependencies dependencies) {
        validate(ruleDefinition);

        List<ModelReference<?>> bindings = ruleDefinition.getReferences();

        ModelReference<?> subject = bindings.get(0);
        List<ModelReference<?>> inputs = bindings.subList(1, bindings.size());
        MethodModelAction<?> mutator = toMutator(ruleDefinition, subject, inputs);

        return new ModelMutatorRegistration(getMutationType(), mutator);
    }

    protected abstract ModelActionRole getMutationType();

    private <R> void validate(MethodRuleDefinition<R> ruleDefinition) {
        if (!ruleDefinition.getReturnType().getRawClass().equals(Void.TYPE)) {
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), "only void can be used as return type for mutation rules");
        }
    }

    private static <T> MethodModelAction<T> toMutator(MethodRuleDefinition<?> ruleDefinition, ModelReference<T> first, List<ModelReference<?>> tail) {
        return new MethodModelAction<T>(ruleDefinition.getRuleInvoker(), ruleDefinition.getDescriptor(), first, tail);
    }

    private static class MethodModelAction<T> implements ModelAction<T> {
        private final ModelRuleDescriptor descriptor;
        private final ModelReference<T> subject;
        private final List<ModelReference<?>> inputs;
        private final ModelRuleInvoker<?> ruleInvoker;

        public MethodModelAction(ModelRuleInvoker<?> ruleInvoker, ModelRuleDescriptor descriptor, ModelReference<T> subject, List<ModelReference<?>> inputs) {
            this.ruleInvoker = ruleInvoker;
            this.subject = subject;
            this.inputs = inputs;
            this.descriptor = descriptor;
        }

        public ModelRuleDescriptor getDescriptor() {
            return descriptor;
        }

        public ModelReference<T> getSubject() {
            return subject;
        }

        public List<ModelReference<?>> getInputs() {
            return inputs;
        }

        public void execute(MutableModelNode modelNode, T object, Inputs inputs) {
            Object[] args = new Object[1 + this.inputs.size()];
            args[0] = object;
            for (int i = 0; i < inputs.size(); ++i) {
                args[i + 1] = inputs.get(i, this.inputs.get(i).getType()).getInstance();
            }
            ruleInvoker.invoke(args);
        }
    }
}
