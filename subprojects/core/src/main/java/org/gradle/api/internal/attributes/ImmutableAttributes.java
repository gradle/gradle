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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.internal.Cast;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ImmutableAttributes implements AttributeContainerInternal {

    public static final ImmutableAttributes EMPTY = new ImmutableAttributes(Collections.<Attribute<?>, Object>emptyMap());

    private final static LoadingCache<Map<Attribute<?>, Object>, ImmutableAttributes> CACHE = CacheBuilder
        .newBuilder()
        .weakValues()
        .build(new CacheLoader<Map<Attribute<?>, Object>, ImmutableAttributes>() {
            @Override
            public ImmutableAttributes load(Map<Attribute<?>, Object> key) throws Exception {
                return new ImmutableAttributes(key);
            }
        });

    private static final Comparator<Attribute<?>> ATTRIBUTE_NAME_COMPARATOR = new Comparator<Attribute<?>>() {
        @Override
        public int compare(Attribute<?> o1, Attribute<?> o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };
    private final ImmutableMap<Attribute<?>, Object> attributes;
    private final int hashCode;

    public static ImmutableAttributes of(Map<Attribute<?>, Object> attributes) {
        return CACHE.getUnchecked(attributes);
    }

    public static ImmutableAttributes concat(AttributeContainerInternal... containers) {
        if (containers.length == 0) {
            return EMPTY;
        }
        if (containers.length == 1) {
            return containers[0].asImmutable();
        }
        Map<Attribute<?>, Object> concat = Maps.newHashMap();
        for (AttributeContainerInternal container : containers) {
            Set<Attribute<?>> attributes = container.keySet();
            for (Attribute<?> attribute : attributes) {
                concat.put(attribute, Cast.uncheckedCast(container.getAttribute(attribute)));
            }
        }
        return of(concat);
    }

    public static ImmutableAttributes concat(AttributeContainerInternal container, Map<Attribute<?>, Object> additionalAttributes) {
        if (additionalAttributes.isEmpty()) {
            return container.asImmutable();
        }
        if (container.isEmpty()) {
            return of(additionalAttributes);
        }
        Map<Attribute<?>, Object> concat = Maps.newHashMap();
        Set<Attribute<?>> attributes = container.keySet();
        for (Attribute<?> attribute : attributes) {
            concat.put(attribute, Cast.uncheckedCast(container.getAttribute(attribute)));
        }
        for (Map.Entry<Attribute<?>, Object> entry : additionalAttributes.entrySet()) {
            Attribute<?> attribute = entry.getKey();
            concat.put(attribute, Cast.uncheckedCast(entry.getValue()));
        }
        return of(concat);
    }

    private ImmutableAttributes(Map<Attribute<?>, Object> attributes) {
        this.attributes = ImmutableMap.copyOf(attributes);
        this.hashCode = 31 * this.attributes.hashCode() + 1;
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
        return attributes.isEmpty();
    }

    @Override
    public boolean contains(Attribute<?> key) {
        return attributes.containsKey(key);
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
        sorted.putAll(attributes);
        return sorted.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableAttributes)) {
            return false;
        }
        ImmutableAttributes that = (ImmutableAttributes) o;
        return attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
