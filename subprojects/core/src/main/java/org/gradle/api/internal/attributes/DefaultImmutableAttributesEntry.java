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

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.gradle.internal.Cast;
import org.gradle.internal.isolation.Isolatable;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link ImmutableAttributesEntry} which caches the result of coercing
 * to other attribute types. The caching is idempotent and thread-safe, so this class is effectively
 * immutable and can be shared by many {@link ImmutableAttributes} containers.
 */
public final class DefaultImmutableAttributesEntry<T> implements ImmutableAttributesEntry<T> {

    // Coercion is an expensive process, so we cache the result of coercing to other attribute types.
    // We can afford using a hashmap here because attributes are interned, and their lifetime doesn't
    // exceed a build
    private final Map<Attribute<?>, Object> coercionCache = new ConcurrentHashMap<>();

    private final Attribute<T> attribute;
    private final Isolatable<T> value;
    private final int hashCode;

    public DefaultImmutableAttributesEntry(Attribute<T> key, Isolatable<T> value) {
        this.attribute = key;
        this.value = value;
        this.hashCode = computeHashCode(key, value);
    }

    @Override
    public Attribute<T> getKey() {
        return attribute;
    }

    @Override
    public Isolatable<T> getValue() {
        return value;
    }

    @Override
    public <S> S coerce(Attribute<S> otherAttribute) {
        S s = Cast.uncheckedCast(coercionCache.get(otherAttribute));
        if (s == null) {
            s = uncachedCoerce(otherAttribute);
            coercionCache.put(otherAttribute, s);
        }
        return s;
    }

    private <S> S uncachedCoerce(Attribute<S> otherAttribute) {
        Class<S> otherAttributeType = otherAttribute.getType();
        // If attribute types are already compatible, go with it. There are two cases covered here:
        // 1) Both attributes are strongly typed and match, usually the case if both are sourced from the local build
        // 2) Both attributes are desugared, usually the case if both are sourced from published metadata
        if (otherAttributeType.isAssignableFrom(attribute.getType())) {
            return Cast.uncheckedCast(getIsolatedValue());
        }

        // Attempt to coerce myself into the other attribute's type
        // - I am desugared and the other attribute is strongly typed, usually the case if I am sourced from published metadata and the other from the local build
        S converted = value.coerce(otherAttributeType);
        if (converted != null) {
            return converted;
        } else if (otherAttributeType.isAssignableFrom(String.class)) {
            // Attempt to desugar myself
            // - I am strongly typed and the other is desugared, usually the case if I am sourced from the local build and the other is sourced from published metadata
            converted = Cast.uncheckedCast(desugar());
            if (converted != null) {
                return converted;
            }
        }
        String foundType = getIsolatedValue().getClass().getName();
        if (foundType.equals(otherAttributeType.getName())) {
            foundType += " with a different ClassLoader";
        }
        throw new IllegalArgumentException(String.format("Unexpected type for attribute '%s' provided. Expected a value of type %s but found a value of type %s.", attribute.getName(), otherAttributeType.getName(), foundType));
    }

    private @Nullable String desugar() {
        // We support desugaring for all non-primitive types supported in GradleModuleMetadataWriter.writeAttributes(), which are:
        // - Named
        // - Enum
        if (Named.class.isAssignableFrom(attribute.getType())) {
            return ((Named) getIsolatedValue()).getName();
        }
        if (Enum.class.isAssignableFrom(attribute.getType())) {
            return ((Enum<?>) getIsolatedValue()).name();
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultImmutableAttributesEntry<?> that = (DefaultImmutableAttributesEntry<?>) o;
        return attribute.equals(that.attribute) && value.equals(that.value);
    }

    private static <T> int computeHashCode(Attribute<T> attribute, Isolatable<T> value) {
        int result = attribute.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return attribute + "=" + getIsolatedValue();
    }

}
