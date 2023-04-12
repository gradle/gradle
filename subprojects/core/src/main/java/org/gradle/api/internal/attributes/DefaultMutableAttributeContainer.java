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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

class DefaultMutableAttributeContainer extends AbstractAttributeContainer implements AttributeContainerInternal {
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private ImmutableAttributes state = ImmutableAttributes.EMPTY;
    private Map<Attribute<?>, Provider<?>> lazyAttributes = Cast.uncheckedCast(Collections.EMPTY_MAP);

    public DefaultMutableAttributeContainer(ImmutableAttributesFactory immutableAttributesFactory) {
        this.immutableAttributesFactory = immutableAttributesFactory;
    }

    @Override
    public Set<Attribute<?>> keySet() {
        return Sets.union(state.keySet(), Maps.filterValues(lazyAttributes, Provider::isPresent).keySet());
    }

    @Override
    public <T> AttributeContainer attribute(Attribute<T> key, T value) {
        checkInsertionAllowed(key);
        doInsertion(key, value);
        return this;
    }

    private <T> void doInsertion(Attribute<T> key, T value) {
        Preconditions.checkNotNull(value, "Setting null as an attribute value is not allowed");
        assertAttributeTypeIsValid(value.getClass(), key);
        state = immutableAttributesFactory.concat(state, key, value);
        removeLazyAttributeIfPresent(key);
    }

    @Override
    public <T> AttributeContainer attributeProvider(Attribute<T> key, Provider<? extends T> provider) {
        checkInsertionAllowed(key);
        Preconditions.checkNotNull(provider, "Attribute provider cannot be null");
        // We can only sometimes check the type of the provider ahead of time.
        // When realizing this provider and inserting its value into the container, we still
        // check the value type is appropriate. see doInsertion
        if (provider instanceof ProviderInternal) {
            Class<?> valueType = ((ProviderInternal<?>) provider).getType();
            if (valueType != null) {
                assertAttributeTypeIsValid(valueType, key);
            }
        }

        if (lazyAttributes == Collections.EMPTY_MAP) {
            lazyAttributes = new LinkedHashMap<>(1);
        }

        removeLazyAttributeIfPresent(key);
        lazyAttributes.put(key, provider);
        state = immutableAttributesWithoutKey(state, key);

        return this;
    }

    private void removeLazyAttributeIfPresent(Attribute<?> key) {
        // checkInsertionAllowed does not verify if we have a non-present lazy attribute with the same name but a different type.
        // In this case, we overwrite the lazy attribute with the new one even if they have a different type.
        // We need to allow this case since users are unable to query for non-present lazy attributes.
        lazyAttributes.keySet().removeIf(x -> x.getName().equals(key.getName()));
    }

    private <T> ImmutableAttributes immutableAttributesWithoutKey(ImmutableAttributes attributes, Attribute<T> key) {
        if (attributes.contains(key)) {
            DefaultMutableAttributeContainer newState = new DefaultMutableAttributeContainer(immutableAttributesFactory);
            attributes.keySet().stream()
                .filter(k -> !k.equals(key))
                .forEach(k -> {
                    @SuppressWarnings("unchecked") Attribute<Object> objectKey = (Attribute<Object>) k;
                    newState.attribute(objectKey, Objects.requireNonNull(attributes.getAttribute(k)));
                });
            return newState.asImmutable();
        }
        return attributes;
    }

    private <T> void checkInsertionAllowed(Attribute<T> key) {
        for (Attribute<?> attribute : keySet()) {
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
            throw new IllegalArgumentException(String.format(
                "Unexpected type for attribute '%s' provided. Expected a value of type %s but found a value of type %s.",
                attribute.getName(), attribute.getType().getName(), valueType.getName())
            );
        }
    }

    @Override
    public <T> T getAttribute(Attribute<T> key) {
        T attribute = state.getAttribute(key);
        if (attribute != null) {
            return attribute;
        }

        if (lazyAttributes.containsKey(key) && lazyAttributes.get(key).isPresent()) {
            @SuppressWarnings("unchecked") T value = (T) lazyAttributes.get(key).get();
            doInsertion(key, value);
            return value;
        }

        return null;
    }

    @Override
    public ImmutableAttributes asImmutable() {
        realizeAllLazyAttributes();
        return state;
    }

    private void realizeAllLazyAttributes() {
        if (!lazyAttributes.isEmpty()) {
            // Since doInsertion removes items from lazyAttributes, we can't iterate that collection
            // directly here, or else we'll get a ConcurrentModificationException
            new LinkedHashSet<>(lazyAttributes.keySet()).forEach(key -> {
                if (lazyAttributes.get(key).isPresent()) {
                    doInsertion(Cast.uncheckedNonnullCast(key), lazyAttributes.get(key).get());
                } else {
                    lazyAttributes.remove(key);
                }
            });
        }
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

        return Objects.equals(asImmutable(), that.asImmutable());
    }

    @Override
    public int hashCode() {
        return asImmutable().hashCode();
    }

    @Override
    public String toString() {
        final Map<Attribute<?>, Object> sorted = new TreeMap<>(Comparator.comparing(Attribute::getName));
        lazyAttributes.keySet().forEach(key -> sorted.put(key, lazyAttributes.get(key).toString()));
        state.keySet().forEach(key -> sorted.put(key, state.getAttribute(key)));
        return sorted.toString();
    }
}
