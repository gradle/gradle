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

import com.google.common.collect.ImmutableList;
import org.gradle.model.internal.core.ModelElement;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.core.rule.Inputs;

import java.util.Iterator;

public class DefaultInputs implements Inputs {

    private final ImmutableList<ModelElement<?>> inputs;

    public DefaultInputs(ImmutableList<ModelElement<?>> inputs) {
        this.inputs = inputs;
    }

    public <T> ModelElement<? extends T> get(int i, ModelType<T> type) {
        ModelElement<?> input = inputs.get(i);
        ModelType<?> inputType = input.getReference().getType();
        if (type.isAssignableFrom(inputType)) {
            @SuppressWarnings("unchecked") ModelElement<? extends T> cast = (ModelElement<? extends T>) input;
            return cast;
        } else {
            throw new RuntimeException("Can't convert input '" + i + "' with type '" + inputType + "' to type '" + type + "'");
        }
    }

    public int size() {
        return inputs.size();
    }

    public Iterator<ModelElement<?>> iterator() {
        return inputs.iterator();
    }
}
