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
import org.gradle.model.ModelPath;

class ModelMutation<T> {

    private final ModelMutator<T> mutator;
    private final ImmutableList<ModelPath> inputPaths;

    public ModelMutation(ModelMutator<T> mutator, ImmutableList<ModelPath> inputPaths) {
        this.mutator = mutator;
        this.inputPaths = inputPaths;
    }

    public ModelMutator<T> getMutator() {
        return mutator;
    }

    public ImmutableList<ModelPath> getInputPaths() {
        return inputPaths;
    }
}
