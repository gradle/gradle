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

import com.google.common.collect.Multimap;
import org.gradle.api.Action;
import org.gradle.model.internal.core.ModelAction;
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.ModelPath;

class BindModelAction<T> implements Action<RuleBinder<T>> {
    private final ModelAction<T> mutator;
    private final ModelActionRole type;
    private final ModelGraph modelGraph;
    private final Multimap<MutationKey, BoundModelMutator<?>> actions;

    public BindModelAction(ModelAction<T> mutator, ModelActionRole type, ModelGraph modelGraph, Multimap<MutationKey, BoundModelMutator<?>> actions) {
        this.mutator = mutator;
        this.type = type;
        this.modelGraph = modelGraph;
        this.actions = actions;
    }

    public void execute(RuleBinder<T> ruleBinder) {
        BoundModelMutator<T> boundMutator = new BoundModelMutator<T>(mutator, ruleBinder.getSubjectBinding(), ruleBinder.getInputBindings());
        ModelPath path = boundMutator.getSubject().getPath();
        ModelNodeInternal subject = modelGraph.get(path);
        if (!subject.canApply(type)) {
            throw new IllegalStateException(String.format(
                    "Cannot add %s rule '%s' for model element '%s' when element is in state %s.",
                    type,
                    boundMutator.getMutator().getDescriptor(),
                    path,
                    subject.getState()
            ));
        }
        actions.put(new MutationKey(path, type), boundMutator);
    }
}
