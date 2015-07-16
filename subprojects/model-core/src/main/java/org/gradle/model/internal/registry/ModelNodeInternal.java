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

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import org.gradle.api.Nullable;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;

abstract class ModelNodeInternal implements MutableModelNode {

    private static final Supplier<List<MutatorRuleBinder<?>>> LIST_SUPPLIER = new Supplier<List<MutatorRuleBinder<?>>>() {
        @Override
        public List<MutatorRuleBinder<?>> get() {
            return Lists.newArrayList();
        }
    };

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelNodeInternal.class);

    private CreatorRuleBinder creatorBinder;
    private final Set<ModelNodeInternal> dependencies = Sets.newHashSet();
    private final Set<ModelNodeInternal> dependents = Sets.newHashSet();
    private ModelNode.State state = ModelNode.State.Known;
    private boolean hidden;
    private final List<ModelRuleDescriptor> executedRules = Lists.newArrayList();

    public ModelNodeInternal(CreatorRuleBinder creatorBinder) {
        this.creatorBinder = creatorBinder;
    }

    public CreatorRuleBinder getCreatorBinder() {
        return creatorBinder;
    }

    public void replaceCreatorRuleBinder(CreatorRuleBinder newCreatorBinder) {
        if (getState() != State.Known) {
            throw new IllegalStateException("Cannot replace creator rule binder when not in known state (node: " + this + ", state: " + getState() + ")");
        }

        ModelCreator newCreator = newCreatorBinder.getCreator();
        ModelCreator oldCreator = creatorBinder.getCreator();

        // Can't change type
        if (!oldCreator.getPromise().equals(newCreator.getPromise())) {
            throw new IllegalStateException("can not replace node " + getPath() + " with different promise (old: " + oldCreator.getPromise() + ", new: " + newCreator.getPromise() + ")");
        }

        // Can't have different inputs
        if (!newCreator.getInputs().equals(oldCreator.getInputs())) {
            Joiner joiner = Joiner.on(", ");
            throw new IllegalStateException("can not replace node " + getPath() + " with creator with different input bindings (old: [" + joiner.join(oldCreator.getInputs()) + "], new: [" + joiner.join(newCreator.getInputs()) + "])");
        }

        this.creatorBinder = newCreatorBinder;
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    @Override
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    @Override
    public boolean isEphemeral() {
        return creatorBinder.getCreator().isEphemeral();
    }

    private static ListMultimap<ModelNode.State, MutatorRuleBinder<?>> createMutatorsMap() {
        return Multimaps.newListMultimap(new EnumMap<ModelNode.State, Collection<MutatorRuleBinder<?>>>(ModelNode.State.class), LIST_SUPPLIER);
    }

    public void notifyFired(RuleBinder binder) {
        assert binder.isBound() : "RuleBinder must be in a bound state";
        for (ModelBinding inputBinding : binder.getInputBindings()) {
            ModelNodeInternal node = inputBinding.getNode();
            dependencies.add(node);
            node.dependents.add(this);
        }
        executedRules.add(binder.getDescriptor());
    }

    public Iterable<? extends ModelNode> getDependencies() {
        return dependencies;
    }

    public Iterable<? extends ModelNode> getDependents() {
        return dependents;
    }

    public ModelPath getPath() {
        return creatorBinder.getCreator().getPath();
    }

    public ModelRuleDescriptor getDescriptor() {
        return creatorBinder.getDescriptor();
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

    public boolean canApply(ModelNode.State targetState) {
        return state.compareTo(targetState) < 0;
    }

    public ModelPromise getPromise() {
        return creatorBinder.getCreator().getPromise();
    }

    public ModelAdapter getAdapter() {
        return creatorBinder.getCreator().getAdapter();
    }

    @Override
    public String toString() {
        return getPath().toString();
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

    public void reset() {
        if (getState() != State.Known) {
            setState(State.Known);
            setPrivateData(ModelType.untyped(), null);

            for (ModelNodeInternal dependent : dependents) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("resetting dependent node of {}: {}", this, dependent);
                }
                dependent.reset();
            }

            for (ModelNodeInternal child : getLinks()) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("resetting child node of {}: {}", this, child);
                }

                child.reset();
            }
        }
    }

    @Override
    public Optional<String> getValueDescription() {
        this.ensureUsable();
        return this.getAdapter().getValueDescription(this);
    }

    @Override
    public Optional<String> getTypeDescription() {
        this.ensureUsable();
        ModelView<?> modelView = getAdapter().asReadOnly(ModelType.untyped(), this, null);
        if (modelView != null) {
            ModelType<?> type = modelView.getType();
            if (type != null) {
                return Optional.of(type.toString());
            }
            modelView.close();
        }
        return Optional.absent();
    }

    @Override
    public List<ModelRuleDescriptor> getExecutedRules() {
        return this.executedRules;
    }
}
