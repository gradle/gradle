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

import org.gradle.api.Attribute;
import org.gradle.api.AttributeContainer;
import org.gradle.internal.Cast;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

class ImmutableAttributes implements AttributeContainerInternal {

    private static final Comparator<Attribute<?>> ATTRIBUTE_NAME_COMPARATOR = new Comparator<Attribute<?>>() {
        @Override
        public int compare(Attribute<?> o1, Attribute<?> o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };
    private final Map<Attribute<?>, Object> attributes;

    ImmutableAttributes(Map<Attribute<?>, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public Set<Attribute<?>> keySet() {
        return attributes.keySet();
    }

    @Override
    public <T> AttributeContainer attribute(Attribute<T> key, T value) {
        throw new UnsupportedOperationException("Mutation of attributes returned by Configuration#getAttributes() is not allowed");
    }

    @Override
    public <T> T getAttribute(Attribute<T> key) {
        return Cast.uncheckedCast(attributes.get(key));
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean contains(Attribute<?> key) {
        return attributes.containsKey(key);
    }

    @Override
    public AttributeContainer asImmutable() {
        return this;
    }

    @Override
    public AttributeContainer copy() {
        AttributeContainer copy = new DefaultAttributeContainer();
        for (Attribute<?> attribute : attributes.keySet()) {
            Attribute<Object> castAttribute = Cast.uncheckedCast(attribute);
            copy.attribute(castAttribute, attributes.get(castAttribute));
        }
        return copy;
    }

    @Override
    public AttributeContainer getAttributes() {
        return this;
    }

    @Override
    public String toString() {
        if (attributes != null) {
            TreeMap<Attribute<?>, Object> sorted = new TreeMap<Attribute<?>, Object>(ATTRIBUTE_NAME_COMPARATOR);
            sorted.putAll(attributes);
            return sorted.toString();
        }
        return "{}";
    }
}
