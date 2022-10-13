/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Joins a primary and fallback attribute container to each other. Any attribute in the primary
 * container will override attributes in the fallback container. All mutation operations are
 * forwarded to the primary container.
 */
public class HierarchicalAttributeContainer extends AbstractAttributeContainer {
    private final ImmutableAttributesFactory attributesFactory;
    private final AttributeContainerInternal fallback;
    private final AttributeContainerInternal primary;

    public HierarchicalAttributeContainer(ImmutableAttributesFactory attributesFactory, AttributeContainerInternal fallback, AttributeContainerInternal primary) {
        this.attributesFactory = attributesFactory;
        this.fallback = fallback;
        this.primary = primary;
    }

    @Override
    public Set<Attribute<?>> keySet() {
        return Sets.union(fallback.keySet(), primary.keySet());
    }

    @Override
    public <T> AttributeContainer attribute(Attribute<T> key, T value) {
        primary.attribute(key, value);
        return this;
    }

    @Override
    public <T> AttributeContainer attributeProvider(Attribute<T> key, Provider<? extends T> provider) {
        primary.attributeProvider(key, provider);
        return this;
    }

    @Nullable
    @Override
    public <T> T getAttribute(Attribute<T> key) {
        T attribute = primary.getAttribute(key);
        if (attribute != null) {
            return attribute;
        }
        return fallback.getAttribute(key);
    }

    @Override
    public ImmutableAttributes asImmutable() {
        if (primary.isEmpty()) {
            return fallback.asImmutable();
        }
        if (fallback.isEmpty()) {
            return primary.asImmutable();
        }
        return attributesFactory.concat(fallback.asImmutable(), primary.asImmutable());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HierarchicalAttributeContainer that = (HierarchicalAttributeContainer) o;
        return fallback.equals(that.fallback) && primary.equals(that.primary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fallback, primary);
    }

    @Override
    public String toString() {
        final Map<Attribute<?>, Object> sorted = new TreeMap<>(Comparator.comparing(Attribute::getName));
        fallback.keySet().forEach(key -> sorted.put(key, fallback.getAttribute(key)));
        primary.keySet().forEach(key -> sorted.put(key, primary.getAttribute(key)));
        return sorted.toString();
    }
}
