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

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.provider.MappingProvider;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.isolation.Isolatable;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

public final class DefaultMutableAttributeContainer extends AbstractAttributeContainer {

    // Services
    private final AttributesFactory attributesFactory;
    private final AttributeValueIsolator attributeValueIsolator;

    // Mutable State
    private final MapProperty<Attribute<?>, Isolatable<?>> state;

    /**
     * Should only be true when realizing lazy attributes, to protect against reentrant
     * mutation of this container.
     */
    private boolean realizingLazyState;

    public DefaultMutableAttributeContainer(
        AttributesFactory attributesFactory,
        AttributeValueIsolator attributeValueIsolator,
        PropertyFactory propertyFactory
    ) {
        this.attributesFactory = attributesFactory;
        this.attributeValueIsolator = attributeValueIsolator;
        this.state = Cast.uncheckedNonnullCast(propertyFactory.mapProperty(Attribute.class, Isolatable.class));
    }

    @Override
    public String toString() {
        Map<Attribute<?>, Object> sorted = new TreeMap<>(Comparator.comparing(Attribute::getName));
        sorted.putAll(getRealizedEntries());
        return sorted.toString();
    }

    @Override
    public Set<Attribute<?>> keySet() {
        Set<Attribute<?>> realizedKeys = doRealize(s -> s.keySet().get());
        assertNoDuplicateNames(realizedKeys);
        return realizedKeys;
    }

    @Override
    public <T> AttributeContainer attribute(Attribute<T> key, T value) {
        checkInsertionAllowed(key);
        assertAttributeValueIsNotNull(value);
        assertAttributeTypeIsValid(value.getClass(), key);
        state.put(key, attributeValueIsolator.isolate(value));
        return this;
    }

    @Override
    public <T> AttributeContainer attributeProvider(Attribute<T> key, Provider<? extends T> provider) {
        checkInsertionAllowed(key);
        assertAttributeValueIsNotNull(provider);

        ProviderInternal<T> providerInternal = Cast.uncheckedCast(provider);

        Provider<Isolatable<T>> isolated;
        Class<T> valueType = providerInternal.getType();
        Class<Isolatable<T>> typedIsolatable = Cast.uncheckedCast(Isolatable.class);
        if (valueType != null) {
            // We can only sometimes check the type of the provider ahead of time.
            assertAttributeTypeIsValid(valueType, key);
            isolated = new MappingProvider<>(typedIsolatable, providerInternal, attributeValueIsolator::isolate);
        } else {
            // Otherwise, check the type when the value is realized.
            isolated = new MappingProvider<>(typedIsolatable, providerInternal, t -> {
                assertAttributeTypeIsValid(t.getClass(), key);
                return attributeValueIsolator.isolate(t);
            });
        }

        state.put(key, isolated);

        return this;
    }

    private <T> void checkInsertionAllowed(Attribute<T> key) {
        if (realizingLazyState) {
            throw new IllegalStateException("Cannot add new attribute '" + key.getName() + "' while realizing all attributes of the container.");
        }
    }

    private Map<Attribute<?>, Isolatable<?>> getRealizedEntries() {
        Map<Attribute<?>, Isolatable<?>> realizedState = doRealize(Provider::get);
        assertNoDuplicateNames(realizedState.keySet());
        return realizedState;
    }

    private static void assertNoDuplicateNames(Set<Attribute<?>> attributes) {
        Map<String, Attribute<?>> attributesByName = new HashMap<>();
        for (Attribute<?> attribute : attributes) {
            String name = attribute.getName();
            Attribute<?> existing = attributesByName.put(name, attribute);
            if (existing != null) {
                throw new IllegalStateException("Cannot have two attributes with the same name but different types. "
                    + "This container has an attribute named '" + name + "' of type '" + existing.getType().getName()
                    + "' and another attribute of type '" + attribute.getType().getName() + "'");
            }
        }
    }

    /**
     * Perform some action against the mutable state of this container, tracking the execution of the action
     * while it is running. The additional tracked state is used to ensure the mutable state of the container
     * is not modified while the action is running.
     * <p>
     * TODO: This sort of tracking should be handled by the provider API infrastructure
     */
    private <T> T doRealize(Function<MapProperty<Attribute<?>, Isolatable<?>>, T> realizeAction) {
        realizingLazyState = true;
        try {
            return realizeAction.apply(state);
        } finally {
            realizingLazyState = false;
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
    @Nullable
    public <T> T getAttribute(Attribute<T> key) {
        if (!isValidAttributeRequest(key)) {
            return null;
        }
        return Cast.uncheckedCast(state.getting(key).map(Isolatable::isolate).getOrNull());
    }

    @Override
    public ImmutableAttributes asImmutable() {
        return attributesFactory.fromMap(getRealizedEntries());
    }

    @Override
    public boolean equals(@Nullable Object o) {
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
}
