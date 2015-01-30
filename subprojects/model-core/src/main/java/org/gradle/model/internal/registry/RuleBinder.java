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

import net.jcip.annotations.NotThreadSafe;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@NotThreadSafe
public abstract class RuleBinder {

    private final ModelRuleDescriptor descriptor;
    private final List<? extends ModelReference<?>> inputReferences;
    private final ModelPath scope;
    private final Collection<RuleBinder> binders;

    private boolean bindingInputs;
    private int inputsBound;
    private List<ModelBinding<?>> inputBindings;

    public RuleBinder(List<? extends ModelReference<?>> inputReferences, ModelRuleDescriptor descriptor, ModelPath scope, Collection<RuleBinder> binders) {
        this.inputReferences = inputReferences;
        this.descriptor = descriptor;
        this.scope = scope;
        this.binders = binders;
        this.inputBindings = inputReferences.isEmpty() ? Collections.<ModelBinding<?>>emptyList() : Arrays.asList(new ModelBinding<?>[inputReferences.size()]); // fix size
        if (!isBound()) {
            binders.add(this);
        }
    }

    // is binding then inputs for this binder in progress?
    public boolean isBindingInputs() {
        return bindingInputs;
    }

    public void setBindingInputs(boolean bindingInputs) {
        this.bindingInputs = bindingInputs;
    }

    public List<? extends ModelReference<?>> getInputReferences() {
        return inputReferences;
    }

    public ModelBinding<?> getSubjectBinding() {
        return null;
    }

    public ModelReference<?> getSubjectReference() {
        return null;
    }

    public List<ModelBinding<?>> getInputBindings() {
        return inputBindings;
    }

    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }

    public ModelPath getScope() {
        return scope;
    }

    public void bindInput(int i, ModelNodeInternal modelNode) {
        assert this.inputBindings.get(i) == null;
        assert inputsBound < inputBindings.size();
        this.inputBindings.set(i, bind(inputReferences.get(i), modelNode));
        ++inputsBound;
        maybeFire();
    }

    protected void maybeFire() {
        if (isBound()) {
            binders.remove(this);
        }
    }

    public boolean isBound() {
        return inputsBound == inputReferences.size();
    }

    static <I> ModelBinding<I> bind(ModelReference<I> reference, ModelNodeInternal modelNode) {
        return ModelBinding.of(reference, modelNode);
    }
}
