/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.model.internal.core.ModelAction;

import java.util.Collection;
import java.util.List;

class MutatorRuleBinder<T> extends RuleBinder {
    private ModelBinding subjectBinding;
    private final ModelAction<T> action;

    public MutatorRuleBinder(final BindingPredicate subjectReference, List<BindingPredicate> inputs, ModelAction<T> action, Collection<RuleBinder> binders) {
        super(subjectReference, inputs, action.getDescriptor(), binders);
        subjectBinding = binding(subjectReference, true, new Action<ModelBinding>() {
            @Override
            public void execute(ModelBinding modelBinding) {
                ModelNodeInternal node = modelBinding.getNode();
                BindingPredicate predicate = modelBinding.getPredicate();
                if (predicate.getState() != null && node.getState().compareTo(predicate.getState()) >= 0) {
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
        this.action = action;
    }

    public ModelAction<T> getAction() {
        return action;
    }

    public ModelBinding getSubjectBinding() {
        return subjectBinding;
    }

    @Override
    public boolean isBound() {
        return subjectBinding != null && subjectBinding.isBound() && super.isBound();
    }
}
