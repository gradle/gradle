/*
 * Copyright 2017 the original author or authors.
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
import com.google.common.collect.Sets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.MultipleCandidatesResult;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * This is the heart of the attribute matching algorithm and is used whenever there are multiple candidates to choose from.
 * <p>
 * <ol>
 * <li>
 * For each candidate, check whether its attribute values are compatible (according to the {@link AttributeSelectionSchema)} with the values that were requested.
 * Any missing or extra attributes on the candidate are ignored at this point. If there are 0 or 1 compatible candidates after this, return that as the result.
 * </li>
 * <li>
 * If there are multiple candidates matching the requested values, check whether one of the candidates is a strict superset of all the others, i.e. it matched more
 * of the requested attributes than any other one. Missing or extra attributes don't count. If such a strict superset candidate exists, it is returned as the single match.
 * </li>
 * <li>
 * Otherwise continue with disambiguation. Disambiguation iterates through all attributes (both requested and extra ones brought in by the candidates) and presents the
 * different values to the {@link AttributeSelectionSchema}. The schema can declare a subset of these values as preferred. Candidates whose value is not in that subset
 * are rejected.
 * </li>
 * <li>
 * If we run out of candidates during this process, the intersection of preferred values is not satisfied by any of them, so there could be multiple valid choices.
 * In that case return all compatible candidates, as none of them is preferable over any other.
 * </li>
 * <li>
 * If there are one or more candidates left after disambiguation, return those.
 * </li>
 * </ol>
 * </p>
 *
 * <p>
 * Implementation notes:
 *
 * The data structure for this matching algorithm is a table, with one row for each candidate and one column for each attribute. The cells contain the values of the candidate
 * for the given attribute. The first row contains the requested values. This table is packed into a single flat array in order to reduce memory usage and increase data locality.
 *
 * The information which candidates are compatible and which candidates are still valid during disambiguation is kept in two {@link BitSet}s. The nth bit is set if the nth candidate
 * is compatible. The longest match is kept using two integers, one containing the length of the match, the other containing the index of the candidate that was the longest.
 *
 * The candidate values are put in the table and checked for compatibility at the same time. This could be further optimized by only allocating a small table first and reusing a row
 * if the candidate didn't match. In most cases only a small subset of candidates makes it through the initial matching, so allocating a smaller table could save a lot of memory in the
 * common case.
 * </p>
 */
class MultipleCandidateMatcher<T extends HasAttributes> {
    private final AttributeSelectionSchema schema;
    private final ImmutableAttributes requested;
    private final List<? extends T> candidates;

    private final Attribute<?>[] allAttributes;
    private final Object[] attributeValues;
    private final BitSet compatibleCandidates;

    private int candidateWithLongestMatch;
    private int lengthOfLongestMatch;

    private BitSet disambiguatedCandidates;

    MultipleCandidateMatcher(AttributeSelectionSchema schema, Collection<? extends T> candidates, ImmutableAttributes requested) {
        this.schema = schema;
        this.requested = requested;
        this.candidates = (candidates instanceof List) ? (List<? extends T>) candidates : ImmutableList.copyOf(candidates);
        this.allAttributes = schema.getAttributes().toArray(new Attribute<?>[0]);
        attributeValues = new Object[(1 + candidates.size()) * this.allAttributes.length];
        compatibleCandidates = new BitSet(candidates.size());
        compatibleCandidates.set(0, candidates.size());
    }

    public List<T> getMatches() {
        fillRequestedValues();
        findCompatibleCandidates();
        if (compatibleCandidates.cardinality() <= 1) {
            return getCandidates(compatibleCandidates);
        }
        if (longestMatchIsSuperSetOfAllOthers()) {
            return Collections.singletonList(candidates.get(candidateWithLongestMatch));
        }
        return disambiguateCompatibleCandidates();
    }

    private void fillRequestedValues() {
        for (int a = 0; a < allAttributes.length; a++) {
            Attribute<?> attribute = allAttributes[a];
            AttributeValue<?> attributeValue = requested.findEntry(attribute);
            setRequestedValue(a, attributeValue.isPresent() ? attributeValue.get() : null);
        }
    }

    private void findCompatibleCandidates() {
        for (int c = 0; c < candidates.size(); c++) {
            matchCandidate(c);
        }
    }

    private void matchCandidate(int c) {
        int matchLength = 0;
        ImmutableAttributes candidateAttributes = ((AttributeContainerInternal) candidates.get(c).getAttributes()).asImmutable();

        for (int a = 0; a < allAttributes.length; a++) {
            MatchResult result = recordAndMatchCandidateValue(c, a, candidateAttributes);
            if (result == MatchResult.NO_MATCH) {
                return;
            }
            if (result == MatchResult.MATCH) {
                matchLength++;
            }
        }

        if (matchLength > lengthOfLongestMatch) {
            lengthOfLongestMatch = matchLength;
            candidateWithLongestMatch = c;
        }
    }

