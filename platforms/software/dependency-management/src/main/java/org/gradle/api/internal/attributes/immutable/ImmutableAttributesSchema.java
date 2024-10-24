/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.attributes.immutable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.MultipleCandidatesDetails;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * An immutable schema for describing the behavior of {@link Attribute attributes}.
 * <p>
 * Instances are created via a {@link ImmutableAttributesSchemaFactory}.
 *
 * @see org.gradle.api.attributes.AttributesSchema
 */
public class ImmutableAttributesSchema {

    /**
     * An attribute schema that describes no attributes.
     */
    public static final ImmutableAttributesSchema EMPTY = new ImmutableAttributesSchema(
        ImmutableMap.of(),
        ImmutableList.of()
    );

    // package-private to allow access from ImmutableAttributesSchemaFactory
    final ImmutableMap<Attribute<?>, ImmutableAttributeMatchingStrategy<?>> strategies;
    final ImmutableList<Attribute<?>> precedence;

    // Computed values
    private final ImmutableMap<String, Attribute<?>> attributesByName;
    private final int hashCode;

    public ImmutableAttributesSchema(
        ImmutableMap<Attribute<?>, ImmutableAttributeMatchingStrategy<?>> strategies,
        ImmutableList<Attribute<?>> precedence
    ) {
        this.strategies = strategies;
        this.precedence = precedence;

        this.attributesByName = computeAttributesByName(strategies);
        this.hashCode = computeHashCode(strategies, precedence);
    }

    private static ImmutableMap<String, Attribute<?>> computeAttributesByName(ImmutableMap<Attribute<?>, ?> strategies) {
        ImmutableMap.Builder<String, Attribute<?>> attributesByName = ImmutableMap.builder();
        for (Attribute<?> attribute : strategies.keySet()) {
            attributesByName.put(attribute.getName(), attribute);
        }

        // TODO: In some cases, two attributes may be registered with the same name.
        // This is something we should probably forbid upstream.
        return attributesByName.buildKeepingLast();
    }

    private static int computeHashCode(
        ImmutableMap<Attribute<?>, ImmutableAttributeMatchingStrategy<?>> strategies,
        ImmutableList<Attribute<?>> precedence
    ) {
        int result = strategies.hashCode();
        result = 31 * result + precedence.hashCode();
        return result;
    }

    /**
     * Get the attributes described by this schema.
     */
    public ImmutableSet<Attribute<?>> getAttributes() {
        return strategies.keySet();
    }

    /**
     * Get the disambiguation rule for the given attribute.
     */
    public <T> ImmutableList<Action<? super MultipleCandidatesDetails<T>>> disambiguationRules(Attribute<T> attribute) {
        ImmutableAttributeMatchingStrategy<T> matchingStrategy = getStrategy(attribute);
        if (matchingStrategy != null) {
            return matchingStrategy.getDisambiguationRules();
        }
        return ImmutableList.of();
    }

    /**
     * Get the compatibility rule for the given attribute.
     */
    public <T> ImmutableList<Action<? super CompatibilityCheckDetails<T>>> compatibilityRules(Attribute<T> attribute) {
        ImmutableAttributeMatchingStrategy<T> matchingStrategy = getStrategy(attribute);
        if (matchingStrategy != null) {
            return matchingStrategy.getCompatibilityRules();
        }
        return ImmutableList.of();
    }

    /**
     * Get an attribute by name.
     */
    @Nullable
    public Attribute<?> getAttributeByName(String name) {
        return attributesByName.get(name);
    }

    /**
     * Get the precedence of attributes in this schema.
     */
    public ImmutableList<Attribute<?>> getAttributeDisambiguationPrecedence() {
        return precedence;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> ImmutableAttributeMatchingStrategy<T> getStrategy(Attribute<T> attribute) {
        return (ImmutableAttributeMatchingStrategy<T>) strategies.get(attribute);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ImmutableAttributesSchema that = (ImmutableAttributesSchema) o;
        return strategies.equals(that.strategies) &&
            precedence.equals(that.precedence);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public static class ImmutableAttributeMatchingStrategy<T> {
        final ImmutableList<Action<? super CompatibilityCheckDetails<T>>> compatibilityRules;
        final ImmutableList<Action<? super MultipleCandidatesDetails<T>>> disambiguationRules;

        public ImmutableAttributeMatchingStrategy(
            ImmutableList<Action<? super CompatibilityCheckDetails<T>>> compatibilityRules,
            ImmutableList<Action<? super MultipleCandidatesDetails<T>>> disambiguationRules
        ) {
            this.compatibilityRules = compatibilityRules;
            this.disambiguationRules = disambiguationRules;
        }

        public ImmutableList<Action<? super CompatibilityCheckDetails<T>>> getCompatibilityRules() {
            return compatibilityRules;
        }

        public ImmutableList<Action<? super MultipleCandidatesDetails<T>>> getDisambiguationRules() {
            return disambiguationRules;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ImmutableAttributeMatchingStrategy<?> that = (ImmutableAttributeMatchingStrategy<?>) o;
            //noinspection EqualsBetweenInconvertibleTypes
            return compatibilityRules.equals(that.compatibilityRules) &&
                disambiguationRules.equals(that.disambiguationRules);
        }

        @Override
        public int hashCode() {
            return Objects.hash(compatibilityRules, disambiguationRules);
        }
    }

}
