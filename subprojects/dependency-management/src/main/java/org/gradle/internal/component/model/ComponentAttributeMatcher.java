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
import java.util.Iterator;
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

        MatchDetails details = new MatchDetails<AttributeContainer>(candidate);
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

        for (Iterator<Attribute<Object>> requestedIterator = requestedAttributes.iterator(); details.compatible && requestedIterator.hasNext();) {
            Attribute<Object> attribute = requestedIterator.next();
            AttributeValue<Object> requestedValue = attributeValue(attribute, schema, requested);
            AttributeValue<Object> actualValue = attributeValue(attribute, schema, candidateAttributesContainer);
            if (!actualValue.isPresent()) {
                if (ignoreAdditionalConsumerAttributes) {
                    continue;
                }
                details.updateForMissingProducerValue(attribute, schema);
            } else {
                details.update(attribute, schema, requestedValue, actualValue);
            }
        }
        if (!details.compatible) {
            return;
        }

        for (Iterator<Attribute<Object>> candidateIterator = candidateAttributes.iterator(); details.compatible && candidateIterator.hasNext();) {
            Attribute<Object> attribute = candidateIterator.next();
            if (requestedAttributes.contains(attribute)) {
                continue;
            }
            AttributeValue<Object> actualValue = attributeValue(attribute, schema, candidateAttributesContainer);
            if (ignoreAdditionalProducerAttributes) {
                details.matchesByAttribute.put(attribute, actualValue.get());
                continue;
            }
            details.updateForMissingConsumerValue(attribute, actualValue, schema);
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
        private final List<MatchDetails<T>> matchDetails;
        private final AttributeContainer requested;

        public Matcher(AttributeSelectionSchema schema,
                       Collection<T> candidates,
                       AttributeContainer requested) {
            this.schema = schema;
            this.matchDetails = Lists.newArrayListWithCapacity(candidates.size());
            for (T cand : candidates) {
                matchDetails.add(new MatchDetails<T>(cand));
            }
            this.requested = requested;
            doMatch();
        }

        private void doMatch() {
            for (MatchDetails<T> matchDetail : matchDetails) {
                doMatchCandidate(schema, matchDetail.candidate, requested, matchDetail);
            }
        }

        public List<T> getMatches() {
            List<MatchDetails<T>> compatible = new ArrayList<MatchDetails<T>>(1);
            for (MatchDetails<T> details : matchDetails) {
                if (details.compatible) {
                    compatible.add(details);
                }
            }
            if (compatible.size() > 1) {
                compatible = selectClosestMatches(compatible);
            }
            if (compatible.isEmpty()) {
                return Collections.emptyList();
            }
            if (compatible.size() == 1) {
                return Collections.singletonList(compatible.get(0).candidate);
            }
            List<T> selected = new ArrayList<T>(compatible.size());
            for (MatchDetails<T> details : compatible) {
                selected.add(details.candidate);
            }
            return selected;
        }

        private List<MatchDetails<T>> selectClosestMatches(List<MatchDetails<T>> compatible) {
            // check whether any single match is a superset of the others
            for (MatchDetails<T> details : compatible) {
                boolean superSetToAll = true;
                for (MatchDetails candidate : compatible) {
                    if (details != candidate && (!details.matched.containsAll(candidate.matched) || details.matched.equals(candidate.matched))) {
                        superSetToAll = false;
                        break;
                    }
                }
                if (superSetToAll) {
                    return Collections.singletonList(details);
                }
            }

            // if there's more than one compatible match, prefer the closest. However there's a catch.
            // We need to look at all candidates globally, and select the closest match for each attribute
            // then see if there's a non-empty intersection.
            List<MatchDetails<T>> remainingMatches = Lists.newArrayList(compatible);
            List<MatchDetails<T>> best = Lists.newArrayListWithCapacity(compatible.size());
            Multimap<Object, MatchDetails<T>> candidatesByValue = LinkedHashMultimap.create();
            Set<Attribute<?>> allAttributes = Sets.newHashSet();
            for (MatchDetails<T> details : compatible) {
                allAttributes.addAll(details.matchesByAttribute.keySet());
            }
            for (Attribute<?> attribute : allAttributes) {
                for (MatchDetails<T> details : compatible) {
                    Map<Attribute<Object>, Object> matchedAttributes = details.matchesByAttribute;
                    Object val = matchedAttributes.get(attribute);
                    candidatesByValue.put(val, details);
                }
                disambiguate(remainingMatches, candidatesByValue, schema.getDisambiguationRules(attribute), best);
                if (remainingMatches.isEmpty()) {
                    // the intersection is empty, so we cannot choose
                    return remainingMatches;
                }
                candidatesByValue.clear();
                best.clear();
            }
            // there's a subset (or not) of best matches
            return remainingMatches;
        }

        private void disambiguate(List<MatchDetails<T>> remainingMatches,
                                  Multimap<Object, MatchDetails<T>> candidatesByValue,
                                  DisambiguationRuleChainInternal<Object> disambiguationRules,
                                  List<MatchDetails<T>> best) {
            if (candidatesByValue.isEmpty()) {
                // missing or unknown
                return;
            }
            MultipleCandidatesDetails<Object> details = new CandidateDetails<MatchDetails<T>>(candidatesByValue, best);
            disambiguationRules.execute(details);
            remainingMatches.retainAll(best);
        }
    }

    private static class MatchDetails<T extends HasAttributes> {
        private final Set<Attribute<Object>> matched = Sets.newHashSet();
        private final Map<Attribute<Object>, Object> matchesByAttribute = Maps.newHashMap();
        private final T candidate;

        private boolean compatible = true;

        MatchDetails(T candidate) {
            this.candidate = candidate;
        }

        void update(final Attribute<Object> attribute, final AttributeSelectionSchema schema, final AttributeValue<Object> consumerValue, final AttributeValue<Object> producerValue) {
            CompatibilityRuleChainInternal<Object> compatibilityRules = schema.getCompatibilityRules(attribute);
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
                    matched.add(attribute);
                    matchesByAttribute.put(attribute, producerValue.get());
                }

                @Override
                public void incompatible() {
                    compatible = false;
                }
            };
            compatibilityRules.execute(details);
        }

        void updateForMissingProducerValue(Attribute<Object> attribute, AttributeSelectionSchema schema) {
            if (!schema.isCompatibleWhenMissing(attribute)) {
                compatible = false;
            }
        }

        void updateForMissingConsumerValue(Attribute<Object> attribute, AttributeValue<Object> producerValue, AttributeSelectionSchema schema) {
            if (!schema.isCompatibleWhenMissing(attribute)) {
                compatible = false;
                return;
            }
            matchesByAttribute.put(attribute, producerValue.get());
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
            Collection<T> t = candidatesByValue.get(candidate);
            best.addAll(t);
        }
    }
}
