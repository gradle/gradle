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

import com.google.common.collect.Sets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * This is the heart of the attribute matching algorithm and is used whenever there are multiple candidates to choose from.
 * <p>
 * <ol>
 * <li>
 * For each candidate, check whether its attribute values are compatible (according to the {@link AttributeSelectionSchema}) with the values that were requested.
 * Any missing or extra attributes on the candidate are ignored at this point. If there are 0 or 1 compatible candidates after this, return that as the result.
 * </li>
 * <li>
 * If there are multiple candidates matching the requested values, check whether one of the candidates is a strict superset of all the others, i.e. it matched more
 * of the requested attributes than any other one. Missing or extra attributes don't count. If such a strict superset candidate exists, it is returned as the single match.
 * </li>
 * <li>
 * Otherwise continue with disambiguation. Disambiguation iterates through the attributes and presents the different values to the {@link AttributeSelectionSchema}.
 * The schema can declare a subset of these values as preferred. Candidates whose value is not in that subset are rejected. If a single candidate remains after
 * disambiguating the requested attributes, this candidate is returned. Otherwise disambiguation continues with extra attributes.
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
 * <p>For matching and disambiguating the requested values, we keep a table of candidate values to avoid recomputing them. The table has one row for each candidate and one column for each attribute.
 * The cells contain the values of the candidate for the given attribute. This table is packed into a single flat array in order to reduce memory usage and increase data locality.
 *
 * <p>The information which candidates are compatible and which candidates are still valid during disambiguation is kept in two {@link BitSet}s. The nth bit is set if the nth candidate
 * is compatible. The longest match is kept using two integers, one containing the length of the match, the other containing the index of the candidate that was the longest.
 *
 * </p>
 */
class MultipleCandidateMatcher<T extends HasAttributes> {
    private final AttributeSelectionSchema schema;
    private final ImmutableAttributes requested;
    private final List<? extends T> candidates;
    private final ImmutableAttributes[] candidateAttributeSets;
    private final AttributeMatchingExplanationBuilder explanationBuilder;

    private final List<Attribute<?>> requestedAttributes;
    private final BitSet compatible;

    /**
     * Cache of requested attribute values.
     */
    private final Object[] requestedAttributeValues;

    /**
     * Cache of candidate attribute values for each requested attribute. Initialized by {@link #findCompatibleCandidates()}.
     *
     * @see #setCandidateValue(int, int, Object)
     * @see #getCandidateValue(int, int)
     */
    private final Object[] candidateValues;

    private int candidateWithLongestMatch;
    private int lengthOfLongestMatch;

    private BitSet remaining;

    <E extends T> MultipleCandidateMatcher(AttributeSelectionSchema schema, List<E> candidates, ImmutableAttributes requested, AttributeMatchingExplanationBuilder explanationBuilder) {
        this.schema = schema;
        this.candidates = candidates;
        this.requested = requested;
        this.explanationBuilder = explanationBuilder;

        this.requestedAttributes = requested.keySet().asList();
        this.requestedAttributeValues = getRequestedValues(requestedAttributes, requested);

        this.candidateAttributeSets = getCandidateAttributeSets(this.candidates);
        this.candidateValues = new Object[candidates.size() * requestedAttributes.size()];

        this.compatible = new BitSet(candidates.size());
        compatible.set(0, candidates.size());
    }

    public int[] getMatches() {
        findCompatibleCandidates();
        if (compatible.cardinality() <= 1) {
            return getCandidates(compatible);
        }
        if (longestMatchIsSuperSetOfAllOthers()) {
            T o = candidates.get(candidateWithLongestMatch);
            explanationBuilder.candidateIsSuperSetOfAllOthers(o);
            return new int[] {candidateWithLongestMatch};
        }
        return disambiguateCompatibleCandidates();
    }

