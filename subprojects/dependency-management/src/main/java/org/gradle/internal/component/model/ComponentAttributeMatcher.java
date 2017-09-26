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
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.MultipleCandidatesResult;
import org.gradle.internal.Cast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ComponentAttributeMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentAttributeMatcher.class);

    /**
     * Determines whether the given candidate is compatible with the requested criteria, according to the given schema.
     */
    public boolean isMatching(AttributeSelectionSchema schema, AttributeContainerInternal candidate, AttributeContainerInternal requested) {
        if (requested.isEmpty() || candidate.isEmpty()) {
            return true;
        }

        ImmutableAttributes requestedAttributes = requested.asImmutable();

        MatchDetails details = new MatchDetails<AttributeContainer>(candidate);
        doMatchCandidate(schema, details.candidateAttributes, requestedAttributes, details);
        return details.compatible;
    }

    /**
     * Selects the candidates from the given set that are compatible with the requested criteria, according to the given schema.
     */
    public <T extends HasAttributes> List<T> match(AttributeSelectionSchema schema, Collection<? extends T> candidates, AttributeContainerInternal requested, @Nullable T fallback) {
        if (candidates.size() == 0) {
            if (fallback != null && isMatching(schema, (AttributeContainerInternal) fallback.getAttributes(), requested)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("No candidates for {}, selected matching fallback {}", requested, fallback);
                }
                return ImmutableList.of(fallback);
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("No candidates for {} and fallback {} does not match. Select nothing.", requested, fallback);
            }
            return ImmutableList.of();
        }

        if (candidates.size() == 1) {
            T candidate = candidates.iterator().next();
            if (isMatching(schema, (AttributeContainerInternal) candidate.getAttributes(), requested)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Selected match {} from candidates {} for {}", candidate, candidates, requested);
                }
                return Collections.singletonList(candidate);
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Selected match [] from candidates {} for {}", candidates, requested);
            }
            return ImmutableList.of();
        }

        ImmutableAttributes requestedAttributes = requested.asImmutable();

        List<T> matches = new Matcher<T>(schema, candidates, requestedAttributes).getMatches();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Selected matches {} from candidates {} for {}", matches, candidates, requested);
        }
        return matches;
    }

    private void doMatchCandidate(AttributeSelectionSchema schema, ImmutableAttributes candidate, ImmutableAttributes requested, MatchDetails details) {
        Set<Attribute<Object>> requestedAttributes = Cast.uncheckedCast(requested.keySet());
        Set<Attribute<Object>> candidateAttributes = Cast.uncheckedCast(candidate.keySet());

        for (Iterator<Attribute<Object>> requestedIterator = requestedAttributes.iterator(); details.compatible && requestedIterator.hasNext();) {
            Attribute<Object> attribute = requestedIterator.next();
            AttributeValue<?> requestedValue = requested.findEntry(attribute);
            AttributeValue<?> actualValue = candidate.findEntry(attribute.getName());
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
            Object actualValue = candidate.getAttribute(attribute);
            details.updateForMissingRequestedValue(attribute, actualValue);
        }
    }

    private class Matcher<T extends HasAttributes> {
        private final AttributeSelectionSchema schema;
        private final List<MatchDetails<T>> matchDetails;
        private final ImmutableAttributes requested;

        public Matcher(AttributeSelectionSchema schema,
                       Collection<? extends T> candidates,
                       ImmutableAttributes requested) {
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
                doMatchCandidate(schema, matchDetail.candidateAttributes, requested, matchDetail);
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
            MultipleCandidatesResult<Object> details = new DefaultCandidateResult<MatchDetails<T>>(candidatesByValue, requested, best);
            schema.disambiguate(attribute, details);
            remainingMatches.retainAll(best);
        }
    }

    private static class MatchDetails<T extends HasAttributes> {
        private final Set<Attribute<Object>> matched = Sets.newHashSet();
        private final Map<Attribute<Object>, Object> matchesByAttribute = Maps.newHashMap();
        private final ImmutableAttributes candidateAttributes;
        private final T candidate;

        private boolean compatible = true;

        MatchDetails(T candidate) {
            this.candidate = candidate;
            candidateAttributes = ((AttributeContainerInternal) candidate.getAttributes()).asImmutable();
        }

        void update(final Attribute<Object> attribute, AttributeSelectionSchema schema, AttributeValue<Object> consumerValue, AttributeValue<Object> producerValue) {
            Object val = producerValue.get();
            Class<Object> attributeType = attribute.getType();
            if (!attributeType.isInstance(val)) {
                Object converted = producerValue.coerce(attributeType);
                if (converted != null) {
                    val = converted;
                } else {
                    String foundType = val.getClass().getName();
                    if (foundType.equals(attributeType.getName())) {
                        foundType += " with a different ClassLoader";
                    }
                    throw new IllegalArgumentException(String.format("Unexpected type for attribute '%s' provided. Expected a value of type %s but found a value of type %s.", attribute.getName(), attributeType.getName(), foundType));
                }
            }
            DefaultCompatibilityCheckResult<Object> details = new DefaultCompatibilityCheckResult<Object>(consumerValue.get(), val);
            schema.matchValue(attribute, details);
            if (details.isCompatible()) {
                matched.add(attribute);
                matchesByAttribute.put(attribute, val);
            } else {
                compatible = false;
            }
        }

        void updateForMissingRequestedValue(Attribute<Object> attribute, Object producerValue) {
            matchesByAttribute.put(attribute, producerValue);
        }
    }
}
