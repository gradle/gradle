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

import org.gradle.model.internal.core.ModelAction;
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;

import java.util.Collection;

public class MutatorRuleBinder<T> extends RuleBinder {

    private ModelBinding<T> subjectBinding;
    private final ModelReference<T> subjectReference;
    private final ModelActionRole role;
    private final ModelAction<T> action;

    public MutatorRuleBinder(ModelReference<T> subjectReference, ModelActionRole role, ModelAction<T> action, ModelPath scope, Collection<RuleBinder> binders) {
        super(action.getInputs(), action.getDescriptor(), scope, binders);
        this.subjectReference = subjectReference;
        this.role = role;
        this.action = action;
    }

    public ModelActionRole getRole() {
        return role;
    }

    public ModelAction<T> getAction() {
        return action;
    }

    public ModelReference<T> getSubjectReference() {
        return subjectReference;
    }

    public ModelBinding<T> getSubjectBinding() {
        return subjectBinding;
    }

    public void bindSubject(ModelNodeInternal modelNode) {
        assert this.subjectBinding == null;
        this.subjectBinding = RuleBinder.bind(subjectReference, modelNode);
        maybeFire();
    }

    @Override
    public boolean isBound() {
        return subjectBinding != null && super.isBound();
    }

}
