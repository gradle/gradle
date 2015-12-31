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

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Nullable;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.List;
import java.util.Set;

import static org.gradle.model.internal.core.ModelNode.State.Discovered;
import static org.gradle.model.internal.core.ModelNodes.withType;

abstract class ModelNodeInternal implements MutableModelNode {

    private final ModelPath path;
    private final ModelRuleDescriptor descriptor;
    private final Set<ModelNodeInternal> dependencies = Sets.newHashSet();
    private final Set<ModelNodeInternal> dependents = Sets.newHashSet();
    private ModelNode.State state = ModelNode.State.Registered;
    private boolean hidden;
    private final List<ModelRuleDescriptor> executedRules = Lists.newArrayList();
    private final List<RuleBinder> registrationActionBinders = Lists.newArrayList();
    private final List<ModelProjection> projections = Lists.newArrayList();
    private final ModelProjection projection;

    public ModelNodeInternal(ModelRegistration registration) {
        this.path = registration.getPath();
        this.descriptor = registration.getDescriptor();
        this.hidden = registration.isHidden();
        this.projection = new ChainingModelProjection(projections);
    }

    /**
     * Returns the binders of the rules created as part of the node's creation. These binders should not be considered
     * unbound in case the node is removed.
     */
    public List<RuleBinder> getRegistrationActionBinders() {
        return registrationActionBinders;
    }

    public void addRegistrationActionBinder(RuleBinder binder) {
        registrationActionBinders.add(binder);
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    @Override
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
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
        return path;
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

    @Nullable
    @Override
    public abstract ModelNodeInternal getLink(String name);

    @Override
    public boolean canBeViewedAs(ModelType<?> type) {
        return getPromise().canBeViewedAsImmutable(type) || getPromise().canBeViewedAsMutable(type);
    }

    @Override
    public Iterable<String> getReadableTypeDescriptions() {
        return getPromise().getReadableTypeDescriptions(this);
    }

    @Override
    public Iterable<String> getWritableTypeDescriptions() {
        return getPromise().getWritableTypeDescriptions(this);
    }

    public ModelPromise getPromise() {
        if (!state.isAtLeast(State.Discovered)) {
            throw new IllegalStateException(String.format("Cannot get promise for %s in state %s when not yet discovered", getPath(), state));
        }
        return projection;
    }

    public ModelAdapter getAdapter() {
        if (!state.isAtLeast(State.Created)) {
            throw new IllegalStateException(String.format("Cannot get adapter for %s in state %s when node is not created", getPath(), state));
        }
        return projection;
    }

    public ModelProjection getProjection() {
        return projection;
    }

    @Override
    public void addProjection(ModelProjection projection) {
        if (isAtLeast(Discovered)) {
            throw new IllegalStateException(String.format("Cannot add projections to node '%s' as it is already %s", getPath(), getState()));
        }
        projections.add(projection);
    }

    @Override
    public String toString() {
        return getPath().toString();
    }

    public abstract Iterable<? extends ModelNodeInternal> getLinks();

    @Override
    public boolean isAtLeast(State state) {
        return this.getState().compareTo(state) >= 0;
    }

    @Override
    public Optional<String> getValueDescription() {
        this.ensureUsable();
        return getAdapter().getValueDescription(this);
    }

    @Override
    public Optional<String> getTypeDescription() {
        this.ensureUsable();
        ModelView<?> modelView = getAdapter().asImmutable(ModelType.untyped(), this, null);
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

    @Override
    public boolean hasLink(String name, ModelType<?> type) {
        return hasLink(name, withType(type));
    }

    @Override
    public Iterable<? extends MutableModelNode> getLinks(ModelType<?> type) {
        return getLinks(withType(type));
    }

    @Override
    public Set<String> getLinkNames(ModelType<?> type) {
        return getLinkNames(withType(type));
    }

    @Override
    public void defineRulesForLink(ModelActionRole role, ModelAction action) {
        applyToLink(role, action);
    }

    @Override
    public void defineRulesFor(NodePredicate predicate, ModelActionRole role, ModelAction action) {
        applyTo(predicate, role, action);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
