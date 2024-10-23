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
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.ChainingModelProjection;
import org.gradle.model.internal.core.EmptyModelProjection;
import org.gradle.model.internal.core.ModelAction;
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.ModelAdapter;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelProjection;
import org.gradle.model.internal.core.ModelPromise;
import org.gradle.model.internal.core.ModelRegistration;
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.NodePredicate;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.ExtractedRuleSource;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.gradle.model.internal.core.ModelNode.State.Discovered;
import static org.gradle.model.internal.core.ModelNodes.withType;

abstract class ModelNodeInternal implements MutableModelNode {
    protected final ModelRegistryInternal modelRegistry;
    private final ModelPath path;
    private final ModelRuleDescriptor descriptor;

    private Set<ModelNodeInternal> dependencies;
    private Set<ModelNodeInternal> dependents;
    private ModelNode.State state = ModelNode.State.Registered;
    private boolean hidden;
    private List<ModelRuleDescriptor> executedRules;
    private List<RuleBinder> registrationActionBinders;
    private List<ModelProjection> projections;

    public ModelNodeInternal(ModelRegistryInternal modelRegistry, ModelRegistration registration) {
        this.modelRegistry = modelRegistry;
        this.path = registration.getPath();
        this.descriptor = registration.getDescriptor();
        this.hidden = registration.isHidden();
    }

    /**
     * Returns the binders of the rules created as part of the node's creation. These binders should not be considered
     * unbound in case the node is removed.
     */
    public List<RuleBinder> getRegistrationActionBinders() {
        return registrationActionBinders == null ? Collections.<RuleBinder>emptyList() : registrationActionBinders;
    }

    public void addRegistrationActionBinder(RuleBinder binder) {
        if (registrationActionBinders == null) {
            registrationActionBinders = new ArrayList<>();
        }
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
            if (dependencies == null) {
                dependencies = new HashSet<>();
            }
            dependencies.add(node);
            if (node.dependents == null) {
                node.dependents = new HashSet<>();
            }
            node.dependents.add(this);
        }
        if (executedRules == null) {
            executedRules = new ArrayList<>();
        }
        executedRules.add(binder.getDescriptor());
    }

    public Iterable<? extends ModelNode> getDependencies() {
        return dependencies == null ? Collections.<ModelNode>emptyList() : dependencies;
    }

    public Iterable<? extends ModelNode> getDependents() {
        return dependents == null ? Collections.<ModelNode>emptyList() : dependents;
    }

    @Override
    public ModelPath getPath() {
        return path;
    }

    @Override
    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public ModelNode.State getState() {
        return state;
    }

    public void setState(ModelNode.State state) {
        this.state = state;
    }

    @Override
    public boolean isMutable() {
        return state.mutable;
    }

    @Nullable
    @Override
    public abstract ModelNodeInternal getLink(String name);

    @Override
    public boolean canBeViewedAs(ModelType<?> type) {
        return getPromise().canBeViewedAs(type);
    }

    @Override
    public Iterable<String> getTypeDescriptions() {
        return getPromise().getTypeDescriptions(this);
    }

    private ModelProjection toProjection() {
        if (projections == null) {
            return EmptyModelProjection.INSTANCE;
        }
        return new ChainingModelProjection(projections);
    }

    public ModelPromise getPromise() {
        if (!state.isAtLeast(State.Discovered)) {
            throw new IllegalStateException(String.format("Cannot get promise for '%s' in state %s.", getPath(), state));
        }
        return toProjection();
    }

    public ModelAdapter getAdapter() {
        if (!state.isAtLeast(State.Created)) {
            throw new IllegalStateException(String.format("Cannot get adapter for '%s' in state %s.", getPath(), state));
        }
        return toProjection();
    }

    public ModelProjection getProjection() {
        return toProjection();
    }

    @Override
    public void addProjection(ModelProjection projection) {
        if (isAtLeast(Discovered)) {
            throw new IllegalStateException(String.format("Cannot add projections to '%s' as it is already in state %s.", getPath(), state));
        }
        if (projections == null) {
            projections = new ArrayList<>();
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
            try {
                ModelType<?> type = modelView.getType();
                if (type != null) {
                    return Optional.of(type.toString());
                }
            } finally {
                modelView.close();
            }
        }
        return Optional.absent();
    }

    @Override
    public List<ModelRuleDescriptor> getExecutedRules() {
        return this.executedRules == null ? Collections.<ModelRuleDescriptor>emptyList() : this.executedRules;
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
    public void applyToSelf(ModelActionRole role, ModelAction action) {
        DefaultModelRegistry.checkNodePath(this, action);
        modelRegistry.bind(action.getSubject(), role, action);
    }

    @Override
    public void applyToSelf(ExtractedRuleSource<?> rules) {
        rules.apply(modelRegistry, this);
    }

    @Override
    public void applyToSelf(Class<? extends RuleSource> rulesClass) {
        ExtractedRuleSource<?> rules = modelRegistry.newRuleSource(rulesClass);
        rules.assertNoPlugins();
        rules.apply(modelRegistry, this);
    }
}
