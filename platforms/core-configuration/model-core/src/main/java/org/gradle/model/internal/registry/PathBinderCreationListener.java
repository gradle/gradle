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
import org.gradle.model.InvalidModelRuleException;
import org.gradle.model.ModelRuleBindingException;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.report.IncompatibleTypeReferenceReporter;

class PathBinderCreationListener extends ModelBinding {
    private final Action<ModelBinding> bindAction;

    public PathBinderCreationListener(ModelRuleDescriptor descriptor, BindingPredicate predicate, boolean writable, Action<ModelBinding> bindAction) {
        super(descriptor, predicate, writable);
        this.bindAction = bindAction;
    }

    @Override
    public boolean canBindInState(ModelNode.State state) {
        return predicate.getReference().isUntyped() || state.isAtLeast(ModelNode.State.Discovered);
    }

    @Override
    public void doOnBind(ModelNodeInternal node) {
        if (predicate.matches(node)) {
            boundTo = node;
            bindAction.execute(this);
        } else {
            throw new InvalidModelRuleException(referrer, new ModelRuleBindingException(
                IncompatibleTypeReferenceReporter.of(node, predicate.getReference().getType(), predicate.getReference().getDescription(), writable).asString()
            ));
        }
    }
}
