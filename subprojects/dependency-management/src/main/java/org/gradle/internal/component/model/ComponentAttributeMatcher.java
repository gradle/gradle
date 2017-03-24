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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.CompatibilityRuleChainInternal;
import org.gradle.api.internal.attributes.DisambiguationRuleChainInternal;
import org.gradle.internal.Cast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ComponentAttributeMatcher {
    private final boolean ignoreAdditionalProducerAttributes;
    private final boolean ignoreAdditionalConsumerAttributes;

    public ComponentAttributeMatcher() {
        this(false, false);
    }

    private ComponentAttributeMatcher(boolean ignoreAdditionalProducerAttributes, boolean ignoreAdditionalConsumerAttributes) {
        this.ignoreAdditionalProducerAttributes = ignoreAdditionalProducerAttributes;
        this.ignoreAdditionalConsumerAttributes = ignoreAdditionalConsumerAttributes;
    }

    public ComponentAttributeMatcher ignoreAdditionalProducerAttributes() {
        return new ComponentAttributeMatcher(true, ignoreAdditionalConsumerAttributes);
    }

    public ComponentAttributeMatcher ignoreAdditionalConsumerAttributes() {
        return new ComponentAttributeMatcher(ignoreAdditionalProducerAttributes, true);
    }

    /**
     * Determines whether the given candidate is compatible with the requested criteria, according to the given schema.
     */
    public boolean isMatching(AttributeSelectionSchema schema, AttributeContainer candidate, AttributeContainer requested) {
        if (requested.isEmpty()) {
            if (candidate.isEmpty() || ignoreAdditionalProducerAttributes) {
                return true;
            }
        }

        MatchDetails details = new MatchDetails();
        doMatchCandidate(schema, candidate, requested, details);
        return details.compatible;
    }

    /**
     * Selects the candidates from the given set that are compatible with the requested criteria, according to the given schema.
     */
    public <T extends HasAttributes> List<T> match(AttributeSelectionSchema schema, Collection<T> candidates, AttributeContainer requested) {
        if (candidates.size() == 1) {
            T candidate = candidates.iterator().next();
            if (isMatching(schema, candidate.getAttributes(), requested)) {
                return Collections.singletonList(candidate);
            }
        }
        return new Matcher<T>(schema, candidates, requested).getMatches();
    }

    private void doMatchCandidate(AttributeSelectionSchema schema, HasAttributes candidate, AttributeContainer requested, MatchDetails details) {
        Set<Attribute<Object>> requestedAttributes = Cast.uncheckedCast(requested.keySet());
        AttributeContainer candidateAttributesContainer = candidate.getAttributes();
        Set<Attribute<Object>> candidateAttributes = Cast.uncheckedCast(candidateAttributesContainer.keySet());
        Set<Attribute<Object>> allAttributes = Sets.union(requestedAttributes, candidateAttributes);
        for (Attribute<Object> attribute : allAttributes) {
            AttributeValue<Object> requestedValue = attributeValue(attribute, schema, requested);
            AttributeValue<Object> actualValue = attributeValue(attribute, schema, candidateAttributesContainer);
            if (!requestedValue.isPresent() && ignoreAdditionalProducerAttributes) {
                details.matchesByAttribute.put(attribute, actualValue.get());
                continue;
            }
            if (!actualValue.isPresent() && ignoreAdditionalConsumerAttributes) {
                continue;
            }
            details.update(attribute, schema, requestedValue, actualValue);
        }
    }

    private AttributeValue<Object> attributeValue(Attribute<Object> attribute, AttributeSelectionSchema schema, AttributeContainer container) {
        if (container.contains(attribute)) {
            return AttributeValue.of(container.getAttribute(attribute));
        }
        if (schema.hasAttribute(attribute)) {
            return AttributeValue.missing();
        } else {
            return AttributeValue.unknown();
        }
    }

    private class Matcher<T extends HasAttributes> {
        private final AttributeSelectionSchema schema;
        private final Map<T, MatchDetails> matchDetails = Maps.newLinkedHashMap();
        private final AttributeContainer requested;

        public Matcher(AttributeSelectionSchema schema,
                       Iterable<T> candidates,
                       AttributeContainer requested) {
            this.schema = schema;
            for (T cand : candidates) {
                if (!cand.getAttributes().isEmpty()) {
                    matchDetails.put(cand, new MatchDetails());
                }
            }
            this.requested = requested;
            doMatch();
        }

        private void doMatch() {
            for (Map.Entry<T, MatchDetails> entry : matchDetails.entrySet()) {
                doMatchCandidate(schema, entry.getKey(), requested, entry.getValue());
            }
        }

        public List<T> getMatches() {
            List<T> matches = new ArrayList<T>(1);
            for (Map.Entry<T, MatchDetails> entry : matchDetails.entrySet()) {
                MatchDetails details = entry.getValue();
                if (details.compatible) {
                    matches.add(entry.getKey());
                }
            }
            return disambiguateUsingClosestMatch(matches);
        }

        private List<T> disambiguateUsingClosestMatch(List<T> matches) {
            if (matches.size() > 1) {
                return selectClosestMatches(matches);
            }
            return matches;
        }

        private List<T> selectClosestMatches(List<T> matches) {
            // if there's more than one compatible match, prefer the closest. However there's a catch.
            // We need to look at all candidates globally, and select the closest match for each attribute
            // then see if there's a non-empty intersection.
            List<T> remainingMatches = Lists.newArrayList(matches);
            List<T> best = Lists.newArrayListWithCapacity(matches.size());
            Multimap<Object, T> candidatesByValue = LinkedHashMultimap.create();
            Set<Attribute<?>> allAttributes = Sets.newHashSet();
            for (MatchDetails details : matchDetails.values()) {
                allAttributes.addAll(details.matchesByAttribute.keySet());
            }
            for (Attribute<?> attribute : allAttributes) {
                for (T match : matches) {
                    Map<Attribute<Object>, Object> matchedAttributes = matchDetails.get(match).matchesByAttribute;
                    Object val = matchedAttributes.get(attribute);
                    candidatesByValue.put(val, match);
                }
                disambiguate(remainingMatches, candidatesByValue, schema.getDisambiguationRules(attribute), best);
                if (remainingMatches.isEmpty()) {
                    // the intersection is empty, so we cannot choose
                    return matches;
                }
                candidatesByValue.clear();
                best.clear();
            }
            if (!remainingMatches.isEmpty()) {
                // there's a subset (or not) of best matches
                return remainingMatches;
            }
            return null;
        }

        private void disambiguate(List<T> remainingMatches,
                                  Multimap<Object, T> candidatesByValue,
                                  DisambiguationRuleChainInternal<Object> disambiguationRules,
                                  List<T> best) {
            if (candidatesByValue.isEmpty()) {
                // missing or unknown
                return;
            }
            MultipleCandidatesDetails<Object> details = new CandidateDetails<T>(candidatesByValue, best);
            disambiguationRules.execute(details);
            remainingMatches.retainAll(best);
        }
    }

    private static class MatchDetails {
        private final Map<Attribute<Object>, Object> matchesByAttribute = Maps.newHashMap();

        private boolean compatible = true;

        private void update(final Attribute<Object> attribute, final AttributeSelectionSchema schema, final AttributeValue<Object> consumerValue, final AttributeValue<Object> producerValue) {
            boolean missingOrUnknown = false;
            if (consumerValue.isUnknown() || consumerValue.isMissing()) {
                // We need to use the producer schema in this case
                missingOrUnknown = true;
            } else if (producerValue.isUnknown() || producerValue.isMissing()) {
                missingOrUnknown = true;
            }
            CompatibilityRuleChainInternal<Object> compatibilityRules = schema.getCompatibilityRules(attribute);
            if (missingOrUnknown) {
                if (schema.isCompatibleWhenMissing(attribute)) {
                    if (producerValue.isPresent()) {
                        matchesByAttribute.put(attribute, producerValue.get());
                    }
                } else {
                    compatible = false;
                }
                return;
            }
            CompatibilityCheckDetails<Object> details = new CompatibilityCheckDetails<Object>() {
                @Override
                public Object getConsumerValue() {
                    return consumerValue.get();
                }

                @Override
                public Object getProducerValue() {
                    return producerValue.get();
                }

                @Override
                public void compatible() {
                    matchesByAttribute.put(attribute, producerValue.get());
                }

                @Override
                public void incompatible() {
                    compatible = false;
                }
            };
            compatibilityRules.execute(details);
        }
    }

    private static class CandidateDetails<T> implements MultipleCandidatesDetails<Object> {
        private final Multimap<Object, T> candidatesByValue;
        private final List<T> best;

        CandidateDetails(Multimap<Object, T> candidatesByValue, List<T> best) {
            this.candidatesByValue = candidatesByValue;
            this.best = best;
        }

        @Override
        public Set<Object> getCandidateValues() {
            return candidatesByValue.keySet();
        }

        @Override
        public void closestMatch(Object candidate) {
            Collection<T> hasAttributes = candidatesByValue.get(candidate);
            for (T attributes : hasAttributes) {
                best.add(attributes);
            }
        }
    }
}
