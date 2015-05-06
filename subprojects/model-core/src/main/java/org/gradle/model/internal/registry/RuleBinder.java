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
import org.gradle.api.Action;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@NotThreadSafe
public abstract class RuleBinder {

    private final ModelRuleDescriptor descriptor;
    private final List<? extends ModelReference<?>> inputReferences;
    private final Collection<RuleBinder> binders;

    private int inputsBound;
    private List<ModelBinding> inputBindings;
    private Action<ModelNodeInternal> inputBindAction;

    public RuleBinder(List<? extends ModelReference<?>> inputReferences, ModelRuleDescriptor descriptor, Collection<RuleBinder> binders) {
        this.inputReferences = inputReferences;
        this.descriptor = descriptor;
        this.binders = binders;
        inputBindAction = new Action<ModelNodeInternal>() {
            @Override
            public void execute(ModelNodeInternal nodeInternal) {
                ++inputsBound;
                maybeFire();
            }
        };
        this.inputBindings = inputBindings(inputReferences);
        if (!isBound()) {
            binders.add(this);
        }
    }

    private List<ModelBinding> inputBindings(List<? extends ModelReference<?>> inputReferences) {
        if (inputReferences.isEmpty()) {
            return Collections.emptyList();
        }
        List<ModelBinding> bindings = new ArrayList<ModelBinding>(inputReferences.size());
        for (ModelReference<?> inputReference : inputReferences) {
            bindings.add(binding(inputReference, false, inputBindAction));
        }
        return bindings;
    }

    protected ModelBinding binding(ModelReference<?> reference, boolean writable, Action<ModelNodeInternal> bindAction) {
        if (reference.getPath() != null) {
            return new PathBinderCreationListener(descriptor, reference, writable, bindAction);
        }
        return new OneOfTypeBinderCreationListener(descriptor, reference, writable, bindAction);
    }

    public ModelBinding getSubjectBinding() {
        return null;
    }

    public List<ModelBinding> getInputBindings() {
        return inputBindings;
    }

    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }

    protected void maybeFire() {
        if (isBound()) {
            binders.remove(this);
        }
    }

    public boolean isBound() {
        return inputsBound == inputReferences.size();
    }
}
