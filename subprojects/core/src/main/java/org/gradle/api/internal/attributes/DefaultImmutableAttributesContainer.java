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
import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.isolation.Isolatable;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Note: The fact that this type is both a <em>container</em> and a <em>value stored in a container</em> is strange.  This is
 * probably something that ought to be addressed, this shouldn't implement {@link AttributeValue}, but it does it in order to
 * build "linked lists" of immutable containers through concatenation to save memory.
 */
public final class DefaultImmutableAttributesContainer<T> extends AbstractAttributeContainer implements ImmutableAttributes, AttributeValue<T> {

    // Coercion is an expensive process, so we cache the result of coercing to other attribute types.
    // We can afford using a hashmap here because attributes are interned, and their lifetime doesn't
    // exceed a build
    private final Map<Attribute<?>, Object> coercionCache = new ConcurrentHashMap<>();

    private final Attribute<T> attribute;
    private final Isolatable<T> value;
    private final ImmutableMap<Attribute<?>, AttributeValue<?>> hierarchy;
    private final ImmutableMap<String, AttributeValue<?>> hierarchyByName;
    private final int hashCode;

    // Optimize for the single entry case, makes findEntry faster
    private final @Nullable String singleEntryName;
    private final @Nullable AttributeValue<?> singleEntryValue;

