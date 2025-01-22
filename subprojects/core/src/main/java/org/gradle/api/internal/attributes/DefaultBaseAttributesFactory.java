/*
 * Copyright 2025 the original author or authors.
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

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link BaseAttributesFactory}.
 */
public class DefaultBaseAttributesFactory implements BaseAttributesFactory {

    private final ImmutableAttributes root;
    private final Map<ImmutableAttributes, ImmutableList<DefaultImmutableAttributesContainer>> children;
    private final UsageCompatibilityHandler usageCompatibilityHandler;

    public DefaultBaseAttributesFactory(NamedObjectInstantiator instantiator) {
        this.root = ImmutableAttributes.EMPTY;
        this.children = new ConcurrentHashMap<>();
        this.usageCompatibilityHandler = new UsageCompatibilityHandler(instantiator);
    }

    @Override
    public <T> ImmutableAttributes of(Attribute<T> key, Isolatable<T> value) {
        return concat(root, key, value);
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
            AttributeValue<?> value = current.findEntry(attribute.getName());
            if (!value.isPresent()) {
                current = doConcatIsolatable(current, attribute, fallback.getAttributeValue(attribute));
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
            AttributeValue<?> value = current.findEntry(attribute.getName());
            if (value.isPresent()) {
                Object currentAttribute = value.get();
                Object existingAttribute = attributes1.getAttribute(attribute);
                if (!currentAttribute.equals(existingAttribute)) {
                    throw new AttributeMergingException(attribute, existingAttribute, currentAttribute);
                }
            }
            current = doConcatIsolatable(current, attribute, value.getIsolatableValue());
        }
        return current;
    }

    @Override
    public ImmutableAttributes fromMap(Map<Attribute<?>, Isolatable<?>> attributes) {
        ImmutableAttributes result = ImmutableAttributes.EMPTY;
        for (Map.Entry<Attribute<?>, ?> entry : attributes.entrySet()) {
            /*
                The order of the concatenation arguments here is important, as we have tests like
                ConfigurationCacheDependencyResolutionIntegrationTest and ConfigurationCacheDependencyResolutionIntegrationTest
                that rely on a particular order of failures when there are multiple invalid attribute type
                conversions.  So even if it looks unnatural to list result second, this should remain.
             */
            result = concatEntry(Cast.uncheckedNonnullCast(entry), result);
        }
        return result;
    }

    private <T> ImmutableAttributes concatEntry(Map.Entry<Attribute<T>, Isolatable<T>> entry, ImmutableAttributes base) {
        return concat(of(entry.getKey(), entry.getValue()), base);
    }

}
