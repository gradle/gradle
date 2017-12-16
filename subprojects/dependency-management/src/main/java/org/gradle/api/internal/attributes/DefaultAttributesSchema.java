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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.InstantiatorFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.AttributeSelectionSchema;
import org.gradle.internal.component.model.ComponentAttributeMatcher;
import org.gradle.internal.component.model.DefaultCompatibilityCheckResult;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultAttributesSchema implements AttributesSchemaInternal, AttributesSchema {
    private final ComponentAttributeMatcher componentAttributeMatcher;
    private final InstantiatorFactory instantiatorFactory;
    /**
     * TODO we currently keep the attributes in declaration order, so that matching error messages
     * are always the same, no matter which machine the build is run on. We might want to reconsider
     * this, as it adds some additional cost for very little benefit.
     */
    private final Map<Attribute<?>, AttributeMatchingStrategy<?>> strategies = Maps.newLinkedHashMap();
    private final DefaultAttributeMatcher matcher;

    public DefaultAttributesSchema(ComponentAttributeMatcher componentAttributeMatcher, InstantiatorFactory instantiatorFactory) {
        this.componentAttributeMatcher = componentAttributeMatcher;
        this.instantiatorFactory = instantiatorFactory;
        matcher = new DefaultAttributeMatcher(componentAttributeMatcher, mergeWith(EmptySchema.INSTANCE));
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
            strategy = Cast.uncheckedCast(instantiatorFactory.decorate().newInstance(DefaultAttributeMatchingStrategy.class, instantiatorFactory));
            strategies.put(attribute, strategy);
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
            DefaultCompatibilityCheckResult<Object> result = new DefaultCompatibilityCheckResult<Object>(requested, candidate);
            effectiveSchema.matchValue(attribute, result);
            return result.isCompatible();
        }

        @Override
        public <T extends HasAttributes> List<T> matches(Collection<? extends T> candidates, AttributeContainerInternal requested) {
            return matches(candidates, requested, null);
        }

        @Override
        public <T extends HasAttributes> List<T> matches(Collection<? extends T> candidates, AttributeContainerInternal requested, @Nullable T fallback) {
            return componentAttributeMatcher.match(effectiveSchema, candidates, requested, fallback);
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
        public void disambiguate(Attribute<?> attribute, MultipleCandidatesResult<Object> result) {
            DisambiguationRule<Object> rules = disambiguationRules(attribute);
            rules.execute(result);
            if (result.hasResult()) {
                return;
            }

            rules = producerSchema.disambiguationRules(attribute);
            rules.execute(result);
            if (result.hasResult()) {
                return;
            }

            Object requested = result.getConsumerValue();
            if (requested != null && result.getCandidateValues().contains(requested)) {
                result.closestMatch(requested);
                return;
            }

            // Select all candidates
            for (Object candidate : result.getCandidateValues()) {
                result.closestMatch(candidate);
            }
        }

        @Override
        public void matchValue(Attribute<?> attribute, CompatibilityCheckResult<Object> result) {
            if (result.getConsumerValue().equals(result.getProducerValue())) {
                result.compatible();
                return;
            }

            CompatibilityRule<Object> rules = compatibilityRules(attribute);
            rules.execute(result);
            if (result.hasResult()) {
                return;
            }
            rules = producerSchema.compatibilityRules(attribute);
            rules.execute(result);
            if (result.hasResult()) {
                return;
            }

            // If no result, the values are not compatible
            result.incompatible();
        }

        @Override
        public Set<Attribute<?>> getAttributes() {
            return Sets.union(DefaultAttributesSchema.this.getAttributes(), producerSchema.getAttributes());
        }
    }
}
