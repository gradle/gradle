/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.internal.component.resolution.failure.transform.TransformationChainData;
import org.gradle.internal.component.resolution.failure.transform.TransformedVariantConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents information about a set of related transformation chains that all
 * satisfy an attribute matching request.
 * <p>
 * This assessment can be used to determine whether there is any ambiguity in the list of chains.
 * <p>
 * Immutable data class.  All included chains should produce results that are compatible
 * with the given target attributes (but may or may not be <strong>mutually compatible</strong>
 * with each other).  If more than one chain is present, it may or may not be truly distinct
 * from all the other chains.  All matching chains should be of equal length.
 */
/* package */ final class AssessedTransformationChains {
    /**
     * Each value in this map represents a group of transformation chains that produce the same fingerprint,
     * which means they represent the same transformations applied in a different sequence.
     */
    private final Map<TransformationChainData.TransformationChainFingerprint, List<TransformedVariant>> matchingChainsByFingerprint;
    private final AttributeMatcher attributeMatcher;

    /**
     * Create an assessment of a given list of transformation chains by fingerprinting those chains
     * to disambiguate which are mutually compatible and which are truly distinct.
     *
     * @param targetAttributes the attributes we are aiming to match via a transformation chain
     * @param attributeMatcher the attribute matcher to use to determine compatible results
     * @param chainsToAssess the candidate transformation chains to disambiguate (must all be same length)
     */
    public AssessedTransformationChains(ImmutableAttributes targetAttributes, AttributeMatcher attributeMatcher, List<TransformedVariant> chainsToAssess) {
        Preconditions.checkArgument(chainsToAssess.stream().map(c -> c.getTransformChain().length()).distinct().count() == 1, "a");

        // This is necessary as we may have multiple COMPATIBLE chains in the chainsToAssess list, but one (or more) may be preferable EXACT matches
        List<TransformedVariant> preferredMatchingChains = attributeMatcher.matchMultipleCandidates(chainsToAssess, targetAttributes);
        TransformedVariantConverter transformedVariantConverter = new TransformedVariantConverter();

        // Fingerprint all matching chains to build map from fingerPrint -> chains with that fingerprint
        this.matchingChainsByFingerprint = new LinkedHashMap<>(preferredMatchingChains.size());
        preferredMatchingChains.forEach(chain -> {
            TransformationChainData.TransformationChainFingerprint fingerprint = transformedVariantConverter.convert(chain).fingerprint();
            matchingChainsByFingerprint.computeIfAbsent(fingerprint, f -> new ArrayList<>()).add(chain);
        });

        this.attributeMatcher = attributeMatcher;
    }

    /**
     * Return the single, truly distinct matching chain, if one exists.
     * <p>
     * For example, chains of A -> B -> C -> D and A -> C -> B -> D are merely re-sequencings of the same chain and
     * are not truly distinct.  This is fine, Gradle will just arbitrarily pick one, as the different order
     * that steps are run is PROBABLY not meaningful - the SAME work will be done.
     *
     * @return single truly distinct transformation chain in this result set if one exists; else {@link Optional#empty()}
     */
    public Optional<TransformedVariant> getSingleDistinctMatchingChain() {
        if (matchingChainsByFingerprint.size() == 1) {
            TransformationChainData.TransformationChainFingerprint onlyFingerprint = Iterables.getOnlyElement(matchingChainsByFingerprint.keySet());
            List<TransformedVariant> chainsWithOnlyFingerprint = matchingChainsByFingerprint.get(onlyFingerprint);
            if (chainsWithOnlyFingerprint.size() == 1) {
                return Optional.of(chainsWithOnlyFingerprint.get(0));
            }
        }
        return Optional.empty();
    }

    /**
     * Each fingerprint is associated with a list containing potentially multiple chains, this will
     * return (arbitrarily) the first chain in each such list - these returned chains will represent
     * each unique fingerprint of chains within the matches used to produce this result.
     *
     * @return one arbitrary chain from each distinct set of chains within the matching candidates
     */
    public List<TransformedVariant> getDistinctMatchingChainRepresentatives() {
        return matchingChainsByFingerprint.values().stream()
            .map(transformedVariants -> transformedVariants.get(0))
            .collect(Collectors.toList());
    }

    /**
     * Return the single group of mutually compatible chains within all the matching candidates, if one exists.
     * <p>
     * For example, if matches contains chains of A -> B -> C and A -> D -> C this is NOT okay!  Even if they end up
     * producing a C with the same exact attributes, they represent DIFFERENT work being done, and Gradle
     * has no way to determine which is better to select and must make an arbitrary choice.  This
     * choice will likely have impact, as different transforms could have very different performance
     * characteristics, and because the author likely expects one path to be taken, but won't know if
     * it was or wasn't should Gradle arbitrarily pick one.  This would be a case of multiple groups of
     * compatible chains.
     *
     * @return list of single group of mutually compatible matching candidates if one exists; else empty list
     */
    public List<TransformedVariant> getSingleGroupOfCompatibleChains() {
        Set<List<TransformedVariant>> compatibilityGroups = new LinkedHashSet<>(); // Preserve ordering of chains within each compatibility group

        matchingChainsByFingerprint.values().stream()
            .flatMap(Collection::stream)
            .forEach(chain -> findCompatiblityGroup(attributeMatcher, compatibilityGroups, chain).add(chain));

        return compatibilityGroups.size() == 1 ? Iterables.getOnlyElement(compatibilityGroups) : Collections.emptyList();
    }

    private List<TransformedVariant> findCompatiblityGroup(AttributeMatcher matcher, Set<List<TransformedVariant>> compatibilityGroups, TransformedVariant toMatch) {
        for (List<TransformedVariant> currentGroup : compatibilityGroups) {
            if (matcher.areMutuallyCompatible(currentGroup.get(0).getAttributes(), toMatch.getAttributes())) {
                return currentGroup;
            }
        }

        List<TransformedVariant> newGroup = new ArrayList<>();
        compatibilityGroups.add(newGroup);
        return newGroup;
    }
}
