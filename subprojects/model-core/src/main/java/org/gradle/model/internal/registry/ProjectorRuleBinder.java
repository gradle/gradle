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

package org.gradle.model.internal.registry;

import org.gradle.api.Action;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelProjector;

import java.util.Collection;
import java.util.List;

class ProjectorRuleBinder extends RuleBinder {
    private final ModelProjector projector;
    private final ModelBinding subjectBinding;

    public ProjectorRuleBinder(ModelProjector projector, BindingPredicate subject, List<BindingPredicate> inputs, Collection<RuleBinder> binders) {
        super(subject, inputs, projector.getDescriptor(), binders);
        this.projector = projector;
        this.subjectBinding = binding(subject, true, new Action<ModelBinding>() {
            @Override
            public void execute(ModelBinding modelBinding) {
                ModelNodeInternal node = modelBinding.getNode();
                if (node.isAtLeast(ModelNode.State.ProjectionsDefined)) {
                    throw new IllegalStateException(String.format("Cannot add projector '%s' for model element '%s' as this element is already at state %s.",
                        modelBinding.referrer,
                        node.getPath(),
                        node.getState()
                    ));
                }
                maybeFire();
            }
        });
    }

    public ModelProjector getProjector() {
        return projector;
    }

    @Override
    public ModelBinding getSubjectBinding() {
        return subjectBinding;
    }

    @Override
    public boolean isBound() {
        return subjectBinding != null && subjectBinding.isBound() && super.isBound();
    }
}
