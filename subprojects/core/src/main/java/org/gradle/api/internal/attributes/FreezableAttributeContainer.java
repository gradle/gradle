/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Describable;
import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * An attribute container which can be frozen in order to avoid subsequent mutations.
 */
public final class FreezableAttributeContainer extends AbstractAttributeContainer {

    private final Property<AttributeContainerInternal> delegate;
    private final Describable owner;

    public FreezableAttributeContainer(
        AttributeContainerInternal delegate,
        Describable owner,
        PropertyFactory propertyFactory
    ) {
        this.delegate = propertyFactory.property(AttributeContainerInternal.class).value(delegate);
        this.owner = owner;
    }

    /**
     * Prevent further mutations to this attribute container.
     */
    public void freeze() {
        this.delegate.set(delegate.get().asImmutable());
    }

    @Override
    public String toString() {
        return delegate.get().toString();
    }

    @Override
    public ImmutableAttributes asImmutable() {
        return delegate.get().asImmutable();
    }

    @Override
    public Map<Attribute<?>, ?> asMap() {
        return delegate.get().asMap();
    }

    @Override
    public Set<Attribute<?>> keySet() {
        return delegate.get().keySet();
    }

    @Override
    public <T> AttributeContainer attribute(Attribute<T> key, T value) {
        assertMutable();
        delegate.get().attribute(key, value);
        return this;
    }

    @Override
    public <T> AttributeContainer attributeProvider(Attribute<T> key, Provider<? extends T> provider) {
        assertMutable();
        delegate.get().attributeProvider(key, provider);
        return this;
    }

    @Override
    public AttributeContainer addAllLater(AttributeContainer other) {
        assertMutable();
        delegate.get().addAllLater(other);
        return this;
    }

    @Nullable
    @Override
    public <T> T getAttribute(Attribute<T> key) {
        if (!isValidAttributeRequest(key)) {
            return null;
        }
        return delegate.get().getAttribute(key);
    }

    @Override
    public Provider<Map<Attribute<?>, AttributeEntry<?>>> getEntriesProvider() {
        return delegate.flatMap(AttributeContainerInternal::getEntriesProvider);
    }

    @Override
    public boolean isEmpty() {
        return delegate.get().isEmpty();
    }

    @Override
    public boolean contains(Attribute<?> key) {
        return delegate.get().contains(key);
    }

    private void assertMutable() {
        if (delegate.get() instanceof ImmutableAttributes) {
            throw new IllegalStateException(String.format("Cannot change attributes of %s after it has been locked for mutation", owner.getDisplayName()));
        }
    }

    @Override
    public <T extends Named> T named(Class<T> type, String name) {
        return delegate.get().named(type, name);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FreezableAttributeContainer that = (FreezableAttributeContainer) o;
        return owner.equals(that.owner) && delegate.get().equals(that.delegate.get());
    }

    @Override
    public int hashCode() {
        int result = owner.hashCode();
        result = 31 * result + delegate.get().hashCode();
        return result;
    }
}
