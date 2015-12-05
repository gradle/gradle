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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Map;
import java.util.Set;

import static org.gradle.model.internal.core.ModelNode.State.Created;
import static org.gradle.model.internal.core.ModelNode.State.Discovered;
import static org.gradle.model.internal.core.ModelNode.State.Initialized;

class ModelElementNode extends ModelNodeInternal {
    private ModelRegistryInternal modelRegistry;
    private final Map<String, ModelNodeInternal> links = Maps.newTreeMap();
    private final MutableModelNode parent;
    private Object privateData;
    private ModelType<?> privateDataType;

    public ModelElementNode(ModelRegistryInternal modelRegistry, ModelRegistration registration, MutableModelNode parent) {
        super(registration);
        this.modelRegistry = modelRegistry;
        this.parent = parent;
    }

    @Override
    public MutableModelNode getParent() {
        return parent;
    }

    @Override
    public boolean canBeViewedAs(ModelType<?> type) {
        return getPromise().canBeViewedAsImmutable(type) || getPromise().canBeViewedAsMutable(type);
    }

    @Override
    public <T> ModelView<? extends T> asImmutable(ModelType<T> type, @Nullable ModelRuleDescriptor ruleDescriptor) {
        ModelView<? extends T> modelView = getAdapter().asImmutable(type, this, ruleDescriptor);
        if (modelView == null) {
            throw new IllegalStateException("Model node " + getPath() + " cannot be expressed as a read-only view of type " + type);
        }
        return modelView;
    }

    @Override
    public <T> ModelView<? extends T> asMutable(ModelType<T> type, ModelRuleDescriptor ruleDescriptor) {
        ModelView<? extends T> modelView = getAdapter().asMutable(type, this, ruleDescriptor);
        if (modelView == null) {
            throw new IllegalStateException("Model node " + getPath() + " cannot be expressed as a mutable view of type " + type);
        }
        return modelView;
    }

    @Override
    public <T> T getPrivateData(Class<T> type) {
        return getPrivateData(ModelType.of(type));
    }

    public <T> T getPrivateData(ModelType<T> type) {
        if (privateData == null) {
            return null;
        }

        if (!type.isAssignableFrom(privateDataType)) {
            throw new ClassCastException("Cannot get private data '" + privateData + "' of type '" + privateDataType + "' as type '" + type);
        }
        return Cast.uncheckedCast(privateData);
    }

    @Override
    public Object getPrivateData() {
        return privateData;
    }

    @Override
    public <T> void setPrivateData(Class<? super T> type, T object) {
        setPrivateData(ModelType.of(type), object);
    }

    public <T> void setPrivateData(ModelType<? super T> type, T object) {
        if (!isMutable()) {
            throw new IllegalStateException(String.format("Cannot set value for model element '%s' as this element is not mutable.", getPath()));
        }
        this.privateDataType = type;
        this.privateData = object;
    }

    public boolean hasLink(String name) {
        return links.containsKey(name);
    }

    @Nullable
    public ModelNodeInternal getLink(String name) {
        return links.get(name);
    }

    public Iterable<? extends ModelNodeInternal> getLinks() {
        return links.values();
    }

