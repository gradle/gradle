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

import org.gradle.model.internal.core.ModelAction;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.List;

class MethodBackedModelAction<T> implements ModelAction<T> {
    private final ModelRuleDescriptor descriptor;
    private final ModelReference<T> subject;
    private final List<ModelReference<?>> inputs;
    private final ModelRuleInvoker<?> ruleInvoker;

    public MethodBackedModelAction(MethodRuleDefinition<?, T> ruleDefinition) {
        this(ruleDefinition.getRuleInvoker(), ruleDefinition.getDescriptor(), ruleDefinition.getSubjectReference(), ruleDefinition.getTailReferences());
    }

    public MethodBackedModelAction(ModelRuleInvoker<?> ruleInvoker, ModelRuleDescriptor descriptor, ModelReference<T> subject, List<ModelReference<?>> inputs) {
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

    @Override
    public void execute(MutableModelNode modelNode, T object, List<ModelView<?>> inputs) {
        Object[] args = new Object[1 + this.inputs.size()];
        args[0] = object;
        for (int i = 0; i < this.inputs.size(); ++i) {
            args[i + 1] = inputs.get(i).getInstance();
        }
        ruleInvoker.invoke(args);
    }
}
