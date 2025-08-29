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
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.isolation.Isolatable;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * The default implementation of {@link ImmutableAttributes}.
 * <p>
 * Most methods are strict in that they will only return an entry if its
 * key matches the requested key's name and type. However, {@link #getAttribute(Attribute)}
 * attempts to coerce entries with a matching name to the requested type.
 */
public final class DefaultImmutableAttributesContainer extends AbstractAttributeContainer implements ImmutableAttributes {

    private final ImmutableAttributesEntry<?> head;
    private final ImmutableMap<Attribute<?>, ImmutableAttributesEntry<?>> hierarchy;
    private final ImmutableMap<String, ImmutableAttributesEntry<?>> hierarchyByName;
    private final int hashCode;

    // Optimize for the single entry case, makes findEntry faster
    private final @Nullable String singleEntryName;
    private final @Nullable ImmutableAttributesEntry<?> singleEntry;

    <T> DefaultImmutableAttributesContainer(ImmutableAttributes parent, ImmutableAttributesEntry<T> head) {
        this.head = head;

        ImmutableMap.Builder<Attribute<?>, ImmutableAttributesEntry<?>> hierarchyBuilder =  ImmutableMap.builderWithExpectedSize(parent.getEntries().size() + 1);
        ImmutableMap.Builder<String, ImmutableAttributesEntry<?>> hierarchyByNameBuilder = ImmutableMap.builderWithExpectedSize(parent.getEntries().size() + 1);
        for (ImmutableAttributesEntry<?> entry : parent.getEntries()) {
            hierarchyBuilder.put(entry.getKey(), entry);
            hierarchyByNameBuilder.put(entry.getKey().getName(), entry);
        }
        hierarchyBuilder.put(head.getKey(), head);
        hierarchyByNameBuilder.put(head.getKey().getName(), head);

        this.hierarchy = hierarchyBuilder.buildKeepingLast();
        this.hierarchyByName = hierarchyByNameBuilder.buildKeepingLast();

        // Computing the hash code based on the parent hash code and the new entry is faster
        // than computing it from the hierarchy map.
        this.hashCode = computeHashCode(parent, head);

        if (hierarchyByName.size() == 1) {
            singleEntry = hierarchyByName.values().iterator().next();
            singleEntryName = singleEntry.getKey().getName();
        } else {
            singleEntryName = null;
            singleEntry = null;
        }
    }

    private static <T> int computeHashCode(ImmutableAttributes parent, ImmutableAttributesEntry<T> first) {
        int hashCode = parent.hashCode();
        hashCode = 31 * hashCode + first.hashCode();
        return hashCode;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultImmutableAttributesContainer that = (DefaultImmutableAttributesContainer) o;

        if (hierarchy.size() != that.hierarchy.size()) {
            return false;
        }

        for (ImmutableAttributesEntry<?> entry : getEntries()) {
            ImmutableAttributesEntry<?> otherEntry = that.findEntry(entry.getKey());
            if (otherEntry == null || !entry.getIsolatedValue().equals(otherEntry.getIsolatedValue())) {
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
    public ImmutableAttributesEntry<?> getHead() {
        return head;
    }

    @Override
    public ImmutableCollection<ImmutableAttributesEntry<?>> getEntries() {
        return hierarchy.values();
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

        ImmutableAttributesEntry<?> entry = findEntry(key.getName());
        if (entry == null) {
            return null;
        }

        Isolatable<?> isolatable = entry.getValue();
        Object value = isolatable.isolate();

        // TODO: Reuse the AttributeValue#coerce implementation here so we can reuse its coercion cache
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
    public <K> @Nullable ImmutableAttributesEntry<K> findEntry(Attribute<K> key) {
        ImmutableAttributesEntry<?> entry = hierarchy.get(key);
        if (entry == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        ImmutableAttributesEntry<K> typedEntry = (ImmutableAttributesEntry<K>) entry;
        return typedEntry;
    }

    @Override
    public @Nullable ImmutableAttributesEntry<?> findEntry(String name) {
        //noinspection StringEquality
        if (singleEntryName == name) {
            // The identity check is intentional here, do not replace with .equals()
            return singleEntry;
        }

        return hierarchyByName.get(name);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean contains(Attribute<?> key) {
        return getAttribute(key) != null;
    }

    @Override
    public ImmutableAttributes asImmutable() {
        return this;
    }

    @Override
    public Map<Attribute<?>, ?> asMap() {
        ImmutableMap.Builder<Attribute<?>, Object> builder = ImmutableMap.builder();
        for (ImmutableAttributesEntry<?> entry : getEntries()) {
            builder.put(entry.getKey(), entry.getIsolatedValue());
        }
        return builder.build();
    }

    @Override
    public Provider<Map<Attribute<?>, AttributeEntry<?>>> getEntriesProvider() {
        ImmutableMap.Builder<Attribute<?>, AttributeEntry<?>> builder = ImmutableMap.builder();
        for (ImmutableAttributesEntry<?> entry : getEntries()) {
            builder.put(entry.getKey(), asEntry(entry));
        }
        return Providers.of(builder.build());
    }

    private static <E> AttributeEntry<E> asEntry(ImmutableAttributesEntry<E> entry) {
        return new AttributeEntry<>(entry.getKey(), entry.getValue());
    }

    @Override
    public String toString() {
        Map<Attribute<?>, Object> sorted = new TreeMap<>(Comparator.comparing(Attribute::getName));
        for (ImmutableAttributesEntry<?> entry : getEntries()) {
            sorted.put(entry.getKey(), entry.getIsolatedValue());
        }
        return sorted.toString();
    }

}
