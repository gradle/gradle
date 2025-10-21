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
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultAttributesFactory implements AttributesFactory {

    private static final ImmutableAttributes ROOT = ImmutableAttributes.EMPTY;

    private final AttributeValueIsolator attributeValueIsolator;
    private final PropertyFactory propertyFactory;
    private final UsageCompatibilityHandler usageCompatibilityHandler;
    private final NamedObjectInstantiator instantiator;

    /**
     * A map from parent attribute containers to the set of containers that have
     * been produced by appending a single entry to it.
     */
    private final Map<ImmutableAttributes, ImmutableList<ImmutableAttributes>> concatCache;

    public DefaultAttributesFactory(
        AttributeValueIsolator attributeValueIsolator,
        IsolatableFactory isolatableFactory,
        NamedObjectInstantiator instantiator,
        PropertyFactory propertyFactory
    ) {
        this.attributeValueIsolator = attributeValueIsolator;
        this.propertyFactory = propertyFactory;
        this.usageCompatibilityHandler = new UsageCompatibilityHandler(isolatableFactory, instantiator);

        this.concatCache = new ConcurrentHashMap<>();
        this.instantiator = instantiator;
    }

    @Override
    public AttributeContainerInternal mutable() {
        return new DefaultMutableAttributeContainer(this, attributeValueIsolator, instantiator, propertyFactory);
    }

    @Override
    public AttributeContainerInternal mutable(AttributeContainerInternal fallback) {
        return join(fallback, mutable());
    }

    @Override
    public AttributeContainerInternal join(AttributeContainerInternal fallback, AttributeContainerInternal primary) {
        return new HierarchicalMutableAttributeContainer(this, fallback, primary);
    }

    @Override
    public <T> ImmutableAttributes of(Attribute<T> key, T value) {
        return concat(ROOT, key, value);
    }

    @Override
    public <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, T value) {
        return concat(node, key, attributeValueIsolator.isolate(value));
    }

    @Override
    public <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, Isolatable<T> value) {
        if (key.equals(Usage.USAGE_ATTRIBUTE) || key.getName().equals(Usage.USAGE_ATTRIBUTE.getName())) {
            return usageCompatibilityHandler.doConcat(this, node, key, value);
        } else {
            return doConcatEntry(node, new DefaultImmutableAttributesEntry<>(key, value));
        }
    }

    /* package */ <T> ImmutableAttributes doConcatEntry(ImmutableAttributes node, ImmutableAttributesEntry<T> entry) {
        assertAttributeNotAlreadyPresent(node, entry.getKey());

        // Try to retrieve a cached value without locking
        ImmutableList<ImmutableAttributes> cachedChildren = concatCache.get(node);
        if (cachedChildren != null) {
            ImmutableAttributes child = findChild(cachedChildren, entry);
            if (child != null) {
                return child;
            }
        }

        // If we didn't find a cached value, we need to lock and update the cache
        cachedChildren = concatCache.compute(node, (k, nodeChildren) -> {
            if (nodeChildren != null) {
                // Check if the value is already present again, now that we have the lock.
                ImmutableAttributes child = findChild(nodeChildren, entry);
                if (child != null) {
                    // Somebody updated the cache before we could. Return the cache unchanged.
                    return nodeChildren;
                }
            } else {
                nodeChildren = ImmutableList.of();
            }

            // Nobody has tried to concat this value yet.
            // Calculate it and add it to the children.
            ImmutableAttributes child = new DefaultImmutableAttributesContainer(node, entry);
            return concatChild(nodeChildren, child);
        });

        return Objects.requireNonNull(findChild(cachedChildren, entry));
    }

    private static @Nullable ImmutableAttributes findChild(
        ImmutableList<ImmutableAttributes> nodeChildren,
        ImmutableAttributesEntry<?> entry
    ) {
        for (ImmutableAttributes child : nodeChildren) {
            ImmutableAttributesEntry<?> headEntry = child.getHead();

            if (headEntry.getKey().equals(entry.getKey()) &&
                headEntry.getValue().equals(entry.getValue())
            ) {
                return child;
            }
        }

        return null;
    }

    private static ImmutableList<ImmutableAttributes> concatChild(
        ImmutableList<ImmutableAttributes> nodeChildren,
        ImmutableAttributes child
    ) {
        return ImmutableList.<ImmutableAttributes>builderWithExpectedSize(nodeChildren.size() + 1)
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
        for (ImmutableAttributesEntry<?> toConcat : fallback.getEntries()) {
            if (current.findEntry(toConcat.getKey().getName()) == null) {
                current = doConcatEntry(current, toConcat);
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
        for (ImmutableAttributesEntry<?> toConcat : attributes1.getEntries()) {
            ImmutableAttributesEntry<?> existing = current.findEntry(toConcat.getKey().getName());
            if (existing != null && !toConcat.getIsolatedValue().equals(existing.getIsolatedValue())) {
                Attribute<?> attribute = toConcat.getKey();
                String message = "An attribute named '" + attribute.getName() + "' of type '" + attribute.getType().getName() + "' already exists in this container";
                throw new AttributeMergingException(attribute, toConcat.getIsolatedValue(), existing.getIsolatedValue(), message);
            }
            current = doConcatEntry(current, toConcat);
        }

        return current;
    }

    @Override
    public ImmutableAttributes fromEntries(Collection<AttributeEntry<?>> entries) {
        /*
         * This should use safeConcat, but can't because of how the GradleModuleMetadataParser
         * uses the DefaultAttributesFactory in consumeAttributes.  See the "can detect incompatible X when merging" tests
         * in DefaultAttributesFactoryTest for examples of the type of behavior this method must
         * support.
         *
         * Eventually, we should construct that set of attributes differently, in a way that
         * allows us to use a safeConcat here.  Possibly we can use a different implementation
         * of this method in a different factory implementation, and let this one do safeConcat.
         */
        ImmutableAttributes result = ImmutableAttributes.EMPTY;
        for (AttributeEntry<?> entry : entries) {
            result = concatEntry(result, entry);
        }
        return result;
    }

    /**
     * Concatenates an attribute entry to an immutable attributes instance.
     */
    private <T> ImmutableAttributes concatEntry(ImmutableAttributes attributes, AttributeEntry<T> entry) {
        return concat(attributes, entry.getKey(), entry.getValue());
    }

    /**
     * Verifies that an attribute with the same name but different types as the given key is not
     * already present in the given container.
     *
     * @param container the container to check
     * @param key the attribute to check for
     *
     * @throws IllegalArgumentException if attribute with same name and different type already exists
     */
    void assertAttributeNotAlreadyPresent(ImmutableAttributes container, Attribute<?> key) {
        ImmutableAttributesEntry<?> entry = container.findEntry(key.getName());
        if (entry != null && entry.getKey().getType() != key.getType()) {
            throw new IllegalArgumentException(buildSameNameDifferentTypeErrorMsg(key, entry.getKey()));
        }
    }

    private String buildSameNameDifferentTypeErrorMsg(Attribute<?> newAttribute, Attribute<?> oldAttribute) {
        return "Cannot have two attributes with the same name but different types. "
            + "This container already has an attribute named '" + newAttribute.getName() + "' of type '" + oldAttribute.getType().getName()
            + "' and you are trying to store another one of type '" + newAttribute.getType().getName() + "'";
    }

}
