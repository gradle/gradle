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
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.ModelSchema;

import java.util.List;

public class RuleMethodBackedMutationAction<T> implements BiAction<MutableModelNode, Inputs> {
    private final ModelSchema<T> schema;
    private final ModelRuleInvoker<?> ruleInvoker;
    private final ModelRuleDescriptor descriptor;
    private final List<ModelReference<?>> inputReferences;

    public RuleMethodBackedMutationAction(ModelSchema<T> schema, ModelRuleInvoker<?> ruleInvoker, ModelRuleDescriptor descriptor, List<ModelReference<?>> inputReferences) {
        this.schema = schema;
        this.ruleInvoker = ruleInvoker;
        this.descriptor = descriptor;
        this.inputReferences = inputReferences;
    }

    public void execute(MutableModelNode modelNode, Inputs inputs) {
        ModelView<? extends T> modelView = modelNode.asWritable(schema.getType(), descriptor, inputs);
        if (modelView == null) {
            throw new IllegalStateException("Couldn't produce managed node as schema type");
        }

        Object[] args = new Object[inputs.size() + 1];
        args[0] = modelView.getInstance();
        for (int i = 0; i < inputs.size(); i++) {
            args[i + 1] = inputs.get(i, inputReferences.get(i).getType()).getInstance();
        }
        ruleInvoker.invoke(args);
        modelView.close();
    }
}
