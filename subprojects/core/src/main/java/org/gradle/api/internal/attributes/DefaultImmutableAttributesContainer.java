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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.isolation.Isolatable;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Note: The fact that this type is both a <em>container</em> and a <em>value stored in a container</em> is strange.  This is
 * probably something that ought to be addressed, this shouldn't implement {@link AttributeValue}, but it does it in order to
 * build "linked lists" of immutable containers through concatenation to save memory.
 */
public final class DefaultImmutableAttributesContainer extends AbstractAttributeContainer implements ImmutableAttributes, AttributeValue<Object> {
    private static final Comparator<Attribute<?>> ATTRIBUTE_NAME_COMPARATOR = Comparator.comparing(Attribute::getName);
    // Coercion is an expensive process, so we cache the result of coercing to other attribute types.
    // We can afford using a hashmap here because attributes are interned, and their lifetime doesn't
    // exceed a build
    private final Map<Attribute<?>, Object> coercionCache = new ConcurrentHashMap<>();

    final Attribute<?> attribute;
    final Isolatable<?> value;
    private final ImmutableMap<Attribute<?>, DefaultImmutableAttributesContainer> hierarchy;
    private final ImmutableMap<String, DefaultImmutableAttributesContainer> hierarchyByName;
    private final int hashCode;

    // Optimize for the single entry case, makes findEntry faster
    private final String singleEntryName;
    private final DefaultImmutableAttributesContainer singleEntryValue;

    DefaultImmutableAttributesContainer() {
        this.attribute = null;
        this.value = null;
        this.hashCode = 0;
        this.hierarchy = ImmutableMap.of();
        this.hierarchyByName = ImmutableMap.of();
        this.singleEntryName = null;
        this.singleEntryValue = null;
    }

    DefaultImmutableAttributesContainer(DefaultImmutableAttributesContainer parent, Attribute<?> key, Isolatable<?> value) {
        this.attribute = key;
        this.value = value;
        Map<Attribute<?>, DefaultImmutableAttributesContainer> hierarchy = new LinkedHashMap<>(parent.hierarchy);
        hierarchy.put(attribute, this);
        this.hierarchy = ImmutableMap.copyOf(hierarchy);
        Map<String, DefaultImmutableAttributesContainer> hierarchyByName = new LinkedHashMap<>(parent.hierarchyByName);
        hierarchyByName.put(attribute.getName(), this);
        this.hierarchyByName = ImmutableMap.copyOf(hierarchyByName);
        int hashCode = parent.hashCode();
        hashCode = 31 * hashCode + attribute.hashCode();
        hashCode = 31 * hashCode + value.hashCode();
        this.hashCode = hashCode;
        if (hierarchyByName.size() == 1) {
            Map.Entry<String, DefaultImmutableAttributesContainer> entry = hierarchyByName.entrySet().iterator().next();
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

        DefaultImmutableAttributesContainer that = (DefaultImmutableAttributesContainer) o;

        if (hierarchy.size() != that.hierarchy.size()) {
            return false;
        }

        for (Map.Entry<Attribute<?>, DefaultImmutableAttributesContainer> entry : hierarchy.entrySet()) {
            if (!Objects.requireNonNull(entry.getValue().value.isolate()).equals(that.getAttribute(entry.getKey()))) {
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
    public <T> AttributeContainer attribute(Attribute<T> key, T value) {
        throw new UnsupportedOperationException("Mutation of attributes is not allowed");
    }

    @Override
    public ImmutableSet<Attribute<?>> keySet() {
        return hierarchy.keySet();
    }

    @Override
    public <T> AttributeContainer attributeProvider(Attribute<T> key, Provider<? extends T> provider) {
        throw new UnsupportedOperationException("Mutation of attributes is not allowed");
    }

    @Override
    @Nullable
    public <T> T getAttribute(Attribute<T> key) {
        if (!isValidAttributeRequest(key)) {
            return null;
        }

        Isolatable<T> isolatable = getIsolatableAttribute(key);
        if (isolatable == null) {
            return null;
        }
        Object value = isolatable.isolate();

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
    private boolean isAppropriateType(@Nullable Object value, Attribute<?> key) {
        return value != null && key.getType().isAssignableFrom(value.getClass());
    }

    /**
     * We lookup by name here to avoid issues with attributes with the type class from different classloaders.  This
     * should only happen with immutable attribute containers, as mutable containers will not cross classloader boundaries
     * when included builds are used, and this is the typical failure case.
     * <p>
     * This lookup requires that only a single attribute with a given name (regardless of type) is present in this container.  This invariant
     * must be enforced by the factory construction logic for immutable containers such as
     * {@link DefaultAttributesFactory#assertAttributeNotAlreadyPresent(AttributeContainer, Attribute)}.
     */
    @Nullable
    /* package */ <T> Isolatable<T> getIsolatableAttribute(Attribute<T> key) {
        DefaultImmutableAttributesContainer attributes = hierarchyByName.get(key.getName());
        return Cast.uncheckedCast(attributes == null ? null : attributes.value);
    }

    @Override
    public <T> AttributeValue<T> findEntry(Attribute<T> key) {
        DefaultImmutableAttributesContainer attributes = hierarchy.get(key);
        return Cast.uncheckedNonnullCast(attributes == null ? MISSING : attributes);
    }

    @Override
    public AttributeValue<?> findEntry(String name) {
        //noinspection StringEquality
        if (singleEntryName == name) {
            // The identity check is intentional here, do not replace with .equals()
            return singleEntryValue;
        }
        DefaultImmutableAttributesContainer attributes = hierarchyByName.get(name);
        return attributes == null ? MISSING : attributes;
    }

    @Override
    @Nullable
    public Attribute<?> findAttribute(String name) {
        DefaultImmutableAttributesContainer attributes = hierarchyByName.get(name);
        return attributes == null ? null : attributes.attribute;
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public Object get() {
        Preconditions.checkState(value != null, "When calling get() value should never be null");
        return value.isolate();
    }

    @Nullable
    private String desugar() {
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

    @Nullable
    private <S> S coerce(Class<S> type) {
        if (value != null) {
            return value.coerce(type);
        }
        return null;
    }

    @Override
    public <S> S coerce(Attribute<S> otherAttribute) {
        Preconditions.checkState(value != null, "When coercing, value should never be null");
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
        S converted = coerce(otherAttributeType);
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
    public boolean isPresent() {
        return attribute != null;
    }

    @Override
    public boolean isEmpty() {
        return attribute == null;
    }

    @Override
    public boolean contains(Attribute<?> key) {
        return hierarchy.containsKey(key);
    }

    @Override
    public ImmutableAttributes asImmutable() {
        return this;
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public Map<Attribute<?>, ?> asMap() {
        ImmutableMap.Builder<Attribute<?>, ?> builder = ImmutableMap.builder();
        for (Attribute<?> attribute : keySet()) {
            builder.put(attribute, Cast.uncheckedCast(getAttribute(attribute)));
        }
        return builder.build();
    }

    @Override
    public String toString() {
        Map<Attribute<?>, Object> sorted = new TreeMap<>(ATTRIBUTE_NAME_COMPARATOR);
        for (Map.Entry<Attribute<?>, DefaultImmutableAttributesContainer> entry : hierarchy.entrySet()) {
            sorted.put(entry.getKey(), entry.getValue().value.isolate());
        }
        return sorted.toString();
    }
}
