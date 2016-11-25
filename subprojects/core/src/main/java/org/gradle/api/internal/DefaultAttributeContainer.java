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

package org.gradle.api.internal;

import com.google.common.collect.Maps;
import org.gradle.api.Attribute;
import org.gradle.api.AttributeContainer;
import org.gradle.internal.Cast;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

// TODO: CC: Maybe it would be good to have a parent AttributeContainer too, so that
// in the case of artifacts, we can inherit the attributes from the configuration
public class DefaultAttributeContainer implements AttributeContainerInternal {

    private Map<Attribute<?>, Object> attributes;

    private void ensureAttributes() {
        if (this.attributes == null) {
            this.attributes = Maps.newHashMap();
        }
    }

    @Override
    public Set<Attribute<?>> keySet() {
        if (attributes == null) {
            return Collections.emptySet();
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
        return Cast.uncheckedCast(attributes == null ? null : attributes.get(key));
    }

    @Override
    public boolean isEmpty() {
        return attributes == null;
    }

    @Override
    public boolean contains(Attribute<?> key) {
        return attributes != null && attributes.containsKey(key);
    }

    public AttributeContainer asImmutable() {
        if (attributes == null) {
            return EMPTY;
        }
        return new ImmutableAttributes(attributes);
    }

    public DefaultAttributeContainer copy() {
        DefaultAttributeContainer container = new DefaultAttributeContainer();
        if (!isEmpty()) {
            container.ensureAttributes();
            container.attributes.putAll(this.attributes);
        }
        return container;
    }

    @Override
    public AttributeContainer getAttributes() {
        return this;
    }
}
