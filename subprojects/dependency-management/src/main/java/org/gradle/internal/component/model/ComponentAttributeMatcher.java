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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * An attribute matcher, which optimizes for the case of only comparing 0 or 1 candidates and delegates to {@link MultipleCandidateMatcher} for all other cases.
 */
public class ComponentAttributeMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentAttributeMatcher.class);

    /**
     * Attribute matching can be very expensive. In case there are multiple candidates, we
     * cache the result of the query, because it's often the case that we ask for the same
     * disambiguation of attributes several times in a row (but with different candidates).
     */
    private final Map<CachedQuery, int[]> cachedQueries = Maps.newConcurrentMap();

    /**
     * Determines whether the given candidate is compatible with the requested criteria, according to the given schema.
     */
    public boolean isMatching(AttributeSelectionSchema schema, AttributeContainerInternal candidate, AttributeContainerInternal requested) {
        if (requested.isEmpty() || candidate.isEmpty()) {
            return true;
        }

        ImmutableAttributes requestedAttributes = requested.asImmutable();
        ImmutableAttributes candidateAttributes = candidate.asImmutable();

        for (Attribute<?> attribute : requestedAttributes.keySet()) {
            AttributeValue<?> requestedValue = requestedAttributes.findEntry(attribute);
            AttributeValue<?> candidateValue = candidateAttributes.findEntry(attribute.getName());
            if (candidateValue.isPresent()) {
                Object coercedValue = candidateValue.coerce(attribute);
                boolean match = schema.matchValue(attribute, requestedValue.get(), coercedValue);
                if (!match) {
                    return false;
                }
            }
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<AttributeMatcher.MatchingDescription<?>> describeMatching(AttributeSelectionSchema schema, AttributeContainerInternal candidate, AttributeContainerInternal requested) {
        if (requested.isEmpty() || candidate.isEmpty()) {
            return Collections.emptyList();
        }

        ImmutableAttributes requestedAttributes = requested.asImmutable();
        ImmutableAttributes candidateAttributes = candidate.asImmutable();

        ImmutableSet<Attribute<?>> attributes = requestedAttributes.keySet();
        List<AttributeMatcher.MatchingDescription<?>> result = Lists.newArrayListWithCapacity(attributes.size());
        for (Attribute<?> attribute : attributes) {
            AttributeValue<?> requestedValue = requestedAttributes.findEntry(attribute);
            AttributeValue<?> candidateValue = candidateAttributes.findEntry(attribute.getName());
            if (candidateValue.isPresent()) {
                Object coercedValue = candidateValue.coerce(attribute);
                boolean match = schema.matchValue(attribute, requestedValue.get(), coercedValue);
                result.add(new AttributeMatcher.MatchingDescription(attribute, requestedValue, candidateValue, match));
            } else {
                result.add(new AttributeMatcher.MatchingDescription(attribute, requestedValue, candidateValue, false));
            }
        }
        return result;
    }

    /**
     * Selects the candidates from the given set that are compatible with the requested criteria, according to the given schema.
     */
    public <T extends HasAttributes> List<T> match(AttributeSelectionSchema schema, Collection<? extends T> candidates, AttributeContainerInternal requested, @Nullable T fallback, AttributeMatchingExplanationBuilder explanationBuilder) {
        if (candidates.size() == 0) {
            if (fallback != null && isMatching(schema, (AttributeContainerInternal) fallback.getAttributes(), requested)) {
                explanationBuilder.selectedFallbackConfiguration(requested, fallback);
                return ImmutableList.of(fallback);
            }
            explanationBuilder.noCandidates(requested, fallback);
            return ImmutableList.of();
        }

        if (candidates.size() == 1) {
            T candidate = candidates.iterator().next();
            if (isMatching(schema, (AttributeContainerInternal) candidate.getAttributes(), requested)) {
                explanationBuilder.singleMatch(candidate, candidates, requested);
                return Collections.singletonList(candidate);
            }
            explanationBuilder.candidateDoesNotMatchAttributes(candidate, requested);
            return ImmutableList.of();
        }

        ImmutableAttributes requestedAttributes = requested.asImmutable();
        CachedQuery query = null;
        if (explanationBuilder.canSkipExplanation()) {
            query = CachedQuery.of(schema, requestedAttributes, candidates);
            int[] index = cachedQueries.get(query);
            if (index != null) {
                return CachedQuery.select(index, candidates);
            }
        }
        List<T> matches = new MultipleCandidateMatcher<T>(schema, candidates, requestedAttributes, explanationBuilder).getMatches();
        if (query != null) {
            LOGGER.debug("Selected matches {} from candidates {} for {}", matches, candidates, requested);
            cacheMatchingResult(candidates, query, matches);
        }
        return matches;
    }

    // in theory we don't need the synchronized here, but let's be safer in the beginning
    private synchronized <T extends HasAttributes> void cacheMatchingResult(Collection<? extends T> candidates, CachedQuery query, List<T> matches) {
        int[] queryResult;
        if (matches.isEmpty()) {
            queryResult = new int[0];
        } else {
            queryResult = new int[matches.size()];
            int i = 0;
            int j = 0;
            Iterator<T> resultIterator = matches.iterator();
            T next = resultIterator.next();
            for (T candidate : candidates) {
                if (candidate == next) {
                    queryResult[i++] = j;
                    if (resultIterator.hasNext()) {
                        next = resultIterator.next();
                    } else {
                        break;
                    }
                }
                j++;
            }
        }
        cachedQueries.put(query, queryResult);
    }

    private static class CachedQuery {
        private final AttributeSelectionSchema schema;
        private final ImmutableAttributes requestedAttributes;
        private final ImmutableAttributes[] candidates;
        private final int hashCode;

        private CachedQuery(AttributeSelectionSchema schema, ImmutableAttributes requestedAttributes, ImmutableAttributes[] candidates) {
            this.schema = schema;
            this.requestedAttributes = requestedAttributes;
            this.candidates = candidates;
            this.hashCode = computeHashCode(schema, requestedAttributes, candidates);
        }

        private int computeHashCode(AttributeSelectionSchema schema, ImmutableAttributes requestedAttributes, ImmutableAttributes[] candidates) {
            int hash = schema.hashCode();
            hash = 31 * hash + requestedAttributes.hashCode();
            for (ImmutableAttributes candidate : candidates) {
                hash = 31 * hash + candidate.hashCode();
            }
            return hash;
        }

        public static <T extends HasAttributes> CachedQuery of(AttributeSelectionSchema schema, ImmutableAttributes requestedAttributes, Collection<T> candidates) {
            ImmutableAttributes[] attributes = new ImmutableAttributes[candidates.size()];
            int i = 0;
            for (T candidate : candidates) {
                attributes[i++] = ((AttributeContainerInternal) candidate.getAttributes()).asImmutable();
            }
            return new CachedQuery(schema, requestedAttributes, attributes);
        }

        public static <T extends HasAttributes> List<T> select(int[] index, Collection<? extends T> unfiltered) {
            if (index.length == 0) {
                return Collections.emptyList();
            }
            List<T> result = Lists.newArrayListWithCapacity(index.length);
            int i = 0;
            int j = 0;
            int k = index[j];
            for (T t : unfiltered) {
                if (i == k) {
                    result.add(t);
                    if (result.size() == index.length) {
                        break;
                    }
                    k = index[++j];
                }
                i++;
            }
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CachedQuery that = (CachedQuery) o;
            return hashCode == that.hashCode &&
                schema.equals(that.schema) &&
                requestedAttributes.equals(that.requestedAttributes) &&
                Arrays.equals(candidates, that.candidates);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return "CachedQuery{" +
                "requestedAttributes=" + requestedAttributes +
                ", candidates=" + Arrays.toString(candidates) +
                '}';
        }
    }
}
