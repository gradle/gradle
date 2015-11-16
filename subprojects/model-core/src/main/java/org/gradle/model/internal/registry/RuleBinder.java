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
import org.gradle.api.Nullable;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@NotThreadSafe
abstract class RuleBinder {

    private final ModelRuleDescriptor descriptor;
    private final BindingPredicate subjectReference;
    private final List<BindingPredicate> inputReferences;
    private final Collection<RuleBinder> binders;

    private int inputsBound;
    private List<ModelBinding> inputBindings;
    private Action<ModelBinding> inputBindAction;

    public RuleBinder(BindingPredicate subjectReference, List<BindingPredicate> inputReferences, ModelRuleDescriptor descriptor, Collection<RuleBinder> binders) {
        this.subjectReference = subjectReference;
        this.inputReferences = inputReferences;
        this.descriptor = descriptor;
        this.binders = binders;
        inputBindAction = new Action<ModelBinding>() {
            @Override
            public void execute(ModelBinding modelBinding) {
                ModelNodeInternal node = modelBinding.getNode();
                BindingPredicate reference = modelBinding.getPredicate();
                if (reference.getState() != null && node.getState().compareTo(reference.getState()) > 0) {
                    throw new IllegalStateException(String.format("Cannot add rule %s with input model element '%s' at state %s as this element is already at state %s.",
                        modelBinding.referrer,
                        node.getPath(),
                        reference.getState(),
                        node.getState()
                    ));
                }
                ++inputsBound;
                maybeFire();
            }
        };
        this.inputBindings = inputBindings(inputReferences);
        if (!isBound()) {
            binders.add(this);
        }
    }

    private List<ModelBinding> inputBindings(List<BindingPredicate> inputReferences) {
        if (inputReferences.isEmpty()) {
            return Collections.emptyList();
        }
        List<ModelBinding> bindings = new ArrayList<ModelBinding>(inputReferences.size());
        for (BindingPredicate inputReference : inputReferences) {
            bindings.add(binding(inputReference, false, inputBindAction));
        }
        return bindings;
    }

    protected ModelBinding binding(BindingPredicate reference, boolean writable, Action<ModelBinding> bindAction) {
        if (reference.getPath() != null) {
            return new PathBinderCreationListener(descriptor, reference, writable, bindAction);
        }
        return new OneOfTypeBinderCreationListener(descriptor, reference, writable, bindAction);
    }

    /**
     * Returns the reference to the <em>output</em> of the rule. The state returned from {@link BindingPredicate#getState()} should reflect
     * the target state, not the input state. Implicitly, a rule accepts as input the subject in the state that is the predecessor of the target state.
     */
    public BindingPredicate getSubjectReference() {
        return subjectReference;
    }

    /**
     * A rule may have a subject binding, but may not require it. All rules, however, have a subject and hence a subject reference.
     */
    @Nullable
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
