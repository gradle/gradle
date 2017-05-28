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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.api.Nullable;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.MultipleCandidatesResult;
import org.gradle.internal.Cast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ComponentAttributeMatcher {
    /**
     * Determines whether the given candidate is compatible with the requested criteria, according to the given schema.
     */
    public boolean isMatching(AttributeSelectionSchema schema, AttributeContainer candidate, AttributeContainer requested) {
        if (requested.isEmpty() || candidate.isEmpty()) {
            return true;
        }

        MatchDetails details = new MatchDetails<AttributeContainer>(candidate);
        doMatchCandidate(schema, candidate, requested, details);
        return details.compatible;
    }

    /**
     * Selects the candidates from the given set that are compatible with the requested criteria, according to the given schema.
     */
    public <T extends HasAttributes> List<T> match(AttributeSelectionSchema schema, Collection<? extends T> candidates, AttributeContainer requested, @Nullable T fallback) {
        if (candidates.size() == 0) {
            if (fallback != null && isMatching(schema, fallback.getAttributes(), requested)) {
                return ImmutableList.of(fallback);
            }
            return ImmutableList.of();
        }
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
            if (actualValue.isPresent()) {
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
            details.updateForMissingConsumerValue(attribute, actualValue);
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
                       Collection<? extends T> candidates,
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
                disambiguate(attribute, requested.getAttribute(attribute), remainingMatches, candidatesByValue, schema, best);
                if (remainingMatches.isEmpty()) {
                    // the intersection is empty, so we cannot choose
                    return compatible;
                }
                candidatesByValue.clear();
                best.clear();
            }
            // there's a subset (or not) of best matches
            return remainingMatches;
        }

        private void disambiguate(Attribute<?> attribute,
                                  Object requested,
                                  List<MatchDetails<T>> remainingMatches,
                                  Multimap<Object, MatchDetails<T>> candidatesByValue,
                                  AttributeSelectionSchema schema,
                                  List<MatchDetails<T>> best) {
            if (candidatesByValue.isEmpty()) {
                // missing or unknown
                return;
            }
            MultipleCandidatesResult<Object> details = new DefaultCandidateResult<MatchDetails<T>>(candidatesByValue, best);
            schema.disambiguate(attribute, requested, details);
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

        void update(final Attribute<Object> attribute, AttributeSelectionSchema schema, AttributeValue<Object> consumerValue, AttributeValue<Object> producerValue) {
            DefaultCompatibilityCheckResult<Object> details = new DefaultCompatibilityCheckResult<Object>(consumerValue.get(), producerValue.get());
            schema.matchValue(attribute, details);
            if (details.isCompatible()) {
                matched.add(attribute);
                matchesByAttribute.put(attribute, producerValue.get());
            } else {
                compatible = false;
            }
        }

        void updateForMissingConsumerValue(Attribute<Object> attribute, AttributeValue<Object> producerValue) {
            matchesByAttribute.put(attribute, producerValue.get());
        }
    }
}
