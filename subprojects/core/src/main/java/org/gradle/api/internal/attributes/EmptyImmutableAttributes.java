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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * An implementation of {@link ImmutableAttributes} that contains no entries.
 */
public final class EmptyImmutableAttributes extends AbstractAttributeContainer implements ImmutableAttributes {

    public static final EmptyImmutableAttributes INSTANCE = new EmptyImmutableAttributes();

    @Override
    public ImmutableAttributesEntry<?> getHead() {
        throw new IllegalStateException("Cannot get first entry since this container is empty.");
    }

    @Override
    public ImmutableCollection<ImmutableAttributesEntry<?>> getEntries() {
        return ImmutableSet.of();
    }

    @Override
    public <T> @Nullable ImmutableAttributesEntry<T> findEntry(Attribute<T> key) {
        return null;
    }

    @Override
    public @Nullable ImmutableAttributesEntry<?> findEntry(String name) {
        return null;
    }

    @Override
    public ImmutableSet<Attribute<?>> keySet() {
        return ImmutableSet.of();
    }

    @Override
    public <T> @Nullable T getAttribute(Attribute<T> key) {
        isValidAttributeRequest(key);
        return null;
    }

    @Override
    public ImmutableAttributes asImmutable() {
        return this;
    }

    @Override
    public Provider<Map<Attribute<?>, AttributeEntry<?>>> getEntriesProvider() {
        return Providers.of(ImmutableMap.of());
    }

    @Override
    public String toString() {
        return "{}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof EmptyImmutableAttributes;
    }

    @Override
    public int hashCode() {
        return EmptyImmutableAttributes.class.hashCode();
    }

}
