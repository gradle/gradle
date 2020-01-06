/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.metadata;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeMergingException;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.snapshot.impl.CoercingStringValueSnapshot;

import java.util.Map;

public class DefaultMavenImmutableAttributesFactory implements MavenImmutableAttributesFactory {
    private final ImmutableAttributesFactory delegate;
    private final NamedObjectInstantiator objectInstantiator;
    private final Map<ComponentTypeEntry, ImmutableAttributes> concatCache = Maps.newConcurrentMap();

    public DefaultMavenImmutableAttributesFactory(ImmutableAttributesFactory delegate, NamedObjectInstantiator objectInstantiator) {
        this.delegate = delegate;
        this.objectInstantiator = objectInstantiator;
    }

    @Override
    public AttributeContainerInternal mutable() {
        return delegate.mutable();
    }

    @Override
    public AttributeContainerInternal mutable(AttributeContainerInternal parent) {
        return delegate.mutable(parent);
    }

    @Override
    public <T> ImmutableAttributes of(Attribute<T> key, T value) {
        return delegate.of(key, value);
    }

    @Override
    public <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, T value) {
        return delegate.concat(node, key, value);
    }

    @Override
    public <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, Isolatable<T> value) {
        return delegate.concat(node, key, value);
    }

    @Override
    public ImmutableAttributes concat(ImmutableAttributes attributes1, ImmutableAttributes attributes2) {
        return delegate.concat(attributes1, attributes2);
    }

    @Override
    public ImmutableAttributes safeConcat(ImmutableAttributes attributes1, ImmutableAttributes attributes2) throws AttributeMergingException {
        return delegate.safeConcat(attributes1, attributes2);
    }

    @Override
    public ImmutableAttributes libraryWithUsage(ImmutableAttributes original, String usage) {
        ComponentTypeEntry entry = new ComponentTypeEntry(original, Category.LIBRARY, usage);
        ImmutableAttributes result = concatCache.get(entry);
        if (result == null) {
            result = concat(original, USAGE_ATTRIBUTE, new CoercingStringValueSnapshot(usage, objectInstantiator));
            result = concat(result, FORMAT_ATTRIBUTE, new CoercingStringValueSnapshot(LibraryElements.JAR, objectInstantiator));
            result = concat(result, CATEGORY_ATTRIBUTE, new CoercingStringValueSnapshot(Category.LIBRARY, objectInstantiator));
            concatCache.put(entry, result);
        }
        return result;
    }

    @Override
    public ImmutableAttributes platformWithUsage(ImmutableAttributes original, String usage, boolean enforced) {
        String componentType = enforced ? Category.ENFORCED_PLATFORM : Category.REGULAR_PLATFORM;
        ComponentTypeEntry entry = new ComponentTypeEntry(original, componentType, usage);
        ImmutableAttributes result = concatCache.get(entry);
        if (result == null) {
            result = concat(original, USAGE_ATTRIBUTE, new CoercingStringValueSnapshot(usage, objectInstantiator));
            result = concat(result, CATEGORY_ATTRIBUTE, new CoercingStringValueSnapshot(componentType, objectInstantiator));
            concatCache.put(entry, result);
        }
        return result;
    }

    private static class ComponentTypeEntry {
        private final ImmutableAttributes source;
        private final String componentType;
        private final String usage;
        private final int hashCode;

        private ComponentTypeEntry(ImmutableAttributes source, String componentType, String usage) {
            this.source = source;
            this.componentType = componentType;
            this.usage = usage;
            this.hashCode = Objects.hashCode(source, componentType, usage);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ComponentTypeEntry that = (ComponentTypeEntry) o;
            return hashCode == that.hashCode &&
                Objects.equal(source, that.source) &&
                Objects.equal(componentType, that.componentType) &&
                Objects.equal(usage, that.usage);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