    private static Object[] getRequestedValues(List<Attribute<?>> requestedAttributes, ImmutableAttributes requested) {
        Object[] requestedAttributeValues = new Object[requestedAttributes.size()];
        for (int a = 0; a < requestedAttributes.size(); a++) {
            Attribute<?> attribute = requestedAttributes.get(a);
            AttributeValue<?> attributeValue = requested.findEntry(attribute);
            requestedAttributeValues[a] = attributeValue.isPresent() ? attributeValue.get() : null;
        }
        return requestedAttributeValues;
    }

    private static ImmutableAttributes[] getCandidateAttributeSets(List<? extends HasAttributes> candidates) {
        ImmutableAttributes[] candidateAttributeSets = new ImmutableAttributes[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            candidateAttributeSets[i] = ((AttributeContainerInternal) candidates.get(i).getAttributes()).asImmutable();
        }
        return candidateAttributeSets;
    }

    private void findCompatibleCandidates() {
        if (requested.isEmpty()) {
            // Avoid iterating on candidates if there's no requested attribute
            return;
        }
        for (int c = 0; c < candidates.size(); c++) {
            matchCandidate(c);
        }
    }

    private void matchCandidate(int c) {
        int matchLength = 0;

        for (int a = 0; a < requestedAttributes.size(); a++) {
            MatchResult result = recordAndMatchCandidateValue(c, a);
            if (result == MatchResult.NO_MATCH) {
                compatible.clear(c);
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

    private MatchResult recordAndMatchCandidateValue(int c, int a) {
        Object requestedValue = requestedAttributeValues[a];
        Attribute<?> attribute = requestedAttributes.get(a);
        AttributeValue<?> candidateValue = candidateAttributeSets[c].findEntry(attribute.getName());

        if (!candidateValue.isPresent()) {
            setCandidateValue(c, a, null);
            explanationBuilder.candidateAttributeMissing(candidates.get(c), attribute, requestedValue);
            return MatchResult.MISSING;
        }

        Object coercedValue = candidateValue.coerce(attribute);
        setCandidateValue(c, a, coercedValue);

        if (schema.matchValue(attribute, requestedValue, coercedValue)) {
            return MatchResult.MATCH;
        }
        explanationBuilder.candidateAttributeDoesNotMatch(candidates.get(c), attribute, requestedValue, candidateValue);
        return MatchResult.NO_MATCH;
    }

    private boolean longestMatchIsSuperSetOfAllOthers() {
        for (int c = compatible.nextSetBit(0); c >= 0; c = compatible.nextSetBit(c + 1)) {
            if (c == candidateWithLongestMatch) {
                continue;
            }
            int lengthOfOtherMatch = 0;
            for (int a = 0; a < requestedAttributes.size(); a++) {
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

    private int[] disambiguateCompatibleCandidates() {
        remaining = new BitSet(candidates.size());
        remaining.or(compatible);

        disambiguateWithRequestedAttributeValues();
        if (remaining.cardinality() == 0) {
            return getCandidates(compatible);
        } else if (remaining.cardinality() == 1) {
            return getCandidates(remaining);
        }

        Attribute<?>[] extraAttributes = schema.collectExtraAttributes(candidateAttributeSets, requested);
        if (remaining.cardinality() > 1) {
            disambiguateWithExtraAttributes(extraAttributes);
        }
        if (remaining.cardinality() > 1) {
            disambiguateWithRequestedAttributeKeys(extraAttributes);
        }
        return remaining.cardinality() == 0 ? getCandidates(compatible) : getCandidates(remaining);
    }

    private void disambiguateWithRequestedAttributeKeys(Attribute<?>[] extraAttributes) {
        if (requestedAttributes.isEmpty()) {
            return;
        }
        for (Attribute<?> extraAttribute : extraAttributes) {
            // We consider only extra attributes which are NOT on every candidate:
            // Because they are EXTRA attributes, we consider that a
            // candidate which does NOT provide this value is a better match
            int candidateCount = candidateAttributeSets.length;
            BitSet any = new BitSet(candidateCount);
            for (int c = 0; c < candidateCount; c++) {
                ImmutableAttributes candidateAttributeSet = candidateAttributeSets[c];
                if (candidateAttributeSet.findEntry(extraAttribute.getName()).isPresent()) {
                    any.set(c);
                }
            }
            if (any.cardinality() > 0 && any.cardinality() != candidateCount) {
                // there is at least one candidate which does NOT provide this attribute
                remaining.andNot(any);
                if (remaining.cardinality() == 0) {
                    // there are no left candidate, do not bother checking other attributes
                    break;
                }
            }
        }
    }

    private void disambiguateWithRequestedAttributeValues() {
        // We need to take the existing requested attributes and sort them in "precedence" order
        // This returns a structure that tells us the order of requestedAttributes by their index in
        // requestedAttributes.
        //
        // If the requested attributes are [ A, B, C ]
        // If the attribute precedence is [ C, A ]
        // The indices are [ A: 0, B: 1, C: 2 ]
        // The sorted indices are [ 2, 0 ]
        // The unsorted indices are [ 1 ]
        //
        final AttributeSelectionSchema.PrecedenceResult precedenceResult = schema.orderByPrecedence(requested.keySet());

        for (int a : precedenceResult.getSortedOrder()) {
            disambiguateRequestedAttribute(a);
            if (remaining.cardinality() == 0) {
                return;
            } else if (remaining.cardinality() == 1) {
                // If we're down to one candidate and the attribute has a known precedence,
                // we can stop now and choose this candidate as the match.
                return;
            }
        }
        // If the attribute does not have a known precedence, then we cannot stop
        // until we've disambiguated all of the attributes.
        for (int a : precedenceResult.getUnsortedOrder()) {
            disambiguateRequestedAttribute(a);
            if (remaining.cardinality() == 0) {
                return;
            }
        }
    }

    private void disambiguateRequestedAttribute(int a) {
        Set<Object> candidateValues = getCandidateValues(compatible, c -> getCandidateValue(c, a));
        if (candidateValues.size() <= 1) {
            return;
        }

        Set<Object> matches = schema.disambiguate(requestedAttributes.get(a), requestedAttributeValues[a], candidateValues);
        if (matches != null && matches.size() < candidateValues.size()) {
            // Remove any candidates which do not satisfy the disambiguation rule.
            for (int c = remaining.nextSetBit(0); c >= 0; c = remaining.nextSetBit(c + 1)) {
                if (!matches.contains(getCandidateValue(c, a))) {
                    remaining.clear(c);
                }
            }
        }
    }

    /**
     * @param attribute The attribute to disambiguate.
     * @param candidates The set of candidate attribute sets to extract values from during disambiguation.
     */
    private void disambiguateExtraAttribute(Attribute<?> attribute, BitSet candidates) {
        Set<Object> candidateValues = getCandidateValues(candidates, c -> getCandidateValue(c, attribute));

        // We continue disambiguation for attributes with only one value since we may have some candidates with
        // no value for this attribute in addition to those with a value. Since we do not include `null` in the
        // candidate values, we must continue to execute the disambiguation rule in case the rule specifies a
        // default value and thus removes the candidates which do not have a value for this attribute.
        if (candidateValues.size() < 1) {
            return;
        }

        Set<Object> matches = schema.disambiguate(attribute, null, candidateValues);
        if (matches != null) {
            // Remove any candidates which do not satisfy the disambiguation rule.
            for (int c = remaining.nextSetBit(0); c >= 0; c = remaining.nextSetBit(c + 1)) {
                if (!matches.contains(getCandidateValue(c, attribute))) {
                    remaining.clear(c);
                }
            }
        }
    }

    /**
     * Given each compatible candidate, determine all values corresponding to some attribute, as defined by {@code candidateValueFetcher}.
     *
     * @param candidateValueFetcher A function which returns the candidate value for some
     *      attribute for the candidate at the provided index.
     *
     * @return A new set containing all compatible values for some attribute.
     */
    private static Set<Object> getCandidateValues(BitSet compatible, IntFunction<Object> candidateValueFetcher) {
        // It's often the case that all the candidate values are the same. In this case, we avoid
        // the creation of a set, and just iterate until we find a different value. Then, only in
        // this case, we lazily initialize a set and collect all the candidate values.
        Set<Object> candidateValues = null;
        Object compatibleValue = null;
        boolean first = true;
        for (int c = compatible.nextSetBit(0); c >= 0; c = compatible.nextSetBit(c + 1)) {
            Object candidateValue = candidateValueFetcher.apply(c);
            if (candidateValue == null) {
                continue;
            }
            if (first) {
                // first match, just record the value. We can't use "null" as the candidate value may be null
                compatibleValue = candidateValue;
                first = false;
            } else if (compatibleValue != candidateValue || candidateValues != null) {
                // we see a different value, or the set already exists, in which case we initialize
                // the set if it wasn't done already, and collect all values.
                if (candidateValues == null) {
                    candidateValues = Sets.newHashSetWithExpectedSize(compatible.cardinality());
                    candidateValues.add(compatibleValue);
                }
                candidateValues.add(candidateValue);
            }
        }
        if (candidateValues == null) {
            if (compatibleValue == null) {
                return Collections.emptySet();
            }
            return Collections.singleton(compatibleValue);
        }
        return candidateValues;
    }

    private void disambiguateWithExtraAttributes(Attribute<?>[] extraAttributes) {
        final AttributeSelectionSchema.PrecedenceResult precedenceResult = schema.orderByPrecedence(Arrays.asList(extraAttributes));

        for (int a : precedenceResult.getSortedOrder()) {
            disambiguateExtraAttribute(extraAttributes[a], remaining);
            if (remaining.cardinality() == 0) {
                return;
            } else if (remaining.cardinality() == 1) {
                // If we're down to one candidate and the attribute has a known precedence,
                // we can stop now and choose this candidate as the match.
                return;
            }
        }

        // When disambiguating in unknown precedence order, we always use the same set of candidates
        // so that this step is not affected by the order in which we iterate.
        BitSet candidates = new BitSet();
        candidates.or(remaining);

        // If the attribute does not have a known precedence, then we cannot stop
        // until we've disambiguated all of the attributes.
        for (int a : precedenceResult.getUnsortedOrder()) {
            disambiguateExtraAttribute(extraAttributes[a], candidates);
            if (remaining.cardinality() == 0) {
                return;
            }
        }
    }

    private int[] getCandidates(BitSet liveSet) {
        if (liveSet.cardinality() == 0) {
            return new int[0];
        }
        if (liveSet.cardinality() == 1) {
            return new int[] {liveSet.nextSetBit(0)};
        }

        int i = 0;
        int[] result = new int[liveSet.cardinality()];
        for (int c = liveSet.nextSetBit(0); c >= 0; c = liveSet.nextSetBit(c + 1)) {
            result[i++] = c;
        }
        return result;
    }

    @Nullable
    private Object getCandidateValue(int c, Attribute<?> attribute) {
        AttributeValue<?> attributeValue = candidateAttributeSets[c].findEntry(attribute.getName());
        return attributeValue.isPresent() ? attributeValue.coerce(attribute) : null;
    }

    @Nullable
    private Object getCandidateValue(int c, int a) {
        return candidateValues[getValueIndex(c, a)];
    }

    private void setCandidateValue(int c, int a, @Nullable Object value) {
        candidateValues[getValueIndex(c, a)] = value;
    }

    private int getValueIndex(int c, int a) {
        return c * requestedAttributes.size() + a;
    }

    private enum MatchResult {
        MATCH,
        MISSING,
        NO_MATCH
    }
}
