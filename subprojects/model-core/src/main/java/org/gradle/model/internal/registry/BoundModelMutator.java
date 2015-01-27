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

import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.core.ModelAction;
import org.gradle.model.internal.core.ModelReference;

import java.util.List;

@ThreadSafe
class BoundModelMutator<T> {

    private final ModelAction<T> mutator;
    private final ModelNodeInternal subject;
    private final ModelReference<T> subjectReference;
    private final List<ModelBinding<?>> inputs;

    BoundModelMutator(ModelAction<T> mutator, ModelNodeInternal subject, ModelReference<T> subjectReference, List<ModelBinding<?>> inputs) {
        this.mutator = mutator;
        this.subject = subject;
        this.subjectReference = subjectReference;
        this.inputs = inputs;
    }

    public ModelAction<T> getMutator() {
        return mutator;
    }

    public ModelNodeInternal getSubject() {
        return subject;
    }

    public List<ModelBinding<?>> getInputs() {
        return inputs;
    }

    public ModelReference<T> getSubjectReference() {
        return subjectReference;
    }
}
