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

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

final class DefaultMutableAttributeContainer extends AbstractAttributeContainer implements AttributeContainerInternal {

    private final DefaultAttributesFactory attributesFactory;

    private final MapProperty<Attribute<?>, Isolatable<?>> state;

    /**
     * Should only be true when realizing lazy attributes, to protect against reentrant
     * mutation of this container.
     */
    private boolean realizingLazyState;

    public DefaultMutableAttributeContainer(DefaultAttributesFactory attributesFactory, PropertyFactory propertyFactory) {
        this.attributesFactory = attributesFactory;
        this.state = Cast.uncheckedNonnullCast(propertyFactory.mapProperty(Attribute.class, Isolatable.class));
    }

    @Override
    public String toString() {
        final Map<Attribute<?>, Object> sorted = new TreeMap<>(Comparator.comparing(Attribute::getName));
        sorted.putAll(getRealizedAttributes());
        return sorted.toString();
    }

    @Override
    public Set<Attribute<?>> keySet() {
        return getRealizedAttributes().keySet();
    }

    @Override
    public <T> AttributeContainer attribute(Attribute<T> key, T value) {
        checkInsertionAllowed(key);
        assertAttributeValueIsNotNull(value);
        assertAttributeTypeIsValid(value.getClass(), key);
        state.put(key, attributesFactory.isolate(value));
        return this;
    }

    @Override
    public <T> AttributeContainer attributeProvider(Attribute<T> key, Provider<? extends T> provider) {
        checkInsertionAllowed(key);
        assertAttributeValueIsNotNull(provider);

        ProviderInternal<T> providerInternal = Cast.uncheckedCast(provider);

        MappingProvider<Isolatable<T>, T> isolated;
        Class<T> valueType = providerInternal.getType();
        if (valueType != null) {
            // We can only sometimes check the type of the provider ahead of time.
            assertAttributeTypeIsValid(valueType, key);
            isolated = new MappingProvider<>(null, providerInternal, attributesFactory::isolate);
        } else {
            // Otherwise, check the type when the value is realized.
            isolated = new MappingProvider<>(null, providerInternal, t -> {
                assertAttributeTypeIsValid(t.getClass(), key);
                return attributesFactory.isolate(t);
            });
        }

        state.put(key, isolated);

        return this;
    }

    private <T> void checkInsertionAllowed(Attribute<T> key) {
        // TODO: This check should be handled by the provider API infrastructure
        if (realizingLazyState) {
            throw new IllegalStateException("Cannot add new attribute '" + key.getName() + "' while realizing all attributes of the container.");
        }
    }

    private Map<Attribute<?>, Isolatable<?>> getRealizedAttributes() {
        Map<Attribute<?>, Isolatable<?>> realizedState = realizedDeclaredState();

        Map<String, Attribute<?>> attributesByName = new HashMap<>();
        for (Map.Entry<Attribute<?>, Isolatable<?>> entry : realizedState.entrySet()) {
            Attribute<?> attribute = entry.getKey();
            String name = attribute.getName();
            Attribute<?> existing = attributesByName.put(name, attribute);
            if (existing != null) {
                throw new IllegalStateException("Cannot have two attributes with the same name but different types. "
                    + "This container has an attribute named '" + name + "' of type '" + existing.getType().getName()
                    + "' and another attribute of type '" + attribute.getType().getName() + "'");
            }
        }

        return realizedState;
    }

    private Map<Attribute<?>, Isolatable<?>> realizedDeclaredState() {
        realizingLazyState = true;
        try {
            return state.get();
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
    public <T> T getAttribute(Attribute<T> key) {
        return Cast.uncheckedCast(state.getting(key).map(Isolatable::isolate).getOrNull());
    }

    @Override
    public ImmutableAttributes asImmutable() {
        return attributesFactory.fromMap(getRealizedAttributes());
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
}