    DefaultImmutableAttributesContainer(ImmutableAttributes parent, Attribute<T> key, Isolatable<T> value) {
        this.attribute = key;
        this.value = value;

        this.hierarchy = ImmutableMap.<Attribute<?>, AttributeValue<?>>builderWithExpectedSize(parent.getEntriesByAttribute().size() + 1)
            .putAll(parent.getEntriesByAttribute())
            .put(attribute, this)
            .buildKeepingLast();

        this.hierarchyByName = ImmutableMap.<String, AttributeValue<?>>builderWithExpectedSize(parent.getEntriesByName().size() + 1)
            .putAll(parent.getEntriesByName())
            .put(attribute.getName(), this)
            .buildKeepingLast();

        int hashCode = parent.hashCode();
        hashCode = 31 * hashCode + attribute.hashCode();
        hashCode = 31 * hashCode + value.hashCode();
        this.hashCode = hashCode;

        if (hierarchyByName.size() == 1) {
            Map.Entry<String, AttributeValue<?>> entry = hierarchyByName.entrySet().iterator().next();
            singleEntryName = entry.getKey();
            singleEntryValue = entry.getValue();
        } else {
            singleEntryName = null;
            singleEntryValue = null;
        }
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultImmutableAttributesContainer<?> that = (DefaultImmutableAttributesContainer<?>) o;

        if (hierarchy.size() != that.hierarchy.size()) {
            return false;
        }

        for (Map.Entry<Attribute<?>, AttributeValue<?>> entry : hierarchy.entrySet()) {
            if (!Objects.requireNonNull(entry.getValue().get()).equals(that.getAttribute(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public AttributeValue<T> getFirst() {
        return this;
    }

    @Override
    public ImmutableCollection<AttributeValue<?>> getEntries() {
        return hierarchy.values();
    }

    @Override
    public ImmutableMap<Attribute<?>, AttributeValue<?>> getEntriesByAttribute() {
        return hierarchy;
    }

    @Override
    public ImmutableMap<String, AttributeValue<?>> getEntriesByName() {
        return hierarchyByName;
    }

    @Override
    public ImmutableSet<Attribute<?>> keySet() {
        return hierarchy.keySet();
    }

    @Override
    public <E> AttributeContainer attribute(Attribute<E> key, E value) {
        throw new UnsupportedOperationException("This container is immutable and cannot be mutated.");
    }

    @Override
    public <E> AttributeContainer attributeProvider(Attribute<E> key, Provider<? extends E> provider) {
        throw new UnsupportedOperationException("This container is immutable and cannot be mutated.");
    }

    @Override
    public AttributeContainer addAllLater(AttributeContainer other) {
        throw new UnsupportedOperationException("This container is immutable and cannot be mutated.");
    }

    @Override
    @Nullable
    public <K> K getAttribute(Attribute<K> key) {
        if (!isValidAttributeRequest(key)) {
            return null;
        }

        AttributeValue<?> entry = findEntry(key.getName());
        if (entry == null) {
            return null;
        }

        Isolatable<?> isolatable = entry.getIsolatable();
        Object value = isolatable.isolate();

        // TODO: Reuse the AttributeValue#coerce implementation here so we can reuse the coercion cache
        // Ensure that the resulting value is of the requested type after coercion, to satisfy the promise of the method signature
        if (!isAppropriateType(value, key)) {
            value = isolatable.coerce(key.getType());
        }

        return Cast.uncheckedCast(value);
    }

    /**
     * Determines if the value is of the appropriate type for the given key; meaning that
     * it can be assigned to it.
     * <p>This method is used to ensure potential results of a call to {@link #getAttribute(Attribute)} satisfy the signature of
     * that method, where when asked for a {@code <T>} we must return one.
     * <p>
     * One interesting potential complication is caused by different classloaders
     * loading the same type when a typed attribute is put into a container vs. when it is retrieved.
     * <p>
     * When a request for a key with type X1
     * is being made in a container holding an attribute with type X2, where X1 and X2 are
     * actually THE SAME CLASS loaded by 2 different classloaders.  In this case we want to both
     * 1) find a match and return the value and 2) coerce this returned attribute value, typed as X2,
     * to the requested X1 type.
     * <p>
     * Doing so avoids the confusing situation where a build author can request an attribute
     * from a resolved variant of an external dep and have a typed attribute request succeed (because the external attribute is
     * desugared internally); but then if the build author replaces the external dep with an included build
     * generating the same exact variants, the same typed attribute request fails, since the desugaring
     * now doesn't happen and the attribute is typed with the non-identical class with the same name loaded
     * from a different classloader.  This is seriously confusing and unintuitive to a build
     * author.  When we detect this case, we want to coerce the value of X2 to X1, instead of returning {@code null}.
     *
     * @param value the value to check
     * @param key the key containing the type to check against
     * @return {@code true} if so; {@code false} otherwise
     */
    private static boolean isAppropriateType(@Nullable Object value, Attribute<?> key) {
        return value != null && key.getType().isAssignableFrom(value.getClass());
    }

    @Override
    public <K> @Nullable AttributeValue<K> findEntry(Attribute<K> key) {
        AttributeValue<?> entry = hierarchy.get(key);
        if (entry == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        AttributeValue<K> typedEntry = (AttributeValue<K>) entry;
        return typedEntry;
    }

    @Override
    public @Nullable AttributeValue<?> findEntry(String name) {
        //noinspection StringEquality
        if (singleEntryName == name) {
            // The identity check is intentional here, do not replace with .equals()
            return singleEntryValue;
        }

        return hierarchyByName.get(name);
    }

    @Override
    public Isolatable<T> getIsolatable() {
        return value;
    }

    private @Nullable String desugar() {
        // We support desugaring for all non-primitive types supported in GradleModuleMetadataWriter.writeAttributes(), which are:
        // - Named
        // - Enum
        if (Named.class.isAssignableFrom(attribute.getType())) {
            return ((Named) get()).getName();
        }
        if (Enum.class.isAssignableFrom(attribute.getType())) {
            return ((Enum<?>) get()).name();
        }
        return null;
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
            return Cast.uncheckedCast(get());
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
        String foundType = get().getClass().getName();
        if (foundType.equals(otherAttributeType.getName())) {
            foundType += " with a different ClassLoader";
        }
        throw new IllegalArgumentException(String.format("Unexpected type for attribute '%s' provided. Expected a value of type %s but found a value of type %s.", attribute.getName(), otherAttributeType.getName(), foundType));
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Attribute<T> getAttribute() {
        return attribute;
    }

    @Override
    public boolean contains(Attribute<?> key) {
        return hierarchy.containsKey(key);
    }

    @Override
    public ImmutableAttributes asImmutable() {
        return this;
    }

    @Override
    public Map<Attribute<?>, ?> asMap() {
        ImmutableMap.Builder<Attribute<?>, Object> builder = ImmutableMap.builder();
        for (AttributeValue<?> entry : hierarchy.values()) {
            builder.put(entry.getAttribute(), entry.get());
        }
        return builder.build();
    }

    @Override
    public Provider<Map<Attribute<?>, AttributeEntry<?>>> getEntryProvider() {
        ImmutableMap.Builder<Attribute<?>, AttributeEntry<?>> builder = ImmutableMap.builder();
        for (AttributeValue<?> entry : hierarchy.values()) {
            builder.put(entry.getAttribute(), asEntry(entry));
        }
        return Providers.of(builder.build());
    }

    private static <E> AttributeEntry<E> asEntry(AttributeValue<E> entry) {
        return new AttributeEntry<>(entry.getAttribute(), entry.getIsolatable());
    }

    @Override
    public String toString() {
        Map<Attribute<?>, Object> sorted = new TreeMap<>(Comparator.comparing(Attribute::getName));
        for (AttributeValue<?> entry : hierarchy.values()) {
            sorted.put(entry.getAttribute(), entry.get());
        }
        return sorted.toString();
    }

}
