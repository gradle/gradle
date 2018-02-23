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

package org.gradle.model.dsl.internal.inputs;

import org.gradle.model.internal.core.ModelView;

import java.util.List;
import java.util.Map;

public class PotentialInputs {

    private final Map<String, PotentialInput> inputs;
    private final List<ModelView<?>> modelViews;

    public PotentialInputs(List<ModelView<?>> modelViews, Map<String, PotentialInput> inputs) {
        this.modelViews = modelViews;
        this.inputs = inputs;
    }

    public Object get(String path) {
        PotentialInput potentialInput = inputs.get(path);
        if (potentialInput == null) {
            throw new IllegalStateException("no input for " + path);
        }
        return potentialInput.get(modelViews);
    }

}
