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

import org.gradle.internal.BiAction;
import org.gradle.model.internal.core.Inputs;
import org.gradle.model.internal.core.ModelReference;

import java.util.List;

public class RuleMethodBackedMutationAction<T> implements BiAction<T, Inputs> {
    private final ModelRuleInvoker<?> ruleInvoker;
    private final List<ModelReference<?>> inputReferences;

    public RuleMethodBackedMutationAction(ModelRuleInvoker<?> ruleInvoker, List<ModelReference<?>> inputReferences) {
        this.ruleInvoker = ruleInvoker;
        this.inputReferences = inputReferences;
    }

    public void execute(T subject, Inputs inputs) {
        Object[] args = new Object[inputs.size() + 1];
        args[0] = subject;
        for (int i = 0; i < inputs.size(); i++) {
            args[i + 1] = inputs.get(i, inputReferences.get(i).getType()).getInstance();
        }
        ruleInvoker.invoke(args);
    }
}
