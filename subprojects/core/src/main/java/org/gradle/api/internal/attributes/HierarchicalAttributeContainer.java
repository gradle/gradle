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
 * Joins a parent attribute container to a child. Any attribute in the child container will
 * override attributes in the parent container. All mutation operations are forwarded to
 * the child container.
 */
public class HierarchicalAttributeContainer extends AbstractAttributeContainer {
    private final ImmutableAttributesFactory attributesFactory;
    private final AttributeContainerInternal parent;
    private final AttributeContainerInternal child;

    public HierarchicalAttributeContainer(ImmutableAttributesFactory attributesFactory, AttributeContainerInternal parent, AttributeContainerInternal child) {
        this.attributesFactory = attributesFactory;
        this.parent = parent;
        this.child = child;
    }

    @Override
    public Set<Attribute<?>> keySet() {
        return Sets.union(parent.keySet(), child.keySet());
    }

    @Override
    public <T> AttributeContainer attribute(Attribute<T> key, T value) {
        child.attribute(key, value);
        return this;
    }

    @Override
    public <T> AttributeContainer attributeProvider(Attribute<T> key, Provider<? extends T> provider) {
        child.attributeProvider(key, provider);
        return this;
    }

    @Nullable
    @Override
    public <T> T getAttribute(Attribute<T> key) {
        T attribute = child.getAttribute(key);
        if (attribute != null) {
            return attribute;
        }
        return parent.getAttribute(key);
    }

    @Override
    public ImmutableAttributes asImmutable() {
        if (child.isEmpty()) {
            return parent.asImmutable();
        }
        if (parent.isEmpty()) {
            return child.asImmutable();
        }
        return attributesFactory.concat(parent.asImmutable(), child.asImmutable());
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
        return parent.equals(that.parent) && child.equals(that.child);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parent, child);
    }

    @Override
    public String toString() {
        final Map<Attribute<?>, Object> sorted = new TreeMap<>(Comparator.comparing(Attribute::getName));
        parent.keySet().forEach(key -> sorted.put(key, parent.getAttribute(key)));
        child.keySet().forEach(key -> sorted.put(key, child.getAttribute(key)));
        return sorted.toString();
    }
}
