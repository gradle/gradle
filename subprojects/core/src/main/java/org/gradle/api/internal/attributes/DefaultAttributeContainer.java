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
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.internal.Cast;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class DefaultAttributeContainer implements AttributeContainerInternal {
    private final AttributeContainerInternal parent;
    private Map<Attribute<?>, Object> attributes;

    public DefaultAttributeContainer() {
        this.parent = null;
    }

    public DefaultAttributeContainer(AttributeContainerInternal parent) {
        this.parent = parent;
    }

    @Override
    public String toString() {
        return asImmutable().toString();
    }

    private void ensureAttributes() {
        if (this.attributes == null) {
            this.attributes = Maps.newHashMap();
        }
    }

    @Override
    public Set<Attribute<?>> keySet() {
        if (attributes == null) {
            if (parent == null) {
                return Collections.emptySet();
            } else {
                return parent.keySet();
            }
        }
        return attributes.keySet();
    }

    @Override
    public <T> AttributeContainer attribute(Attribute<T> key, T value) {
        assertAttributeConstraints(value, key);
        ensureAttributes();
        checkInsertionAllowed(key);
        attributes.put(key, value);
        return this;
    }

    private <T> void checkInsertionAllowed(Attribute<T> key) {
        for (Attribute<?> attribute : attributes.keySet()) {
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
        if (attributes != null) {
            T value = key.getType().cast(attributes.get(key));
            if (value != null) {
                return value;
            }
        }
        if (parent != null) {
            return parent.getAttribute(key);
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        return (attributes == null || attributes.isEmpty()) && (parent == null || parent.isEmpty());
    }

    @Override
    public boolean contains(Attribute<?> key) {
        return attributes != null && attributes.containsKey(key) || parent != null && parent.contains(key);
    }

    public AttributeContainerInternal asImmutable() {
        if (isEmpty()) {
            return EMPTY;
        }
        if (parent == null) {
            return new ImmutableAttributes(attributes);
        }
        return copy().asImmutable();
    }

    public AttributeContainerInternal copy() {
        if (isEmpty()) {
            return new DefaultAttributeContainer();
        }
        AttributeContainerInternal copy;
        if (parent != null) {
            copy = parent.copy();
        } else {
            copy = new DefaultAttributeContainer();
        }
        if (attributes != null) {
            for (Map.Entry<Attribute<?>, Object> entry : attributes.entrySet()) {
                Attribute<Object> attribute = Cast.uncheckedCast(entry.getKey());
                copy.attribute(attribute, entry.getValue());
            }
        }
        return copy;
    }

    @Override
    public AttributeContainer getAttributes() {
        return this;
    }
}
