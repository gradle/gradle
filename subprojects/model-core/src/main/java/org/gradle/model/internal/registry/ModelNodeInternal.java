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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

abstract class ModelNodeInternal implements MutableModelNode {

    private BoundModelCreator creator;
    private Map<ModelActionRole, List<BoundModelMutator<?>>> mutators;

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

    public void addMutator(ModelActionRole role, BoundModelMutator<?> mutator) {
        if (mutators == null) {
            mutators = Maps.newEnumMap(ModelActionRole.class);
        }

        List<BoundModelMutator<?>> mutatorsForRole = mutators.get(role);
        if (mutatorsForRole == null) {
            mutatorsForRole = Lists.newLinkedList();
            mutators.put(role, mutatorsForRole);
        }

        mutatorsForRole.add(mutator);
    }

    public List<BoundModelMutator<?>> removeMutators(ModelActionRole role) {
        if (mutators == null) {
            return Collections.emptyList();
        }

        List<BoundModelMutator<?>> mutatorsForRole = mutators.remove(role);
        if (mutatorsForRole == null) {
            return Collections.emptyList();
        }

        return mutatorsForRole;
    }

    public Iterable<ModelPath> getMutationDependencies() {
        if (mutators == null) {
            return Collections.emptyList();
        } else {
            Iterable<BoundModelMutator<?>> allMutators = Iterables.concat(mutators.values());
            Iterable<Iterable<ModelPath>> nestedPaths = Iterables.transform(allMutators, new Function<BoundModelMutator<?>, Iterable<ModelPath>>() {
                @Override
                public Iterable<ModelPath> apply(BoundModelMutator<?> input) {
                    return Iterables.transform(input.getInputs(), ModelBinding.GetPath.INSTANCE);
                }
            });
            return Iterables.concat(nestedPaths);
        }
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
