/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.base.Objects;
import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder;
import org.gradle.internal.component.model.AttributeSelectionSchema;
import org.gradle.internal.component.model.AttributeSelectionUtils;
import org.gradle.internal.component.model.ComponentAttributeMatcher;
import org.gradle.internal.component.model.DefaultCompatibilityCheckResult;
import org.gradle.internal.component.model.DefaultMultipleCandidateResult;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DefaultAttributesSchema implements AttributesSchemaInternal, AttributesSchema {
    private final ComponentAttributeMatcher componentAttributeMatcher;
    private final InstantiatorFactory instantiatorFactory;
    private final Map<Attribute<?>, AttributeMatchingStrategy<?>> strategies = new HashMap<>();
    private final Map<String, Attribute<?>> attributesByName = new HashMap<>();

    private final DefaultAttributeMatcher matcher;
    private final IsolatableFactory isolatableFactory;
    private final Map<ExtraAttributesEntry, Attribute<?>[]> extraAttributesCache = new HashMap<>();
    private final List<AttributeDescriber> consumerAttributeDescribers = new ArrayList<>();
    private final Set<Attribute<?>> precedence = new LinkedHashSet<>();

    public DefaultAttributesSchema(ComponentAttributeMatcher componentAttributeMatcher, InstantiatorFactory instantiatorFactory, IsolatableFactory isolatableFactory) {
        this.componentAttributeMatcher = componentAttributeMatcher;
        this.instantiatorFactory = instantiatorFactory;
        this.matcher = new DefaultAttributeMatcher(componentAttributeMatcher, mergeWith(EmptySchema.INSTANCE));
        this.isolatableFactory = isolatableFactory;
    }

    @Override
    public <T> AttributeMatchingStrategy<T> getMatchingStrategy(Attribute<T> attribute) {
        AttributeMatchingStrategy<?> strategy = strategies.get(attribute);
        if (strategy == null) {
            throw new IllegalArgumentException("Unable to find matching strategy for " + attribute);
        }
        return Cast.uncheckedCast(strategy);
    }

    @Override
    public <T> AttributeMatchingStrategy<T> attribute(Attribute<T> attribute) {
        return attribute(attribute, null);
    }

    @Override
    public <T> AttributeMatchingStrategy<T> attribute(Attribute<T> attribute, Action<? super AttributeMatchingStrategy<T>> configureAction) {
        AttributeMatchingStrategy<T> strategy = Cast.uncheckedCast(strategies.get(attribute));
        if (strategy == null) {
            strategy = Cast.uncheckedCast(instantiatorFactory.decorateLenient().newInstance(DefaultAttributeMatchingStrategy.class, instantiatorFactory, isolatableFactory));
            strategies.put(attribute, strategy);
            attributesByName.put(attribute.getName(), attribute);
        }
        if (configureAction != null) {
            configureAction.execute(strategy);
        }
        return strategy;
    }

    @Override
    public Set<Attribute<?>> getAttributes() {
        return strategies.keySet();
    }

    @Override
    public boolean hasAttribute(Attribute<?> key) {
        return strategies.containsKey(key);
    }

    AttributeSelectionSchema mergeWith(AttributesSchemaInternal producerSchema) {
        return new MergedSchema(producerSchema);
    }

    @Override
    public AttributeMatcher withProducer(AttributesSchemaInternal producerSchema) {
        return new DefaultAttributeMatcher(componentAttributeMatcher, mergeWith(producerSchema));
    }

    @Override
    public AttributeMatcher matcher() {
        return matcher;
    }

    @Override
    public CompatibilityRule<Object> compatibilityRules(Attribute<?> attribute) {
        AttributeMatchingStrategy<?> matchingStrategy = strategies.get(attribute);
        if (matchingStrategy != null) {
            return Cast.uncheckedCast(matchingStrategy.getCompatibilityRules());
        }
        return EmptySchema.INSTANCE.compatibilityRules(attribute);
    }

    @Override
    public DisambiguationRule<Object> disambiguationRules(Attribute<?> attribute) {
        AttributeMatchingStrategy<?> matchingStrategy = strategies.get(attribute);
        if (matchingStrategy != null) {
            return Cast.uncheckedCast(matchingStrategy.getDisambiguationRules());
        }
        return EmptySchema.INSTANCE.disambiguationRules(attribute);
    }

    @Override
    public List<AttributeDescriber> getConsumerDescribers() {
        return consumerAttributeDescribers;
    }

    @Override
    public void addConsumerDescriber(AttributeDescriber describer) {
        consumerAttributeDescribers.add(describer);
    }

    @Override
    public void attributeDisambiguationPrecedence(Attribute<?>... attributes) {
        for (Attribute<?> attribute : attributes) {
            if (!precedence.add(attribute)) {
                throw new IllegalArgumentException(String.format("Attribute '%s' precedence has already been set.", attribute.getName()));
            }
        }
    }

    @Override
    public void setAttributeDisambiguationPrecedence(Collection<Attribute<?>> attributes) {
        precedence.clear();
        attributeDisambiguationPrecedence(attributes.toArray(new Attribute<?>[0]));
    }

    @Override
    public Collection<Attribute<?>> getAttributeDisambiguationPrecedence() {
        return Collections.unmodifiableCollection(precedence);
    }

    private static class DefaultAttributeMatcher implements AttributeMatcher {
        private final ComponentAttributeMatcher componentAttributeMatcher;
        private final AttributeSelectionSchema effectiveSchema;

        DefaultAttributeMatcher(ComponentAttributeMatcher componentAttributeMatcher, AttributeSelectionSchema effectiveSchema) {
            this.componentAttributeMatcher = componentAttributeMatcher;
            this.effectiveSchema = effectiveSchema;
        }

        @Override
        public boolean isMatching(AttributeContainerInternal candidate, AttributeContainerInternal requested) {
            return componentAttributeMatcher.isMatching(effectiveSchema, candidate, requested);
        }

        @Override
        public <T> boolean isMatching(Attribute<T> attribute, T candidate, T requested) {
            return effectiveSchema.matchValue(attribute, requested, candidate);
        }

        @Override
        public <T extends HasAttributes> List<T> matches(Collection<? extends T> candidates, AttributeContainerInternal requested, AttributeMatchingExplanationBuilder explanationBuilder) {
            return matches(candidates, requested, null, explanationBuilder);
        }

        @Override
        public <T extends HasAttributes> List<T> matches(Collection<? extends T> candidates, AttributeContainerInternal requested, @Nullable T fallback, AttributeMatchingExplanationBuilder explanationBuilder) {
            return componentAttributeMatcher.match(effectiveSchema, candidates, requested, fallback, explanationBuilder);
        }

        @Override
        public List<MatchingDescription<?>> describeMatching(AttributeContainerInternal candidate, AttributeContainerInternal requested) {
            return componentAttributeMatcher.describeMatching(effectiveSchema, candidate, requested);
        }
    }

    private class MergedSchema implements AttributeSelectionSchema {
        private final AttributesSchemaInternal producerSchema;

        MergedSchema(AttributesSchemaInternal producerSchema) {
            this.producerSchema = producerSchema;
        }

        @Override
        public boolean hasAttribute(Attribute<?> attribute) {
            return DefaultAttributesSchema.this.getAttributes().contains(attribute) || producerSchema.getAttributes().contains(attribute);
        }

        @Override
        public Set<Object> disambiguate(Attribute<?> attribute, @Nullable Object requested, Set<Object> candidates) {
            DefaultMultipleCandidateResult<Object> result = null;

            DisambiguationRule<Object> rules = disambiguationRules(attribute);
            if (rules.doesSomething()) {
                result = new DefaultMultipleCandidateResult<>(requested, candidates);
                rules.execute(result);
                if (result.hasResult()) {
                    return result.getMatches();
                }
            }

            rules = producerSchema.disambiguationRules(attribute);
            if (rules.doesSomething()) {
                if (result == null) {
                    result = new DefaultMultipleCandidateResult<>(requested, candidates);
                }
                rules.execute(result);
                if (result.hasResult()) {
                    return result.getMatches();
                }
            }

            if (requested != null && candidates.contains(requested)) {
                return Collections.singleton(requested);
            }

            return candidates;
        }

        @Override
        public boolean matchValue(Attribute<?> attribute, Object requested, Object candidate) {
            if (requested.equals(candidate)) {
                return true;
            }

            CompatibilityCheckResult<Object> result = null;

            CompatibilityRule<Object> rules = compatibilityRules(attribute);
            if (rules.doesSomething()) {
                result = new DefaultCompatibilityCheckResult<>(requested, candidate);
                rules.execute(result);
                if (result.hasResult()) {
                    return result.isCompatible();
                }
            }

            rules = producerSchema.compatibilityRules(attribute);
            if (rules.doesSomething()) {
                if (result == null) {
                    result = new DefaultCompatibilityCheckResult<>(requested, candidate);
                }
                rules.execute(result);
                if (result.hasResult()) {
                    return result.isCompatible();
                }
            }

            return false;
        }

        @Override
        public Attribute<?> getAttribute(String name) {
            Attribute<?> attribute = attributesByName.get(name);
            if (attribute != null) {
                return attribute;
            }
            if (producerSchema instanceof DefaultAttributesSchema) {
                return ((DefaultAttributesSchema) producerSchema).attributesByName.get(name);
            }
            for (Attribute<?> producerAttribute : producerSchema.getAttributes()) {
                if (producerAttribute.getName().equals(name)) {
                    return producerAttribute;
                }
            }
            return null;
        }

        @Override
        public Attribute<?>[] collectExtraAttributes(ImmutableAttributes[] candidateAttributeSets, ImmutableAttributes requested) {
            // It's almost always the same attribute sets which are compared, so in order to avoid a lot of memory allocation
            // during computation of the intersection, we cache the result here.
            ExtraAttributesEntry entry = new ExtraAttributesEntry(candidateAttributeSets, requested);
            Attribute<?>[] attributes = extraAttributesCache.get(entry);
            if (attributes == null) {
                attributes = AttributeSelectionUtils.collectExtraAttributes(this, candidateAttributeSets, requested);
                extraAttributesCache.put(entry, attributes);
            }
            return attributes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MergedSchema that = (MergedSchema) o;
            return producerSchema.equals(that.producerSchema);
        }

        @Override
        public PrecedenceResult orderByPrecedence(ImmutableAttributes requested) {
            if (precedence.isEmpty() && producerSchema.getAttributeDisambiguationPrecedence().isEmpty()) {
                // if no attribute precedence has been set anywhere, we can just iterate in order
                return new PrecedenceResult(IntStream.range(0, requested.keySet().size()).boxed().collect(Collectors.toList()));
            } else {
                // Populate requested attribute -> position in requested attribute list
                final Map<String, Integer> remaining = new LinkedHashMap<>();
                int position = 0;
                for (Attribute<?> requestedAttribute : requested.keySet()) {
                    remaining.put(requestedAttribute.getName(), position++);
                }
                List<Integer> sorted = new ArrayList<>(remaining.size());

                // Add attribute index to sorted in the order of precedence by the consumer
                for (Attribute<?> preferredAttribute : precedence) {
                    if (requested.contains(preferredAttribute)) {
                        sorted.add(remaining.remove(preferredAttribute.getName()));
                    }
                }
                // Add attribute index to sorted in the order of precedence by the producer
                for (Attribute<?> preferredAttribute : producerSchema.getAttributeDisambiguationPrecedence()) {
                    if (remaining.containsKey(preferredAttribute.getName()) && requested.contains(preferredAttribute)) {
                        sorted.add(remaining.remove(preferredAttribute.getName()));
                    }
                }
                // If nothing was sorted, there were no attributes in the request that matched any attribute precedences
                if (sorted.isEmpty()) {
                    // Iterate in order
                    return new PrecedenceResult(remaining.values());
                } else {
                    // sorted now contains any requested attribute indices in the order they appear in
                    // the consumer and producer's attribute precedences
                    return new PrecedenceResult(sorted, remaining.values());
                }
            }
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(producerSchema);
        }
    }

    /**
     * A cache entry key, leveraging _identity_ as the key, because we do interning.
     * This is a performance optimization.
     */
    private static class ExtraAttributesEntry {
        private final ImmutableAttributes[] candidateAttributeSets;
        private final ImmutableAttributes requestedAttributes;
        private final int hashCode;

        private ExtraAttributesEntry(ImmutableAttributes[] candidateAttributeSets, ImmutableAttributes requestedAttributes) {
            this.candidateAttributeSets = candidateAttributeSets;
            this.requestedAttributes = requestedAttributes;
            int hash = Arrays.hashCode(candidateAttributeSets);
            hash = 31 * hash + requestedAttributes.hashCode();
            this.hashCode = hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ExtraAttributesEntry that = (ExtraAttributesEntry) o;
            if (requestedAttributes != that.requestedAttributes) {
                return false;
            }
            if (candidateAttributeSets.length != that.candidateAttributeSets.length) {
                return false;
            }
            for (int i = 0; i < candidateAttributeSets.length; i++) {
                if (candidateAttributeSets[i] != that.candidateAttributeSets[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

}
