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

package org.gradle.model.internal;

import com.google.common.collect.ImmutableList;

public class DefaultInputs implements Inputs {

    private final ImmutableList<?> inputs;

    public DefaultInputs(ImmutableList<?> inputs) {
        this.inputs = inputs;
    }

    public <T> T get(int i, Class<T> type) {
        Object input = inputs.get(i);
        if (type.isInstance(input)) {
            return type.cast(input);
        } else {
            throw new RuntimeException("Can't convert input '" + i + "' with type '" + input.getClass() + "' to type '" + type + "'");
        }
    }

    public int size() {
        return inputs.size();
    }

}
