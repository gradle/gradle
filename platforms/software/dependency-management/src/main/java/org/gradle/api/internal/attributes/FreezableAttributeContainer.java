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
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * An attribute container which can be frozen in order to avoid subsequent mutations.
 */
public class FreezableAttributeContainer implements AttributeContainerInternal {

    private final Describable owner;

    private AttributeContainerInternal delegate;

    public FreezableAttributeContainer(AttributeContainerInternal delegate, Describable owner) {
        this.delegate = delegate;
        this.owner = owner;
    }

    /**
     * Prevent further mutations to this attribute container.
     */
    public void freeze() {
        this.delegate = delegate.asImmutable();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public ImmutableAttributes asImmutable() {
        return delegate.asImmutable();
    }

    @Override
    public Map<Attribute<?>, ?> asMap() {
        return delegate.asMap();
    }

    @Override
    public Set<Attribute<?>> keySet() {
        return delegate.keySet();
    }

    @Override
    public <T> AttributeContainer attribute(Attribute<T> key, T value) {
        assertMutable();
        delegate.attribute(key, value);
        return this;
    }

    @Override
    public <T> AttributeContainer attributeProvider(Attribute<T> key, Provider<? extends T> provider) {
        assertMutable();
        delegate.attributeProvider(key, provider);
        return this;
    }

    @Nullable
    @Override
    public <T> T getAttribute(Attribute<T> key) {
        return delegate.getAttribute(key);
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(Attribute<?> key) {
        return delegate.contains(key);
    }

    @Override
    public AttributeContainer getAttributes() {
        return this;
    }

    private void assertMutable() {
        if (delegate instanceof ImmutableAttributes) {
            throw new IllegalStateException(String.format("Cannot change attributes of %s after it has been locked for mutation", owner.getDisplayName()));
        }
    }
}
