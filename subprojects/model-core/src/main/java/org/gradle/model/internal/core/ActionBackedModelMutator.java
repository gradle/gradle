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

    private final ModelReference<T> reference;
    private final List<? extends ModelReference<?>> inputBindings;
    private final ModelRuleDescriptor sourceDescriptor;
    private final Action<? super T> action;

    public ActionBackedModelMutator(ModelReference<T> reference, List<? extends ModelReference<?>> inputBindings, ModelRuleDescriptor sourceDescriptor, Action<? super T> action) {
        this.reference = reference;
        this.inputBindings = inputBindings;
        this.sourceDescriptor = sourceDescriptor;
        this.action = action;
    }

    public static <T> ModelMutator<T> of(ModelReference<T> reference, List<? extends ModelReference<?>> bindings, ModelRuleDescriptor descriptor, Action<? super T> action) {
        return new ActionBackedModelMutator<T>(reference, bindings, descriptor, action);
    }

    public ModelReference<T> getReference() {
        return reference;
    }

    public void mutate(T object, Inputs inputs) {
        action.execute(object);
    }

    public List<? extends ModelReference<?>> getInputBindings() {
        return inputBindings;
    }

    public ModelRuleDescriptor getSourceDescriptor() {
        return sourceDescriptor;
    }

}
