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

import java.util.Set;

class DefaultMutableAttributeContainer implements AttributeContainerInternal {
    private final ImmutableAttributesFactory cache;
    private final AttributeContainerInternal parent;
    private ImmutableAttributes state = ImmutableAttributes.EMPTY;

    public DefaultMutableAttributeContainer(ImmutableAttributesFactory cache) {
        this(cache, null);
    }

    public DefaultMutableAttributeContainer(ImmutableAttributesFactory cache, AttributeContainerInternal parent) {
        this.cache = cache;
        this.parent = parent;
    }

    @Override
    public String toString() {
        return asImmutable().toString();
    }

    @Override
    public Set<Attribute<?>> keySet() {
        if (parent == null) {
            return state.keySet();
        } else {
            return Sets.union(parent.keySet(), state.keySet());
        }
    }

    @Override
    public <T> AttributeContainer attribute(Attribute<T> key, T value) {
        assertAttributeConstraints(value, key);
        checkInsertionAllowed(key);
        state = cache.concat(state, key, value);
        return this;
    }

    private <T> void checkInsertionAllowed(Attribute<T> key) {
        for (Attribute<?> attribute : state.keySet()) {
            String name = key.getName();
            if (attribute.getName().equals(name) && attribute.getType() != key.getType()) {
                throw new IllegalArgumentException("Cannot have two attributes with the same name but different types. "
                    + "This container already has an attribute named '" + name + "' of type '" + attribute.getType().getName()
                    + "' and you are trying to store another one of type '" + key.getType().getName() + "'");
            }
        }
    }

    private static void assertAttributeConstraints(Object value, Attribute<?> attribute) {
        if (value == null) {
            throw new IllegalArgumentException("Setting null as an attribute value is not allowed");
        }
        if (!attribute.getType().isAssignableFrom(value.getClass())) {
            throw new IllegalArgumentException("Unexpected type for attribute '" + attribute.getName() + "'. Expected " + attribute.getType().getName() + " but was:" + value.getClass().getName());
        }
    }

    @Override
    public <T> T getAttribute(Attribute<T> key) {
        T attribute = state.getAttribute(key);
        if (attribute == null && parent != null) {
            return parent.getAttribute(key);
        }
        return attribute;
    }

    @Override
    public boolean isEmpty() {
        return state.isEmpty() && (parent == null || parent.isEmpty());
    }

    @Override
    public boolean contains(Attribute<?> key) {
        return state.contains(key) || (parent != null && parent.contains(key));
    }

    @Override
    public ImmutableAttributes asImmutable() {
        if (parent == null) {
            return state;
        } else {
            ImmutableAttributes attributes = parent.asImmutable();
            if (!state.isEmpty()) {
                attributes = cache.concat(attributes, state);
            }
            return attributes;
        }
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

        if (parent != null ? !parent.equals(that.parent) : that.parent != null) {
            return false;
        }
        return state.equals(that.state);
    }

    @Override
    public int hashCode() {
        int result = parent != null ? parent.hashCode() : 0;
        result = 31 * result + state.hashCode();
        return result;
    }
}
