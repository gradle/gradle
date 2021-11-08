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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class DefaultMutableAttributeContainer implements AttributeContainerInternal {
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final AttributeContainerInternal parent;
    private ImmutableAttributes state = ImmutableAttributes.EMPTY;
    private final Map<Attribute<?>, Provider<?>> lazyAttributes = Maps.newHashMap();

    public DefaultMutableAttributeContainer(ImmutableAttributesFactory immutableAttributesFactory) {
        this(immutableAttributesFactory, null);
    }

    public DefaultMutableAttributeContainer(ImmutableAttributesFactory immutableAttributesFactory, @Nullable AttributeContainerInternal parent) {
        this.immutableAttributesFactory = immutableAttributesFactory;
        this.parent = parent;
    }

    @Override
    public String toString() {
        return asImmutable().toString();
    }

    @Override
    public Set<Attribute<?>> keySet() {
        final Set<Attribute<?>> selfKeys = Sets.union(state.keySet(), lazyAttributes.keySet());
        if (parent == null) {
            return selfKeys;
        } else {
            return Sets.union(parent.keySet(), selfKeys);
        }
    }

    @Override
    public <T> AttributeContainer attribute(Attribute<T> key, T value) {
        assertAttributeTypeIsValid(value, key);
        checkInsertionAllowed(key);
        state = immutableAttributesFactory.concat(state, key, value);
        return this;
    }

    @Override
    public <T> AttributeContainer attribute(Attribute<T> key, Provider<? extends T> provider) {
        assertAttributeTypeIsValid(provider, key);
        checkInsertionAllowed(key);
        lazyAttributes.put(key, provider);
        return this;
    }

    private <T> void checkInsertionAllowed(Attribute<T> key) {
        // Don't just use keySet() method instead, since we should be allowed to override attributes alread in the parent
        final Set<Attribute<?>> keys = Sets.union(state.keySet(), lazyAttributes.keySet());
        for (Attribute<?> attribute : keys) {
            String name = key.getName();
            if (attribute.getName().equals(name) && attribute.getType() != key.getType()) {
                throw new IllegalArgumentException("Cannot have two attributes with the same name but different types. "
                    + "This container already has an attribute named '" + name + "' of type '" + attribute.getType().getName()
                    + "' and you are trying to store another one of type '" + key.getType().getName() + "'");
            }
        }
    }

    /**
     * Checks that the attribute's type matches the given value's type (if a non-{@link Provider}), or the {@link ProviderInternal#getType()} if the
     * value is a {@link ProviderInternal}; as well as the expectedValueType.
     *
     * If the value is a {@link Provider} but <strong>NOT</strong> also
     * a {@link ProviderInternal}, then no assertions are made (this type of lazily added attribute will fail at the time of retrieval from this container).
     *
     * @param value the value (or value provider) to check
     * @param attribute the attribute containing a type to check against
     */
    private void assertAttributeTypeIsValid(@Nullable Object value, Attribute<?> attribute) {
        if (value == null) {
            throw new IllegalArgumentException("Setting null as an attribute value is not allowed");
        }

        final Class<?> actualValueType;
        if (ProviderInternal.class.isAssignableFrom(value.getClass())) {
            ProviderInternal<?> provider = Cast.uncheckedCast(value);
            actualValueType = provider.getType();
        } else if (!Provider.class.isAssignableFrom(value.getClass())) {
            actualValueType = value.getClass();
        } else {
            actualValueType = null;
        }

        if (null != actualValueType) {
            if (!attribute.getType().isAssignableFrom(actualValueType)) {
                throw new IllegalArgumentException("Unexpected type for attribute: '" + attribute.getName() + "'. Attribute type: " + attribute.getType().getName() + " did not match actual type: " + actualValueType.getName());
            }
        }
    }

    @Override
    public <T> T getAttribute(Attribute<T> key) {
        T attribute = state.getAttribute(key);
        if (attribute == null && parent != null) {
            attribute = parent.getAttribute(key);
        }
        if (attribute == null && lazyAttributes.containsKey(key)) {
            attribute = getLazyAttribute(key);
        }
        return attribute;
    }

    @Nullable
    private <T> T getLazyAttribute(Attribute<T> key) {
        if (lazyAttributes.containsKey(key)) {
            @SuppressWarnings("unchecked") final T value = (T) lazyAttributes.get(key).get();
            lazyAttributes.remove(key);
            attribute(key, value);
            return getAttribute(key);
        } else {
            return null;
        }
    }

    @Override
    public boolean isEmpty() {
        return state.isEmpty() && lazyAttributes.isEmpty() && (parent == null || parent.isEmpty());
    }

    @Override
    public boolean contains(Attribute<?> key) {
        return state.contains(key) || lazyAttributes.containsKey(key) || (parent != null && parent.contains(key));
    }

    @Override
    public ImmutableAttributes asImmutable() {
        if (parent == null) {
            if (lazyAttributes.isEmpty()) {
                // There is a recursive call relationship between this method and evaluateLazyAttributes(), this check is the base case
                return state;
            } else {
                return immutableAttributesFactory.concat(state, evaluateLazyValues());
            }
        } else {
            ImmutableAttributes attributes = parent.asImmutable();
            if (!state.isEmpty()) {
                attributes = immutableAttributesFactory.concat(immutableAttributesFactory.concat(attributes, state), evaluateLazyValues());
            }
            return attributes;
        }
    }

    @Override
    public Map<Attribute<?>, ?> asMap() {
        Map<Attribute<?>, ?> map = Maps.newLinkedHashMap();
        for (Attribute<?> attribute : keySet()) {
            map.put(attribute, Cast.uncheckedCast(getAttribute(attribute)));
        }
        return map;
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
        if (!Objects.equals(lazyAttributes, that.lazyAttributes)) {
            return false;
        }

        return state.equals(that.state);
    }

    @Override
    public int hashCode() {
        int result = parent != null ? parent.hashCode() : 0;
        result = 31 * result + state.hashCode();
        result = 31 * result + lazyAttributes.hashCode();
        return result;
    }

    private ImmutableAttributes evaluateLazyValues() {
        final AttributeContainerInternal evaluatedAttributes = immutableAttributesFactory.mutable();
        for (Map.Entry<Attribute<?>, Provider<?>> entry : lazyAttributes.entrySet()) {
            @SuppressWarnings("unchecked") Attribute<Object> attribute = (Attribute<Object>) entry.getKey();
            Object value = entry.getValue().get();
            evaluatedAttributes.attribute(attribute, value);
        }
        return evaluatedAttributes.asImmutable();
    }
}
