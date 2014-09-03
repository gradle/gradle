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

package org.gradle.model.internal.registry;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.gradle.model.internal.core.*;

import java.util.List;

public class DefaultInputs implements Inputs {

    private final List<ModelRuleInput<?>> inputs;

    public DefaultInputs(List<ModelRuleInput<?>> inputs) {
        this.inputs = inputs;
    }

    public <T> ModelView<? extends T> get(int i, ModelType<T> type) {
        ModelRuleInput<?> input = inputs.get(i);
        ModelView<?> untypedView = input.getView();
        if (type.isAssignableFrom(untypedView.getType())) {
            @SuppressWarnings("unchecked") ModelView<? extends T> view = (ModelView<? extends T>) untypedView;
            return view;
        } else {
            // TODO better exception type
            throw new IllegalArgumentException("Can't view input '" + i + "' (" + input.getView().getType() + ") as type '" + type + "'");
        }
    }

    public int size() {
        return inputs.size();
    }

    public List<ModelReference<?>> getReferences() {
        return Lists.transform(inputs, new Function<ModelRuleInput<?>, ModelReference<?>>() {
            public ModelReference<?> apply(ModelRuleInput<?> input) {
                return input.getBinding().getReference();
            }
        });
    }

    public List<ModelBinding<?>> getBindings() {
        return Lists.transform(inputs, new Function<ModelRuleInput<?>, ModelBinding<?>>() {
            public ModelBinding<?> apply(ModelRuleInput<?> input) {
                return input.getBinding();
            }
        });
    }

    public List<ModelRuleInput<?>> getRuleInputs() {
        return inputs;
    }
}
