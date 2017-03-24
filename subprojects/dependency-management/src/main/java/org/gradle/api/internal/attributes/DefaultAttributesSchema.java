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
import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.InstantiatorFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.AttributeSelectionSchema;
import org.gradle.internal.component.model.ComponentAttributeMatcher;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultAttributesSchema implements AttributesSchemaInternal, AttributesSchema, AttributeSelectionSchema {
    private final ComponentAttributeMatcher componentAttributeMatcher;
    private final InstantiatorFactory instantiatorFactory;
    private final Map<Attribute<?>, AttributeMatchingStrategy<?>> strategies = Maps.newHashMap();
    private final DefaultAttributeMatcher matcher;

    public DefaultAttributesSchema(ComponentAttributeMatcher componentAttributeMatcher, InstantiatorFactory instantiatorFactory) {
        this.componentAttributeMatcher = componentAttributeMatcher;
        this.instantiatorFactory = instantiatorFactory;
        matcher = new DefaultAttributeMatcher(componentAttributeMatcher, this);
    }

    @Override
    @SuppressWarnings("unchecked")
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

    AttributeSelectionSchema mergeFrom(AttributesSchemaInternal producerSchema) {
        boolean same = true;
        for (Attribute<?> attribute : producerSchema.getAttributes()) {
            if (!getAttributes().contains(attribute)) {
                same = false;
                break;
            }
            CompatibilityRuleChainInternal<?> producerRule = (CompatibilityRuleChainInternal<?>) producerSchema.getMatchingStrategy(attribute).getCompatibilityRules();
            CompatibilityRuleChainInternal<?> thisRule = (CompatibilityRuleChainInternal<?>) getMatchingStrategy(attribute).getCompatibilityRules();
            if (producerRule.isCompatibleWhenMissing() != thisRule.isCompatibleWhenMissing()) {
                same = false;
                break;
            }
        }
        if (same) {
            return this;
        }
        return new MergedSchema(producerSchema);
    }

    @Override
    public boolean isCompatibleWhenMissing(Attribute<?> attribute) {
        return getCompatibilityRules(attribute).isCompatibleWhenMissing();
    }

    @Override
    public AttributeMatcher withProducer(AttributesSchemaInternal producerSchema) {
        AttributeSelectionSchema effectiveSchema = mergeFrom(producerSchema);
        if (effectiveSchema == this) {
            return matcher;
        }
        return new DefaultAttributeMatcher(componentAttributeMatcher, effectiveSchema);
    }

    @Override
    public AttributeMatcher matcher() {
        return matcher;
    }

    @Override
    public DisambiguationRuleChainInternal<Object> getDisambiguationRules(Attribute<?> attribute) {
        return Cast.uncheckedCast(getMatchingStrategy(attribute).getDisambiguationRules());
    }

    @Override
    public CompatibilityRuleChainInternal<Object> getCompatibilityRules(Attribute<?> attribute) {
        return Cast.uncheckedCast(getMatchingStrategy(attribute).getCompatibilityRules());
    }

    private static class DefaultAttributeMatcher implements AttributeMatcher {
        private final ComponentAttributeMatcher componentAttributeMatcher;
        private final AttributeSelectionSchema effectiveSchema;

        DefaultAttributeMatcher(ComponentAttributeMatcher componentAttributeMatcher, AttributeSelectionSchema effectiveSchema) {
            this.componentAttributeMatcher = componentAttributeMatcher;
            this.effectiveSchema = effectiveSchema;
        }

        @Override
        public AttributeMatcher ignoreAdditionalConsumerAttributes() {
            return new DefaultAttributeMatcher(componentAttributeMatcher.ignoreAdditionalConsumerAttributes(), effectiveSchema);
        }

        @Override
        public AttributeMatcher ignoreAdditionalProducerAttributes() {
            return new DefaultAttributeMatcher(componentAttributeMatcher.ignoreAdditionalProducerAttributes(), effectiveSchema);
        }

        @Override
        public boolean isMatching(AttributeContainer candidate, AttributeContainer requested) {
            return componentAttributeMatcher.isMatching(effectiveSchema, candidate, requested);
        }

        @Override
        public <T extends HasAttributes> List<T> matches(Collection<T> candidates, AttributeContainerInternal requested) {
            return componentAttributeMatcher.match(effectiveSchema, candidates, requested);
        }
    }

    private class MergedSchema implements AttributeSelectionSchema {
        private final AttributesSchemaInternal producerSchema;

        MergedSchema(AttributesSchemaInternal producerSchema) {
            this.producerSchema = producerSchema;
        }

        @Override
        public boolean hasAttribute(Attribute<?> attribute) {
            return getAttributes().contains(attribute) || producerSchema.getAttributes().contains(attribute);
        }

        @Override
        public DisambiguationRuleChainInternal<Object> getDisambiguationRules(Attribute<?> attribute) {
            return Cast.uncheckedCast(strategyFor(attribute).getDisambiguationRules());
        }

        @Override
        public CompatibilityRuleChainInternal<Object> getCompatibilityRules(Attribute<?> attribute) {
            return Cast.uncheckedCast(strategyFor(attribute).getCompatibilityRules());
        }

        @Override
        public boolean isCompatibleWhenMissing(Attribute<?> attribute) {
            AttributeMatchingStrategy<?> attributeMatchingStrategy = strategies.get(attribute);
            if (attributeMatchingStrategy != null) {
                if (((CompatibilityRuleChainInternal<?>) attributeMatchingStrategy.getCompatibilityRules()).isCompatibleWhenMissing()) {
                    return true;
                }
            }
            attributeMatchingStrategy = producerSchema.getMatchingStrategy(attribute);
            if (attributeMatchingStrategy != null) {
                return ((CompatibilityRuleChainInternal<?>) attributeMatchingStrategy.getCompatibilityRules()).isCompatibleWhenMissing();
            }
            return false;
        }

        private AttributeMatchingStrategy<?> strategyFor(Attribute<?> attribute) {
            AttributeMatchingStrategy<?> matchingStrategy = strategies.get(attribute);
            if (matchingStrategy == null) {
                matchingStrategy = producerSchema.getMatchingStrategy(attribute);
            }
            return matchingStrategy;
        }
    }
}
