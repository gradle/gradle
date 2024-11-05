/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.attributes;

import com.google.common.collect.ImmutableList;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.Cast;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.snapshot.impl.CoercingStringValueSnapshot;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@ServiceScope(Scope.BuildSession.class)
public class DefaultAttributesFactory implements AttributesFactory {
    private final ImmutableAttributes root;
    private final Map<ImmutableAttributes, ImmutableList<DefaultImmutableAttributesContainer>> children;
    private final IsolatableFactory isolatableFactory;
    private final UsageCompatibilityHandler usageCompatibilityHandler;
    private final NamedObjectInstantiator instantiator;

    public DefaultAttributesFactory(IsolatableFactory isolatableFactory, NamedObjectInstantiator instantiator) {
        this.isolatableFactory = isolatableFactory;
        this.instantiator = instantiator;
        this.root = ImmutableAttributes.EMPTY;
        this.children = new ConcurrentHashMap<>();
        this.usageCompatibilityHandler = new UsageCompatibilityHandler(isolatableFactory, instantiator);
    }

    public int size() {
        return children.size();
    }

    @Override
    public DefaultMutableAttributeContainer mutable() {
        return new DefaultMutableAttributeContainer(this);
    }

    @Override
    public HierarchicalMutableAttributeContainer mutable(AttributeContainerInternal fallback) {
        return join(fallback, new DefaultMutableAttributeContainer(this));
    }

    @Override
    public HierarchicalMutableAttributeContainer join(AttributeContainerInternal fallback, AttributeContainerInternal primary) {
        return new HierarchicalMutableAttributeContainer(this, fallback, primary);
    }

    @Override
    public <T> ImmutableAttributes of(Attribute<T> key, T value) {
        return concat(root, key, value);
    }

    @Override
    public <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, @Nullable T value) {
        return concat(node, key, isolate(value));
    }

    public <T> Isolatable<T> isolate(@Nullable T value) {
        if (value instanceof String) {
            return Cast.uncheckedNonnullCast(new CoercingStringValueSnapshot((String) value, instantiator));
        } else {
            return isolatableFactory.isolate(value);
        }
    }

    @Override
    public <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, Isolatable<T> value) {
        if (key.equals(Usage.USAGE_ATTRIBUTE) || key.getName().equals(Usage.USAGE_ATTRIBUTE.getName())) {
            return usageCompatibilityHandler.doConcat(this, node, key, value);
        } else {
            return doConcatIsolatable(node, key, value);
        }
    }

    ImmutableAttributes doConcatIsolatable(ImmutableAttributes node, Attribute<?> key, Isolatable<?> value) {

        // Try to retrieve a cached value without locking
        ImmutableList<DefaultImmutableAttributesContainer> cachedChildren = children.get(node);
        if (cachedChildren != null) {
            DefaultImmutableAttributesContainer child = findChild(cachedChildren, key, value);
            if (child != null) {
                return child;
            }
        }

        // If we didn't find a cached value, we need to lock and update the cache
        cachedChildren = children.compute(node, (k, nodeChildren) -> {
            if (nodeChildren != null) {
                // Check if the value is already present again, now that we have the lock.
                DefaultImmutableAttributesContainer child = findChild(nodeChildren, key, value);
                if (child != null) {
                    // Somebody updated the cache before we could. Return the cache unchanged.
                    return nodeChildren;
                }
            } else {
                nodeChildren = ImmutableList.of();
            }

            // Nobody has tried to concat this value yet.
            // Calculate it and add it to the children.
            DefaultImmutableAttributesContainer child = new DefaultImmutableAttributesContainer((DefaultImmutableAttributesContainer) node, key, value);
            return concatChild(nodeChildren, child);
        });

        return Objects.requireNonNull(findChild(cachedChildren, key, value));
    }

    private static @Nullable DefaultImmutableAttributesContainer findChild(
        ImmutableList<DefaultImmutableAttributesContainer> nodeChildren,
        Attribute<?> key,
        Isolatable<?> value
    ) {
        for (DefaultImmutableAttributesContainer child : nodeChildren) {
            if (child.attribute.equals(key) && child.value.equals(value)) {
                return child;
            }
        }
        return null;
    }

    private static ImmutableList<DefaultImmutableAttributesContainer> concatChild(
        ImmutableList<DefaultImmutableAttributesContainer> nodeChildren,
        DefaultImmutableAttributesContainer child
    ) {
        return ImmutableList.<DefaultImmutableAttributesContainer>builderWithExpectedSize(nodeChildren.size() + 1)
            .addAll(nodeChildren)
            .add(child)
            .build();
    }

    @Override
    public ImmutableAttributes concat(ImmutableAttributes fallback, ImmutableAttributes primary) {
        if (fallback == ImmutableAttributes.EMPTY) {
            return primary;
        }
        if (primary == ImmutableAttributes.EMPTY) {
            return fallback;
        }
        ImmutableAttributes current = primary;
        for (Attribute<?> attribute : fallback.keySet()) {
            if (!current.findEntry(attribute.getName()).isPresent()) {
                if (fallback instanceof DefaultImmutableAttributesContainer) {
                    current = doConcatIsolatable(current, attribute, ((DefaultImmutableAttributesContainer) fallback).getIsolatableAttribute(attribute));
                } else {
                    current = concat(current, Cast.uncheckedNonnullCast(attribute), fallback.getAttribute(attribute));
                }
            }
        }
        return current;
    }

    @Override
    public ImmutableAttributes safeConcat(ImmutableAttributes attributes1, ImmutableAttributes attributes2) throws AttributeMergingException {
        if (attributes1 == ImmutableAttributes.EMPTY) {
            return attributes2;
        }
        if (attributes2 == ImmutableAttributes.EMPTY) {
            return attributes1;
        }
        ImmutableAttributes current = attributes2;
        for (Attribute<?> attribute : attributes1.keySet()) {
            AttributeValue<?> entry = current.findEntry(attribute.getName());
            if (entry.isPresent()) {
                Object currentAttribute = entry.get();
                Object existingAttribute = attributes1.getAttribute(attribute);
                if (!currentAttribute.equals(existingAttribute)) {
                    throw new AttributeMergingException(attribute, existingAttribute, currentAttribute);
                }
            }
            if (attributes1 instanceof DefaultImmutableAttributesContainer) {
                current = doConcatIsolatable(current, attribute, ((DefaultImmutableAttributesContainer) attributes1).getIsolatableAttribute(attribute));
            } else {
                current = concat(current, Cast.uncheckedNonnullCast(attribute), attributes1.getAttribute(attribute));
            }
        }
        return current;
    }

    @Override
    public ImmutableAttributes fromMap(Map<Attribute<?>, ?> attributes) {
        ImmutableAttributes result = ImmutableAttributes.EMPTY;
        for (Map.Entry<Attribute<?>, ?> entry : attributes.entrySet()) {
            /*
                The order of the concatenation arguments here is important, as we have tests like
                ConfigurationCacheDependencyResolutionIntegrationTest and ConfigurationCacheDependencyResolutionIntegrationTest
                that rely on a particular order of failures when there are multiple invalid attribute type
                conversions.  So even if it looks unnatural to list result second, this should remain.
             */
            result = concat(of(entry.getKey(), Cast.uncheckedNonnullCast(entry.getValue())), result);
        }
        return result;
    }
}
