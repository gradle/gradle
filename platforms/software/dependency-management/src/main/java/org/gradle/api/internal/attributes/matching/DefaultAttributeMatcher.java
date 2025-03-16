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
package org.gradle.api.internal.attributes.matching;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder;
import org.gradle.internal.model.InMemoryCacheFactory;
import org.gradle.internal.model.InMemoryLoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * An {@link AttributeMatcher}, which optimizes for the case of only comparing 0 or 1 candidates
 * and delegates to {@link MultipleCandidateMatcher} for all other cases.
 */
public class DefaultAttributeMatcher implements AttributeMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAttributeMatcher.class);

    private final AttributeSelectionSchema schema;

    /**
     * Attribute matching can be very expensive. In case there are multiple candidates, we
     * cache the result of the query, because it's often the case that we ask for the same
     * disambiguation of attributes several times in a row (but with different candidates).
     */
    private final InMemoryLoadingCache<CachedQuery, int[]> cachedQueries;
    private final InMemoryLoadingCache<MatchingCandidateCacheKey, Boolean> matchingCandidatesCache;

    public DefaultAttributeMatcher(
        AttributeSelectionSchema schema,
        InMemoryCacheFactory cacheFactory
    ) {
        this.schema = schema;
        this.cachedQueries = cacheFactory.create(this::doMatchMultipleCandidates);
        this.matchingCandidatesCache = cacheFactory.create(this::doIsMatchingCandidate);
    }

    @Override
    public <T> boolean isMatchingValue(Attribute<T> attribute, T candidate, T requested) {
        return schema.matchValue(attribute, requested, candidate);
    }

    @Override
    public boolean isMatchingCandidate(ImmutableAttributes candidate, ImmutableAttributes requested) {
        MatchingCandidateCacheKey key = new MatchingCandidateCacheKey(candidate, requested);
        return matchingCandidatesCache.get(key);
    }

    private boolean doIsMatchingCandidate(MatchingCandidateCacheKey k) {
        return allCommonAttributesSatisfy(k.candidate, k.requested, schema::matchValue);
    }

    @Override
    public boolean areMutuallyCompatible(ImmutableAttributes candidate, ImmutableAttributes requested) {
        return allCommonAttributesSatisfy(candidate, requested, schema::weakMatchValue);
    }

    /**
     * Return true iff all common attributes between the candidate and requested
     * attribute sets satisfy the given predicate.
     */
    private boolean allCommonAttributesSatisfy(
        ImmutableAttributes candidate,
        ImmutableAttributes requested,
        CoercingAttributeValuePredicate predicate
    ) {
        if (requested.isEmpty() || candidate.isEmpty()) {
            return true;
        }

        for (Attribute<?> attribute : requested.keySet()) {
            AttributeValue<?> requestedAttributeValue = requested.findEntry(attribute);
            AttributeValue<?> candidateAttributeValue = candidate.findEntry(attribute.getName());

            if (candidateAttributeValue.isPresent()) {
                Attribute<?> typedAttribute = schema.tryRehydrate(attribute);
                if (!predicate.test(typedAttribute, requestedAttributeValue, candidateAttributeValue)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Matches two attribute values, one from the consumer and one from the producer,
     * that share a common attribute type.
     */
    private interface CoercingAttributeValuePredicate {

        /**
         * Test that the candidate attribute value satisfies the requested attribute value.
         */
        <A> boolean test(Attribute<A> attribute, A requested, A candidate);

        /**
         * Coerce the candidate and requested attribute values to the type of the attribute
         * and test that they match.
         */
        default <T> boolean test(
            Attribute<T> attribute,
            AttributeValue<?> requested,
            AttributeValue<?> candidate
        ) {
            T requestedValue = requested.coerce(attribute);
            T candidateValue = candidate.coerce(attribute);
            return test(attribute, requestedValue, candidateValue);
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<AttributeMatcher.MatchingDescription<?>> describeMatching(ImmutableAttributes candidate, ImmutableAttributes requested) {
        if (requested.isEmpty() || candidate.isEmpty()) {
            return Collections.emptyList();
        }

        CoercingAttributeValuePredicate matches = schema::matchValue;

        ImmutableSet<Attribute<?>> attributes = requested.keySet();
        List<AttributeMatcher.MatchingDescription<?>> result = new ArrayList<>(attributes.size());
        for (Attribute<?> attribute : attributes) {
            AttributeValue<?> requestedValue = requested.findEntry(attribute);
            AttributeValue<?> candidateValue = candidate.findEntry(attribute.getName());
            if (candidateValue.isPresent()) {
                Attribute<?> typedAttribute = schema.tryRehydrate(attribute);
                boolean match = matches.test(typedAttribute, requestedValue, candidateValue);
                result.add(new AttributeMatcher.MatchingDescription(attribute, requestedValue, candidateValue, match));
            } else {
                result.add(new AttributeMatcher.MatchingDescription(attribute, requestedValue, candidateValue, false));
            }
        }
        return result;
    }

    @Override
    public <T extends HasAttributes> List<T> matchMultipleCandidates(Collection<? extends T> candidates, ImmutableAttributes requested) {
        AttributeMatchingExplanationBuilder explanationBuilder = AttributeMatchingExplanationBuilder.logging();

        if (candidates.isEmpty()) {
            explanationBuilder.noCandidates(requested);
            return ImmutableList.of();
        }

        if (candidates.size() == 1) {
            T candidate = candidates.iterator().next();
            ImmutableAttributes candidateAttrs = ((AttributeContainerInternal) candidate.getAttributes()).asImmutable();
            if (isMatchingCandidate(candidateAttrs, requested)) {
                explanationBuilder.singleMatch(candidate, candidates, requested);
                return Collections.singletonList(candidate);
            }
            explanationBuilder.candidateDoesNotMatchAttributes(candidate, requested);
            return ImmutableList.of();
        }

        List<T> candidateList = (candidates instanceof List) ? Cast.uncheckedCast(candidates) : ImmutableList.copyOf(candidates);

        // Often times, collections of candidates will themselves differ even though their attributes are the same.
        // Disambiguating two different candidate lists which map to the same attribute lists in reality performs
        // the same work, so instead we cache disambiguation results based on the attributes being disambiguated.
        // The result of this is a list of indices into the original candidate list from which the
        // attributes-to-disambiguate are derived. When retrieving a result from the cache, we use the resulting
        // indices to index back into the original candidates list.
        CachedQuery query = CachedQuery.from(requested, candidateList);
        int[] indices = cachedQueries.get(query);
        return CachedQuery.getMatchesFromCandidateIndices(indices, candidateList);
    }

    private int[] doMatchMultipleCandidates(CachedQuery key) {
        AttributeMatchingExplanationBuilder explanationBuilder = AttributeMatchingExplanationBuilder.logging();
        int[] matches = new MultipleCandidateMatcher<>(schema, key.candidates, key.requestedAttributes, explanationBuilder).getMatches();
        LOGGER.debug("Selected matches {} from candidates {} for {}", Ints.asList(matches), key.candidates, key.requestedAttributes);
        return matches;
    }

    private static class CachedQuery {
        private final ImmutableAttributes requestedAttributes;
        private final List<ImmutableAttributes> candidates;
        private final int hashCode;

        private CachedQuery(ImmutableAttributes requestedAttributes, List<ImmutableAttributes> candidates) {
            this.requestedAttributes = requestedAttributes;
            this.candidates = candidates;
            this.hashCode = computeHashCode(requestedAttributes, candidates);
        }

        private static int computeHashCode(ImmutableAttributes requestedAttributes, List<ImmutableAttributes> candidates) {
            int hash = requestedAttributes.hashCode();
            for (ImmutableAttributes candidate : candidates) {
                hash = 31 * hash + candidate.hashCode();
            }
            return hash;
        }

        public static <T extends HasAttributes> CachedQuery from(ImmutableAttributes requestedAttributes, List<T> candidates) {
            List<ImmutableAttributes> attributes = new ArrayList<>(candidates.size());
            for (T candidate : candidates) {
                attributes.add(((AttributeContainerInternal) candidate.getAttributes()).asImmutable());
            }
            return new CachedQuery(requestedAttributes, attributes);
        }

        private static <T extends HasAttributes> List<T> getMatchesFromCandidateIndices(int[] indices, List<? extends T> candidates) {
            if (indices.length == 0) {
                return Collections.emptyList();
            }

            List<T> matches = new ArrayList<>(indices.length);
            for (int index : indices) {
                matches.add(candidates.get(index));
            }

            return matches;
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
                requestedAttributes.equals(that.requestedAttributes) &&
                candidates.equals(that.candidates);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return "CachedQuery{" +
                "requestedAttributes=" + requestedAttributes +
                ", candidates=" + candidates +
                '}';
        }
    }

    private static class MatchingCandidateCacheKey {
        private final ImmutableAttributes candidate;
        private final ImmutableAttributes requested;
        private final int hashCode;

        public MatchingCandidateCacheKey(ImmutableAttributes candidate, ImmutableAttributes requested) {
            this.candidate = candidate;
            this.requested = requested;
            this.hashCode = 31 * candidate.hashCode() + requested.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MatchingCandidateCacheKey cacheKey = (MatchingCandidateCacheKey) o;
            return candidate.equals(cacheKey.candidate) && requested.equals(cacheKey.requested);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
