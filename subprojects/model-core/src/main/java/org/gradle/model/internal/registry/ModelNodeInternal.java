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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Nullable;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.*;

abstract class ModelNodeInternal implements MutableModelNode {

    private CreatorRuleBinder creatorBinder;
    private Map<ModelActionRole, List<MutatorRuleBinder<?>>> mutators;
    private final Set<ModelPath> dependencies = Sets.newHashSet();

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

    public CreatorRuleBinder getCreatorBinder() {
        return creatorBinder;
    }

    public void setCreatorBinder(CreatorRuleBinder creatorBinder) {
        if (this.creatorBinder != null) {
            throw new IllegalStateException("creatorBinder already set for node " + getPath());
        }
        this.creatorBinder = creatorBinder;
    }

    public void addMutatorBinder(ModelActionRole role, MutatorRuleBinder<?> mutator) {
        if (mutators == null) {
            mutators = Maps.newEnumMap(ModelActionRole.class);
        }

        List<MutatorRuleBinder<?>> mutatorsForRole = mutators.get(role);
        if (mutatorsForRole == null) {
            mutatorsForRole = Lists.newLinkedList();
            mutators.put(role, mutatorsForRole);
        }

        mutatorsForRole.add(mutator);
    }

    public Iterable<MutatorRuleBinder<?>> getMutatorBinders(ModelActionRole role) {
        if (mutators == null) {
            return Collections.emptyList();
        }
        final List<MutatorRuleBinder<?>> ruleBinders = mutators.get(role);
        if (ruleBinders == null) {
            return Collections.emptyList();
        } else {
            return new Iterable<MutatorRuleBinder<?>>() {
                @Override
                public Iterator<MutatorRuleBinder<?>> iterator() {
                    return new Iterator<MutatorRuleBinder<?>>() {
                        int i;

                        @Override
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public boolean hasNext() {
                            return i < ruleBinders.size();
                        }

                        @Override
                        public MutatorRuleBinder<?> next() {
                            if (hasNext()) {
                                return ruleBinders.get(i++);
                            } else {
                                throw new NoSuchElementException();
                            }
                        }
                    };
                }
            };
        }
    }

    public void notifyFired(MutatorRuleBinder<?> binder) {
        assert binder.isBound();
        for (ModelBinding<?> inputBinding : binder.getInputBindings()) {
            dependencies.add(inputBinding.getNode().getPath());
        }
    }

    public Iterable<ModelPath> getDependencies() {
        return dependencies;
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

    @Override
    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type, @Nullable ModelRuleDescriptor ruleDescriptor) {
        ModelView<? extends T> modelView = getAdapter().asReadOnly(type, this, ruleDescriptor);
        if (modelView == null) {
            throw new IllegalStateException("Model node " + getPath() + " cannot be expressed as a read-only view of type " + type);
        }
        return modelView;
    }

    @Override
    public <T> ModelView<? extends T> asWritable(ModelType<T> type, ModelRuleDescriptor ruleDescriptor, List<ModelView<?>> inputs) {
        ModelView<? extends T> modelView = getAdapter().asWritable(type, this, ruleDescriptor, inputs);
        if (modelView == null) {
            throw new IllegalStateException("Model node " + getPath() + " cannot be expressed as a mutable view of type " + type);
        }
        return modelView;
    }
}
