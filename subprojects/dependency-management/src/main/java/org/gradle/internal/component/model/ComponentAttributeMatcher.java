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
package org.gradle.internal.component.model;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Attribute;
import org.gradle.api.AttributeContainer;
import org.gradle.api.AttributeMatchingStrategy;
import org.gradle.api.AttributeValue;
import org.gradle.api.AttributesSchema;
import org.gradle.api.GradleException;
import org.gradle.internal.Cast;
import org.gradle.internal.component.local.model.LocalComponentMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ComponentAttributeMatcher {
    private final AttributesSchema consumerAttributeSchema;
    private final AttributesSchema producerAttributeSchema;
    private final Map<ConfigurationMetadata, MatchDetails> matchDetails = Maps.newHashMap();
    private final AttributeContainer requestedAttributesContainer;

    public ComponentAttributeMatcher(AttributesSchema consumerAttributeSchema, ComponentResolveMetadata targetComponent, AttributeContainer requestedAttributesContainer) {
        this.consumerAttributeSchema = consumerAttributeSchema;
        this.producerAttributeSchema = targetComponent instanceof LocalComponentMetadata ? ((LocalComponentMetadata) targetComponent).getAttributesSchema() : null;
        Set<Attribute<?>> requestedAttributeSet = requestedAttributesContainer.keySet();
        for (String config : targetComponent.getConfigurationNames()) {
            ConfigurationMetadata configuration = targetComponent.getConfiguration(config);
            if (configuration.isCanBeConsumed()) {
                boolean hasAllAttributes = configuration.getAttributes().keySet().containsAll(requestedAttributeSet);
                matchDetails.put(configuration, new MatchDetails(hasAllAttributes));
            }
        }
        this.requestedAttributesContainer = requestedAttributesContainer;
        doMatch();
    }

    private void doMatch() {
        Set<Attribute<Object>> requestedAttributes = Cast.uncheckedCast(requestedAttributesContainer.keySet());
        for (Map.Entry<ConfigurationMetadata, MatchDetails> entry : matchDetails.entrySet()) {
            ConfigurationMetadata key = entry.getKey();
            MatchDetails details = entry.getValue();
            AttributeContainer dependencyAttributesContainer = key.getAttributes();
            Set<Attribute<Object>> dependencyAttributes = Cast.uncheckedCast(dependencyAttributesContainer.keySet());
            Set<Attribute<Object>> commonAttributes = Sets.intersection(requestedAttributes, dependencyAttributes);
            for (Attribute<Object> attribute : commonAttributes) {
                AttributeMatchingStrategy<Object> strategy = Cast.uncheckedCast(consumerAttributeSchema.getMatchingStrategy(attribute));
                try {
                    details.update(attribute, strategy, requestedAttributesContainer.getAttribute(attribute), dependencyAttributesContainer.getAttribute(attribute));
                } catch (Exception ex) {
                    throw new GradleException("Unexpected error thrown when trying to match attribute values with " + strategy, ex);
                }
            }
        }
    }

    public List<ConfigurationMetadata> getFullMatchs() {
        List<ConfigurationMetadata> matchs = new ArrayList<ConfigurationMetadata>(1);
        for (Map.Entry<ConfigurationMetadata, MatchDetails> entry : matchDetails.entrySet()) {
            MatchDetails details = entry.getValue();
            if (details.isFullMatch && details.hasAllAttributes) {
                matchs.add(entry.getKey());
            }
        }
        return disambiguateUsingClosestMatch(matchs);
    }

    private List<ConfigurationMetadata> disambiguateUsingClosestMatch(List<ConfigurationMetadata> matchs) {
        if (matchs.size() > 1) {
            List<ConfigurationMetadata> remainingMatches = selectClosestMatches(matchs);
            if (remainingMatches != null) {
                return disambiguateUsingProducerSchema(remainingMatches);
            }
        }
        return matchs;
    }

    private List<ConfigurationMetadata> disambiguateUsingProducerSchema(List<ConfigurationMetadata> matchs) {
        if (matchs.size() < 2 || producerAttributeSchema == null) {
            return matchs;
        }
        // If we are reaching this point, it means that we have more than one match, so we need to
        // ask the producer if it has any preference: so far only the consumer schema was used. Now
        // we need to take into consideration the producer schema
        Set<Attribute<?>> producerOnlyAttributes = Sets.newHashSet();
        for (ConfigurationMetadata match : matchs) {
            AttributeContainer attributes = match.getAttributes();
            for (Attribute<?> attribute : attributes.keySet()) {
                if (!requestedAttributesContainer.contains(attribute)) {
                    producerOnlyAttributes.add(attribute);
                }
            }
        }
        Set<Attribute<?>> consumerAttributes = consumerAttributeSchema.getAttributes();
        List<ConfigurationMetadata> remainingMatches = Lists.newArrayList(matchs);
        Map<ConfigurationMetadata, Object> values = Maps.newHashMap();
        for (Attribute<?> attribute : producerOnlyAttributes) {
            for (ConfigurationMetadata match : matchs) {
                Object maybeProvided = match.getAttributes().getAttribute(attribute);
                if (maybeProvided != null) {
                    values.put(match, maybeProvided);
                }
            }
            if (!values.isEmpty()) {
                AttributeMatchingStrategy<Object> matchingStrategy = Cast.uncheckedCast(producerAttributeSchema.getMatchingStrategy(attribute));
                AttributeValue<Object> absent = consumerAttributes.contains(attribute) ? AttributeValue.missing() : AttributeValue.unknown();
                List<ConfigurationMetadata> best = matchingStrategy.selectClosestMatch(absent, values);
                remainingMatches.retainAll(best);
                if (remainingMatches.isEmpty()) {
                    // the intersection is empty, so we cannot choose
                    return matchs;
                }
                values.clear();
            }
        }
        if (!remainingMatches.isEmpty()) {
            // there's a subset (or not) of best matches
            return remainingMatches;
        }
        return matchs;
    }

    public List<ConfigurationMetadata> getPartialMatchs() {
        List<ConfigurationMetadata> matchs = new ArrayList<ConfigurationMetadata>(1);
        for (Map.Entry<ConfigurationMetadata, MatchDetails> entry : matchDetails.entrySet()) {
            MatchDetails details = entry.getValue();
            if (details.isPartialMatch && !details.hasAllAttributes) {
                matchs.add(entry.getKey());
            }
        }
        return disambiguateUsingClosestMatch(matchs);
    }

    private List<ConfigurationMetadata> selectClosestMatches(List<ConfigurationMetadata> fullMatches) {
        Set<Attribute<?>> requestedAttributes = requestedAttributesContainer.keySet();
        // if there's more than one compatible match, prefer the closest. However there's a catch.
        // We need to look at all candidates globally, and select the closest match for each attribute
        // then see if there's a non-empty intersection.
        List<ConfigurationMetadata> remainingMatches = Lists.newArrayList(fullMatches);
        Map<ConfigurationMetadata, Object> values = Maps.newHashMap();
        for (Attribute<?> attribute : requestedAttributes) {
            Object requestedValue = requestedAttributesContainer.getAttribute(attribute);
            for (ConfigurationMetadata match : fullMatches) {
                Map<Attribute<Object>, Object> matchedAttributes = matchDetails.get(match).matchesByAttribute;
                values.put(match, matchedAttributes.get(attribute));
            }
            AttributeMatchingStrategy<Object> matchingStrategy = Cast.uncheckedCast(consumerAttributeSchema.getMatchingStrategy(attribute));
            List<ConfigurationMetadata> best = matchingStrategy.selectClosestMatch(AttributeValue.of(requestedValue), values);
            remainingMatches.retainAll(best);
            if (remainingMatches.isEmpty()) {
                // the intersection is empty, so we cannot choose
                return fullMatches;
            }
            values.clear();
        }
        if (!remainingMatches.isEmpty()) {
            // there's a subset (or not) of best matches
            return remainingMatches;
        }
        return null;
    }

    private static class MatchDetails {
        private final Map<Attribute<Object>, Object> matchesByAttribute = Maps.newHashMap();
        private final boolean hasAllAttributes;

        private boolean isFullMatch;
        private boolean isPartialMatch;

        private MatchDetails(boolean hasAllAttributes) {
            this.hasAllAttributes = hasAllAttributes;
            this.isFullMatch = hasAllAttributes;
        }

        private void update(Attribute<Object> attribute, AttributeMatchingStrategy<Object> strategy, Object requested, Object provided) {
            boolean attributeCompatible = strategy.isCompatible(requested, provided);
            if (attributeCompatible) {
                matchesByAttribute.put(attribute, provided);
            }
            isFullMatch &= attributeCompatible;
            isPartialMatch |= attributeCompatible;
        }
    }
}
