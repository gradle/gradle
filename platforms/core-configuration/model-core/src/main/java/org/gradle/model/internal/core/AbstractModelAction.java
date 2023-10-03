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

import com.google.common.base.Preconditions;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class AbstractModelAction<T> implements ModelAction {
    public static final List<ModelReference<?>> EMPTY_MODEL_REF_LIST = Collections.emptyList();

    protected final ModelReference<T> subject;
    protected final ModelRuleDescriptor descriptor;
    protected final List<? extends ModelReference<?>> inputs;

    protected AbstractModelAction(ModelReference<T> subject, ModelRuleDescriptor descriptor, ModelReference<?>... inputs) {
        this(subject, descriptor, inputs == null ? EMPTY_MODEL_REF_LIST : Arrays.asList(inputs));
    }

    protected AbstractModelAction(ModelReference<T> subject, ModelRuleDescriptor descriptor, List<? extends ModelReference<?>> inputs) {
        this.subject = Preconditions.checkNotNull(subject, "subject");
        this.descriptor = Preconditions.checkNotNull(descriptor, "descriptor");
        Preconditions.checkNotNull(inputs, "inputs");
        this.inputs = inputs.isEmpty() ? EMPTY_MODEL_REF_LIST : inputs;
    }

    @Override
    final public ModelReference<T> getSubject() {
        return subject;
    }

    @Override
    final public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    final public List<? extends ModelReference<?>> getInputs() {
        return inputs;
    }
}
