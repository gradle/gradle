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

import org.gradle.model.internal.core.ModelBinding;
import org.gradle.model.internal.core.ModelCreator;

import java.util.List;

class BoundModelCreator {

    private final ModelCreator creator;
    private final List<ModelBinding<?>> inputs;

    BoundModelCreator(ModelCreator creator, List<ModelBinding<?>> inputs) {
        this.creator = creator;
        this.inputs = inputs;
    }

    public ModelCreator getCreator() {
        return creator;
    }

    public List<ModelBinding<?>> getInputs() {
        return inputs;
    }
}
