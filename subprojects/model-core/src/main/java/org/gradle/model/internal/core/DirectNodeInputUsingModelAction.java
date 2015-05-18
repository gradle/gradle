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

package org.gradle.model.internal.core;

import org.gradle.internal.TriAction;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.List;

public class DirectNodeInputUsingModelAction<T> implements ModelAction<T> {
    private final ModelReference<T> subject;
    private final ModelRuleDescriptor descriptor;
    private final List<ModelReference<?>> inputs;
    private final TriAction<? super MutableModelNode, ? super T, ? super List<ModelView<?>>> action;

    public DirectNodeInputUsingModelAction(ModelReference<T> subject, ModelRuleDescriptor descriptor, List<ModelReference<?>> inputs,
                                           TriAction<? super MutableModelNode, ? super T, ? super List<ModelView<?>>> action) {
        this.subject = subject;
        this.descriptor = descriptor;
        this.inputs = inputs;
        this.action = action;
    }

    public static <T> DirectNodeInputUsingModelAction<T> of(ModelReference<T> modelReference, ModelRuleDescriptor descriptor, List<ModelReference<?>> inputs,
                                                      TriAction<? super MutableModelNode, ? super T, ? super List<ModelView<?>>> action) {
        return new DirectNodeInputUsingModelAction<T>(modelReference, descriptor, inputs, action);
    }

    @Override
    public ModelReference<T> getSubject() {
        return subject;
    }

    @Override
    public void execute(MutableModelNode modelNode, T object, List<ModelView<?>> inputs) {
        action.execute(modelNode, object, inputs);
    }

    @Override
    public List<ModelReference<?>> getInputs() {
        return inputs;
    }

    @Override
    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }
}
