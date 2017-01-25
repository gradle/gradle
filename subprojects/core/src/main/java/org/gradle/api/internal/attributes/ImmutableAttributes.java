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
import org.gradle.internal.Cast;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ImmutableAttributes implements AttributeContainerInternal {

    public final static ImmutableAttributes EMPTY = new ImmutableAttributes(null);

    private static final Comparator<Attribute<?>> ATTRIBUTE_NAME_COMPARATOR = new Comparator<Attribute<?>>() {
        @Override
        public int compare(Attribute<?> o1, Attribute<?> o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };

    final ImmutableAttributes parent;
    final Attribute<?> attribute;
    final Object value;

    private final int hashCode;
    private final int size;

    // the builder here is a performance optimization. It avoids trashing a lot
    // of object that would be otherwise built again and again
    final DefaultImmutableAttributesFactory.Builder builder;

    // cache keyset in case we need it again
    private Set<Attribute<?>> keySet;

    ImmutableAttributes(ImmutableAttributesFactory owner) {
        this.builder = owner != null ? owner.builder(this) : null;
        this.parent = null;
        this.attribute = null;
        this.value = null;
        this.hashCode = 0;
        this.size = 0;
    }

    ImmutableAttributes(ImmutableAttributes parent, Attribute<?> key, Object value, ImmutableAttributesFactory owner) {
        this.parent = parent;
        this.attribute = key;
        this.value = value;
        this.builder = owner != null ? owner.builder(this) : null;
        int hashCode = parent.hashCode();
        hashCode = 31 * hashCode + attribute.hashCode();
        hashCode = 31 * hashCode + value.hashCode();
        this.hashCode = hashCode;
        this.size = parent.size + 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ImmutableAttributes that = (ImmutableAttributes) o;

        if (size != that.size) {
            return false;
        }

        ImmutableAttributes cur = this;

        while (cur.value != null) {
            if (!cur.value.equals(that.getAttribute(cur.attribute))) {
                return false;
            }
            cur = cur.parent;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public Set<Attribute<?>> keySet() {
        if (parent == null) {
            return Collections.emptySet();
        }
        if (keySet == null) {
            keySet = Sets.union(Collections.singleton(attribute), parent.keySet());
        }
        return keySet;
    }

    @Override
    public <T> AttributeContainer attribute(Attribute<T> key, T value) {
        throw new UnsupportedOperationException("Mutation of attributes is not allowed");
    }

    @Override
    public <T> T getAttribute(Attribute<T> key) {
        if (key.equals(attribute)) {
            return Cast.uncheckedCast(value);
        }
        if (parent != null) {
            return parent.getAttribute(key);
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        return attribute == null;
    }

    @Override
    public boolean contains(Attribute<?> key) {
        return key.equals(attribute) || parent != null && parent.contains(key);
    }

    @Override
    public ImmutableAttributes asImmutable() {
        return this;
    }

    @Override
    public AttributeContainerInternal copy() {
        return this;
    }

    @Override
    public AttributeContainer getAttributes() {
        return this;
    }

    @Override
    public String toString() {
        Map<Attribute<?>, Object> sorted = new TreeMap<Attribute<?>, Object>(ATTRIBUTE_NAME_COMPARATOR);
        ImmutableAttributes node = this;
        while (node != null) {
            if (node.attribute != null) {
                sorted.put(node.attribute, node.value);
            }
            node = node.parent;
        }
        return sorted.toString();
    }

}
