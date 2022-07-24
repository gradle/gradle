/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.Sets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

class DefaultMutableAttributeContainer implements AttributeContainerInternal {
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final AttributeContainerInternal parent;
    private ImmutableAttributes state = ImmutableAttributes.EMPTY;
    private Map<Attribute<?>, Provider<?>> lazyAttributes = Cast.uncheckedCast(Collections.EMPTY_MAP);

    public DefaultMutableAttributeContainer(ImmutableAttributesFactory immutableAttributesFactory) {
        this(immutableAttributesFactory, null);
    }

    public DefaultMutableAttributeContainer(ImmutableAttributesFactory immutableAttributesFactory, @Nullable AttributeContainerInternal parent) {
        this.immutableAttributesFactory = immutableAttributesFactory;
        this.parent = parent;
    }

    @Override
    public String toString() {
        final Map<Attribute<?>, Object> sorted = new TreeMap<>(Comparator.comparing(Attribute::getName));

        state.keySet().forEach(key -> sorted.put(key, state.getAttribute(key)));
        if (null != parent) {
            parent.keySet().forEach(key -> sorted.put(key, parent.getAttribute(key)));
        }
        lazyAttributes.keySet().forEach(key -> sorted.put(key, lazyAttributes.get(key).toString()));

        return sorted.toString();
    }

    @Override
    public Set<Attribute<?>> keySet() {
        if (parent == null) {
            return nonParentKeys();
        } else {
            return Sets.union(parent.keySet(), nonParentKeys());
        }
    }

    private Set<Attribute<?>> nonParentKeys() {
        return Sets.union(state.keySet(), lazyAttributes.keySet());
    }

    @Override
    public <T> AttributeContainer attribute(Attribute<T> key, T value) {
        checkInsertionAllowed(key);
        doInsertion(key, value);
        return this;
    }

    private <T> void doInsertion(Attribute<T> key, T value) {
        assertAttributeValueIsNotNull(value);
        assertAttributeTypeIsValid(value.getClass(), key);
        state = immutableAttributesFactory.concat(state, key, value);
        removeLazyAttributeIfPresent(key);
    }

    private <T> void removeLazyAttributeIfPresent(Attribute<T> key) {
        if (lazyAttributes.containsKey(key)) {
            lazyAttributes.remove(key);
        }
    }

    @Override
    public <T> AttributeContainer attributeProvider(Attribute<T> key, Provider<? extends T> provider) {
        checkInsertionAllowed(key);
        assertAttributeValueIsNotNull(provider);
        // We can only sometimes check the type of the provider ahead of time.
        // When realizing this provider and inserting its value into the container, we still
        // check the value type is appropriate. see doInsertion
        if (provider instanceof ProviderInternal) {
            Class<T> valueType = Cast.<ProviderInternal<T>>uncheckedCast(provider).getType();
            if (valueType != null) {
                assertAttributeTypeIsValid(valueType, key);
            }
        }
        doInsertionLazy(key, provider);
        return this;
    }

    private <T> void checkInsertionAllowed(Attribute<T> key) {
        // Don't just use keySet() method instead, since we should be allowed to override attributes already in the parent
        for (Attribute<?> attribute : nonParentKeys()) {
            String name = key.getName();
            if (attribute.getName().equals(name) && attribute.getType() != key.getType()) {
                throw new IllegalArgumentException("Cannot have two attributes with the same name but different types. "
                    + "This container already has an attribute named '" + name + "' of type '" + attribute.getType().getName()
                    + "' and you are trying to store another one of type '" + key.getType().getName() + "'");
            }
        }
    }

    /**
     * Checks that the attribute's type matches the given value's type is the expected value type.
     *
     * @param valueType the value type to check
     * @param attribute the attribute containing a type to check against
     */
    private <T> void assertAttributeTypeIsValid(Class<?> valueType, Attribute<T> attribute) {
        if (!attribute.getType().isAssignableFrom(valueType)) {
            throw new IllegalArgumentException(String.format("Unexpected type for attribute '%s' provided. Expected a value of type %s but found a value of type %s.", attribute.getName(), attribute.getType().getName(), valueType.getName()));
        }
    }

    private void assertAttributeValueIsNotNull(@Nullable Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Setting null as an attribute value is not allowed");
        }
    }

    @Override
    public <T> T getAttribute(Attribute<T> key) {
        T attribute = state.getAttribute(key);
        if (attribute == null && lazyAttributes.containsKey(key)) {
            attribute = realizeLazyAttribute(key);
        }
        if (attribute == null && parent != null) {
            attribute = parent.getAttribute(key);
        }
        return attribute;
    }

    @Override
    public boolean isEmpty() {
        return keySet().isEmpty();
    }

    @Override
    public boolean contains(Attribute<?> key) {
        return keySet().contains(key);
    }

    @Override
    public ImmutableAttributes asImmutable() {
        realizeAllLazyAttributes();

        if (parent == null) {
            return state;
        } else {
            ImmutableAttributes attributes = parent.asImmutable();
            if (!state.isEmpty()) {
                attributes = immutableAttributesFactory.concat(attributes, state);
            }
            return attributes;
        }
    }

    @Override
    public Map<Attribute<?>, ?> asMap() {
        return asImmutable().asMap();
    }

    @Override
    public AttributeContainer getAttributes() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultMutableAttributeContainer that = (DefaultMutableAttributeContainer) o;

        if (!Objects.equals(parent, that.parent)) {
            return false;
        }
        if (!Objects.equals(asImmutable(), that.asImmutable())) {
            return false;
        }

        return state.equals(that.state);
    }

    @Override
    public int hashCode() {
        int result = parent != null ? parent.hashCode() : 0;
        result = 31 * result + state.hashCode();
        result = 31 * result + asImmutable().hashCode();
        return result;
    }

    private <T> void doInsertionLazy(Attribute<T> key, Provider<? extends T> provider) {
        if (lazyAttributes == Collections.EMPTY_MAP) {
            lazyAttributes = new LinkedHashMap<>(1);
        }
        lazyAttributes.put(key, provider);
        removeAttributeIfPresent(key);
    }

    private <T> void removeAttributeIfPresent(Attribute<T> key) {
        if (state.contains(key)) {
            DefaultMutableAttributeContainer newState = new DefaultMutableAttributeContainer(immutableAttributesFactory, parent);
            state.keySet().stream()
                    .filter(k -> !k.equals(key))
                    .forEach(k -> {
                        @SuppressWarnings("unchecked") Attribute<Object> objectKey = (Attribute<Object>) k;
                        newState.attribute(objectKey, Objects.requireNonNull(state.getAttribute(k)));
                    });
            state = newState.asImmutable();
        }
    }

    private <T> T realizeLazyAttribute(Attribute<T> key) {
        @SuppressWarnings("unchecked") final T value = (T) lazyAttributes.get(key).get();
        doInsertion(key, value);
        return value;
    }

    private void realizeAllLazyAttributes() {
        if (!lazyAttributes.isEmpty()) {
            // As doInsertion will remove an item from lazyAttributes, we can't iterate that collection directly here, or else we'll get ConcurrentModificationException
            final Set<Attribute<?>> savedKeys = new HashSet<>(lazyAttributes.keySet());
            savedKeys.forEach(key -> doInsertion(Cast.uncheckedNonnullCast(key), lazyAttributes.get(key).get()));
        }
    }
}