    @Override
    public int getLinkCount(ModelType<?> type) {
        int count = 0;
        for (ModelNodeInternal linked : links.values()) {
            linked.ensureAtLeast(Discovered);
            if (linked.getPromise().canBeViewedAsMutable(type)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Set<String> getLinkNames(ModelType<?> type) {
        Set<String> names = Sets.newLinkedHashSet();
        for (Map.Entry<String, ModelNodeInternal> entry : links.entrySet()) {
            ModelNodeInternal link = entry.getValue();
            link.ensureAtLeast(Discovered);
            if (link.getPromise().canBeViewedAsMutable(type)) {
                names.add(entry.getKey());
            }
        }
        return names;
    }

    @Override
    public Iterable<? extends MutableModelNode> getLinks(final ModelType<?> type) {
        return Iterables.filter(links.values(), new Predicate<ModelNodeInternal>() {
            @Override
            public boolean apply(ModelNodeInternal link) {
                link.ensureAtLeast(Discovered);
                return link.getPromise().canBeViewedAsMutable(type);
            }
        });
    }

    @Override
    public int getLinkCount() {
        return links.size();
    }

    @Override
    public boolean hasLink(String name, ModelType<?> type) {
        ModelNodeInternal linked = getLink(name);
        if (linked == null) {
            return false;
        }
        linked.ensureAtLeast(Discovered);
        return linked.getPromise().canBeViewedAsMutable(type);
    }

    @Override
    public void applyToSelf(ModelActionRole role, ModelAction action) {
        DefaultModelRegistry.checkNodePath(this, action);
        modelRegistry.bind(action.getSubject(), role, action, ModelPath.ROOT);
    }

    @Override
    public void applyToLink(ModelActionRole type, ModelAction action) {
        if (!getPath().isDirectChild(action.getSubject().getPath())) {
            throw new IllegalArgumentException(String.format("Linked element action reference has a path (%s) which is not a child of this node (%s).", action.getSubject().getPath(), getPath()));
        }
        modelRegistry.bind(action.getSubject(), type, action, ModelPath.ROOT);
    }

    @Override
    public void applyToLink(String name, Class<? extends RuleSource> rules) {
        apply(rules, getPath().child(name));
    }

    @Override
    public void applyToSelf(Class<? extends RuleSource> rules) {
        apply(rules, getPath());
    }

    @Override
    public void applyToLinks(final ModelType<?> type, final Class<? extends RuleSource> rules) {
        modelRegistry.registerListener(new ModelListener() {
            @Nullable
            @Override
            public ModelPath getParent() {
                return getPath();
            }

            @Nullable
            @Override
            public ModelType<?> getType() {
                return type;
            }

            @Override
            public boolean onDiscovered(ModelNodeInternal node) {
                node.applyToSelf(rules);
                return false;
            }
        });
    }

    @Override
    public void applyToAllLinksTransitive(final ModelType<?> type, final Class<? extends RuleSource> rules) {
        modelRegistry.registerListener(new ModelListener() {
            @Override
            public ModelPath getAncestor() {
                return ModelElementNode.this.getPath();
            }

            @Nullable
            @Override
            public ModelType<?> getType() {
                return type;
            }

            @Override
            public boolean onDiscovered(ModelNodeInternal node) {
                node.applyToSelf(rules);
                return false;
            }
        });
    }

    private void apply(Class<? extends RuleSource> rules, ModelPath scope) {
        Iterable<ExtractedModelRule> extractedRules = modelRegistry.extract(rules);
        for (ExtractedModelRule extractedRule : extractedRules) {
            if (!extractedRule.getRuleDependencies().isEmpty()) {
                throw new IllegalStateException("Rule source " + rules + " cannot have plugin dependencies (introduced by rule " + extractedRule + ")");
            }
            extractedRule.apply(modelRegistry, scope);
        }
    }

    @Override
    public void applyToAllLinks(final ModelActionRole type, final ModelAction action) {
        if (action.getSubject().getPath() != null) {
            throw new IllegalArgumentException("Linked element action reference must have null path.");
        }

        modelRegistry.registerListener(new ModelListener() {
            @Override
            public ModelPath getParent() {
                return ModelElementNode.this.getPath();
            }

            @Override
            public ModelType<?> getType() {
                return action.getSubject().getType();
            }

            @Override
            public boolean onDiscovered(ModelNodeInternal node) {
                modelRegistry.bind(ModelReference.of(node.getPath(), action.getSubject().getType()), type, action, ModelPath.ROOT);
                return false;
            }
        });
    }

    @Override
    public void applyToAllLinksTransitive(final ModelActionRole type, final ModelAction action) {
        if (action.getSubject().getPath() != null) {
            throw new IllegalArgumentException("Linked element action reference must have null path.");
        }

        modelRegistry.registerListener(new ModelListener() {
            @Override
            public ModelPath getAncestor() {
                return ModelElementNode.this.getPath();
            }

            @Override
            public ModelType<?> getType() {
                return action.getSubject().getType();
            }

            @Override
            public boolean onDiscovered(ModelNodeInternal node) {
                modelRegistry.bind(ModelReference.of(node.getPath(), action.getSubject().getType()), type, action, ModelPath.ROOT);
                return false;
            }
        });
    }

    @Override
    public void addReference(ModelRegistration registration) {
        addNode(new ModelReferenceNode(registration, this), registration);
    }

    @Override
    public void addLink(ModelRegistration registration) {
        addNode(new ModelElementNode(modelRegistry, registration, this), registration);
    }

    private void addNode(ModelNodeInternal child, ModelRegistration registration) {
        ModelPath childPath = child.getPath();
        if (!getPath().isDirectChild(childPath)) {
            throw new IllegalArgumentException(String.format("Element registration has a path (%s) which is not a child of this node (%s).", childPath, getPath()));
        }

        ModelNodeInternal currentChild = links.get(childPath.getName());
        if (currentChild != null) {
            if (!currentChild.isAtLeast(Created)) {
                throw new DuplicateModelException(
                    String.format(
                        "Cannot create '%s' using creation rule '%s' as the rule '%s' is already registered to create this model element.",
                        childPath,
                        describe(registration.getDescriptor()),
                        describe(currentChild.getDescriptor())
                    )
                );
            }
            throw new DuplicateModelException(
                String.format(
                    "Cannot create '%s' using creation rule '%s' as the rule '%s' has already been used to create this model element.",
                    childPath,
                    describe(registration.getDescriptor()),
                    describe(currentChild.getDescriptor())
                )
            );
        }
        if (!isMutable()) {
            throw new IllegalStateException(
                String.format(
                    "Cannot create '%s' using creation rule '%s' as model element '%s' is no longer mutable.",
                    childPath,
                    describe(registration.getDescriptor()),
                    getPath()
                )
            );
        }
        links.put(child.getPath().getName(), child);
        modelRegistry.registerNode(child, registration.getActions());
    }

    @Override
    public void removeLink(String name) {
        if (links.remove(name) != null) {
            modelRegistry.remove(getPath().child(name));
        }
    }

    @Override
    public void setTarget(ModelNode target) {
        throw new UnsupportedOperationException(String.format("This node (%s) is not a reference to another node.", getPath()));
    }

    @Override
    public void ensureUsable() {
        ensureAtLeast(Initialized);
    }

    @Override
    public void ensureAtLeast(State state) {
        modelRegistry.transition(this, state, true);
    }

    private static String describe(ModelRuleDescriptor descriptor) {
        StringBuilder stringBuilder = new StringBuilder();
        descriptor.describeTo(stringBuilder);
        return stringBuilder.toString();
    }
}
