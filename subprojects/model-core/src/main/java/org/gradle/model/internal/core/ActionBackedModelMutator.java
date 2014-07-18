/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.List;

public class ActionBackedModelMutator<T> implements ModelMutator<T> {

    private final ModelBinding<T> binding;
    private final List<ModelBinding<?>> inputBindings;
    private final ModelRuleDescriptor sourceDescriptor;
    private final Action<? super T> action;

    public ActionBackedModelMutator(ModelBinding<T> binding, List<ModelBinding<?>> inputBindings, ModelRuleDescriptor sourceDescriptor, Action<? super T> action) {
        this.binding = binding;
        this.inputBindings = inputBindings;
        this.sourceDescriptor = sourceDescriptor;
        this.action = action;
    }

    public static <T> ModelMutator<T> of(ModelBinding<T> reference, List<ModelBinding<?>> bindings, ModelRuleDescriptor descriptor, Action<? super T> action) {
        return new ActionBackedModelMutator<T>(reference, bindings, descriptor, action);
    }

    public ModelBinding<T> getBinding() {
        return binding;
    }

    public void mutate(T object, Inputs inputs) {
        action.execute(object);
    }

    public List<ModelBinding<?>> getInputBindings() {
        return inputBindings;
    }

    public ModelRuleDescriptor getDescriptor() {
        return sourceDescriptor;
    }

}
