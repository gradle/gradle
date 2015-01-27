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

import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

abstract class ModelNodeInternal implements MutableModelNode {

    private BoundModelCreator creator;

    private final ModelPath creationPath;
    private final ModelRuleDescriptor descriptor;
    private final ModelPromise promise;
    private final ModelAdapter adapter;
    private ModelNode.State state = ModelNode.State.Known;

    public ModelNodeInternal(ModelPath creationPath, ModelRuleDescriptor descriptor, ModelPromise promise, ModelAdapter adapter) {
        this.creationPath = creationPath;
        this.descriptor = descriptor;
        this.promise = promise;
        this.adapter = adapter;
    }

    public BoundModelCreator getCreator() {
        return creator;
    }

    public void setCreator(BoundModelCreator creator) {
        this.creator = creator;
    }

    public ModelPath getPath() {
        return creationPath;
    }

    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }

    public ModelNode.State getState() {
        return state;
    }

    public void setState(ModelNode.State state) {
        this.state = state;
    }

    public boolean isMutable() {
        return state.mutable;
    }

    public boolean canApply(ModelActionRole type) {
        return type.ordinal() >= state.ordinal() - ModelNode.State.Created.ordinal();
    }

    public ModelPromise getPromise() {
        return promise;
    }

    public ModelAdapter getAdapter() {
        return adapter;
    }

    @Override
    public String toString() {
        return creationPath.toString();
    }

    public abstract ModelNodeInternal getTarget();

    public abstract Iterable<? extends ModelNodeInternal> getLinks();

    public abstract ModelNodeInternal addLink(ModelNodeInternal node);
}
