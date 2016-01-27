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
import org.gradle.model.internal.core.ModelAction;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@NotThreadSafe
public class RuleBinder {

    private final ModelBinding subjectBinding;
    private final ModelAction action;
    private final List<BindingPredicate> inputReferences;
    private final Collection<RuleBinder> binders;

    private int inputsBound;
    private final List<ModelBinding> inputBindings;

    public RuleBinder(BindingPredicate subjectReference, List<BindingPredicate> inputReferences, ModelAction action, Collection<RuleBinder> binders) {
        this.action = action;
        this.inputReferences = inputReferences;
        this.binders = binders;
        this.subjectBinding = binding(subjectReference, action.getDescriptor(), true, new Action<ModelBinding>() {
            @Override
            public void execute(ModelBinding modelBinding) {
                ModelNodeInternal node = modelBinding.getNode();
                BindingPredicate predicate = modelBinding.getPredicate();
                if (node.isAtLeast(predicate.getState())) {
                    throw new IllegalStateException(String.format("Cannot add rule %s for model element '%s' at state %s as this element is already at state %s.",
                        modelBinding.referrer,
                        node.getPath(),
                        predicate.getState().previous(),
                        node.getState()
                    ));
                }
                maybeFire();
            }
        });
        this.inputBindings = inputBindings(inputReferences, action.getDescriptor(), new Action<ModelBinding>() {
            @Override
            public void execute(ModelBinding modelBinding) {
                ModelNodeInternal node = modelBinding.getNode();
                BindingPredicate reference = modelBinding.getPredicate();
                if (node.getState().compareTo(reference.getState()) > 0) {
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
        });
        if (!isBound()) {
            binders.add(this);
        }
    }

    private static List<ModelBinding> inputBindings(List<BindingPredicate> inputReferences, ModelRuleDescriptor descriptor, Action<ModelBinding> inputBindAction) {
        if (inputReferences.isEmpty()) {
            return Collections.emptyList();
        }
        List<ModelBinding> bindings = new ArrayList<ModelBinding>(inputReferences.size());
        for (BindingPredicate inputReference : inputReferences) {
            bindings.add(binding(inputReference, descriptor, false, inputBindAction));
        }
        return bindings;
    }

    private static ModelBinding binding(BindingPredicate reference, ModelRuleDescriptor descriptor, boolean writable, Action<ModelBinding> bindAction) {
        if (reference.getPath() != null) {
            return new PathBinderCreationListener(descriptor, reference, writable, bindAction);
        }
        return new OneOfTypeBinderCreationListener(descriptor, reference, writable, bindAction);
    }

    /**
     * Returns the rule being bound.
     */
    public ModelAction getAction() {
        return action;
    }

    /**
     * Returns the subject binding for the rule.
     */
    public ModelBinding getSubjectBinding() {
        return subjectBinding;
    }

    public List<ModelBinding> getInputBindings() {
        return inputBindings;
    }

    public ModelRuleDescriptor getDescriptor() {
        return action.getDescriptor();
    }

    private void maybeFire() {
        if (isBound()) {
            binders.remove(this);
        }
    }

    public boolean isBound() {
        return subjectBinding.isBound()
            && inputsBound == inputReferences.size();
    }

    @Override
    public String toString() {
        return String.format("%s[%s - %s]", getClass().getSimpleName(), subjectBinding, action.getDescriptor());
    }
}