    private MatchResult recordAndMatchCandidateValue(int c, int a, ImmutableAttributes candidateAttributes) {
        Object requestedValue = getRequestedValue(a);
        Attribute<?> attribute = allAttributes[a];
        AttributeValue<?> candidateValue = candidateAttributes.findEntry(attribute.getName());

        if (!candidateValue.isPresent()) {
            setCandidateValue(c, a, null);
            return MatchResult.MISSING;
        }

        Object coercedValue = candidateValue.coerce(attribute);
        setCandidateValue(c, a, coercedValue);
        if (requestedValue == null) {
            return MatchResult.EXTRA;
        }

        DefaultCompatibilityCheckResult<Object> details = new DefaultCompatibilityCheckResult<Object>(requestedValue, coercedValue);
        schema.matchValue(attribute, details);
        if (details.isCompatible()) {
            return MatchResult.MATCH;
        } else {
            compatibleCandidates.clear(c);
            return MatchResult.NO_MATCH;
        }
    }


    private boolean longestMatchIsSuperSetOfAllOthers() {
        for (int c = compatibleCandidates.nextSetBit(0); c >= 0; c = compatibleCandidates.nextSetBit(c + 1)) {
            if (c == candidateWithLongestMatch) {
                continue;
            }
            int lengthOfOtherMatch = 0;
            for (int a = 0; a < allAttributes.length; a++) {
                if (getRequestedValue(a) == null) {
                    continue;
                }
                if (getCandidateValue(c, a) == null) {
                    continue;
                }
                lengthOfOtherMatch++;
                if (getCandidateValue(candidateWithLongestMatch, a) == null) {
                    return false;
                }
            }
            if (lengthOfOtherMatch == lengthOfLongestMatch) {
                return false;
            }
        }
        return true;
    }


    private List<T> disambiguateCompatibleCandidates() {
        disambiguatedCandidates = new BitSet(candidates.size());
        disambiguatedCandidates.or(compatibleCandidates);

        for (int a = 0; a < allAttributes.length; a++) {
            disambiguateAttribute(a);

            if (disambiguatedCandidates.cardinality() == 0) {
                return getCandidates(compatibleCandidates);
            }
        }
        return getCandidates(disambiguatedCandidates);
    }

    private void disambiguateAttribute(int a) {
        Set<Object> candidateValues = getCandidateValues(a);
        if (candidateValues.size() == 1) {
            return;
        }

        Set<Object> matchedValues = Sets.newHashSetWithExpectedSize(candidateValues.size());
        MultipleCandidatesResult<Object> result = new DefaultCandidateResult<T>(candidateValues, getRequestedValue(a), matchedValues);
        schema.disambiguate(allAttributes[a], result);
        if (result.hasResult()) {
            removeCandidatesWithValueNotIn(a, matchedValues);
        }
    }

    private Set<Object> getCandidateValues(int a) {
        Set<Object> candidateValues = Sets.newHashSetWithExpectedSize(compatibleCandidates.cardinality());
        for (int c = compatibleCandidates.nextSetBit(0); c >= 0; c = compatibleCandidates.nextSetBit(c + 1)) {
            candidateValues.add(getCandidateValue(c, a));
        }
        return candidateValues;
    }

    private void removeCandidatesWithValueNotIn(int a, Set<Object> matchedValues) {
        for (int c = compatibleCandidates.nextSetBit(0); c >= 0; c = compatibleCandidates.nextSetBit(c + 1)) {
            if (!matchedValues.contains(getCandidateValue(c, a))) {
                disambiguatedCandidates.clear(c);
            }
        }
    }

    private List<T> getCandidates(BitSet liveSet) {
        if (liveSet.cardinality() == 0) {
            return Collections.emptyList();
        }
        if (liveSet.cardinality() == 1) {
            return Collections.singletonList(this.candidates.get(liveSet.nextSetBit(0)));
        }
        ImmutableList.Builder<T> builder = ImmutableList.builder();
        for (int c = liveSet.nextSetBit(0); c >= 0; c = liveSet.nextSetBit(c + 1)) {
            builder.add(this.candidates.get(c));
        }
        return builder.build();
    }

    private void setRequestedValue(int a, Object value) {
        attributeValues[a] = value;
    }

    private Object getRequestedValue(int a) {
        return attributeValues[a];
    }

    private void setCandidateValue(int c, int a, Object value) {
        attributeValues[getValueIndex(c, a)] = value;
    }

    private Object getCandidateValue(int c, int a) {
        return attributeValues[getValueIndex(c, a)];
    }

    private int getValueIndex(int c, int a) {
        return (1 + c) * allAttributes.length + a;
    }

    private enum MatchResult {
        MATCH,
        MISSING,
        EXTRA,
        NO_MATCH
    }
}
