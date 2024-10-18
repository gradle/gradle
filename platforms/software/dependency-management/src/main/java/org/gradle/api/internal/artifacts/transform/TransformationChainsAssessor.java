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

import com.google.common.collect.Iterables;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder;
import org.gradle.internal.component.resolution.failure.transform.TransformationChainData.TransformationChainFingerprint;
import org.gradle.internal.component.resolution.failure.transform.TransformedVariantConverter;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Responsible for assessing a group of transformation chains, finding those that match a
 * set of target attributes, and disambiguating between which matches represent
 * truly distinct chains, and are mutually compatible.
 * <p>
 * This class is not meant to take any action on the result, but just to handle separating
 * each chain into the appropriate bucket.
 */
@ServiceScope(Scope.Build.class)
public final class TransformationChainsAssessor {
    private final ProviderFactory providerFactory;
    private final TransformedVariantConverter converter = new TransformedVariantConverter();
    private final AttributeMatchingExplanationBuilder explanationBuilder = AttributeMatchingExplanationBuilder.logging();

    @Inject
    public TransformationChainsAssessor(ProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    /**
     * Assessed a given list of candidate transformation chains by finding which chains match to the target attributes
     * and disambiguating which of those matches are mutually compatible and which are truly distinct chains.
     *
     * @param attributeMatcher the attribute matcher to use to determine compatible results with the requested component
     * @param candidateChains the candidate transformation chains to disambiguate
     * @param targetAttributes requested target attributes
     * @return result data containing disambiguation knowledge
     */
    public AssessedTransformChains assessCandidates(
        AttributeMatcher attributeMatcher,
        List<TransformedVariant> candidateChains,
        ImmutableAttributes targetAttributes
    ) {
        List<TransformedVariant> matchingChains = attributeMatcher.matchMultipleCandidates(candidateChains, targetAttributes, explanationBuilder);
        return new AssessedTransformChains(
            candidateChains,
            targetAttributes,
            providerFactory.provider(() -> {
                Map<TransformationChainFingerprint, List<TransformedVariant>> chainsByFingerprint = new LinkedHashMap<>(candidateChains.size());
                for (TransformedVariant chain : matchingChains) {
                    TransformationChainFingerprint fingerprint = converter.convert(chain).fingerprint();
                    chainsByFingerprint.computeIfAbsent(fingerprint, f -> new ArrayList<>()).add(chain);
                }
                return chainsByFingerprint;
            }),
            providerFactory.provider(() -> {
                Set<List<TransformedVariant>> mutuallyCompatibleChains = new LinkedHashSet<>(candidateChains.size());
                for (TransformedVariant chain : matchingChains) {
                    List<TransformedVariant> compatibleChains = findCompatibleChains(attributeMatcher, mutuallyCompatibleChains, chain);
                    compatibleChains.add(chain);
                }
                return mutuallyCompatibleChains;
            }));
    }

    private static List<TransformedVariant> findCompatibleChains(AttributeMatcher matcher, Set<List<TransformedVariant>> compatibleChainGroups, TransformedVariant candidate) {
        for (List<TransformedVariant> currentGroup : compatibleChainGroups) {
            if (matcher.areMutuallyCompatible(currentGroup.get(0).getAttributes(), candidate.getAttributes())) {
                return currentGroup;
            }
        }

        List<TransformedVariant> newGroup = new ArrayList<>();
        compatibleChainGroups.add(newGroup);
        return newGroup;
    }

    /**
     * Represents information about a set of related transformation chains that all
     * satisfy an attribute matching request.
     * <p>
     * Immutable data class.  All included chains should produce results that are compatible
     * with the given target attributes (but may or may not be <strong>mutually compatible</strong>
     * with each other).  If more than one chain is present, it may or may not be truly distinct
     * all the other chains.  All matching chains should be of equal length.
     * <p>
     * All transformation chains contained anywhere within a single instance should have the same length.
     */
    public static final class AssessedTransformChains {
        private final List<TransformedVariant> candidateChains;

        /**
         * Each value in this map represents a group of chains that produce the same fingerprint,
         * which means they represent the same transformations applied in a different sequence.
         */
        private final Provider<Map<TransformationChainFingerprint, List<TransformedVariant>>> chainsByFingerprint;

        /**
         * Each value in this set represents a group of chains that produce compatible final sets
         * of attributes when applied to some (not necessarily the same) source variant.
         */
        private final Provider<Set<List<TransformedVariant>>> compatibleChainsGroups;

        private final ImmutableAttributes targetAttributes;

        private AssessedTransformChains(List<TransformedVariant> candidateChains, final ImmutableAttributes targetAttributes, Provider<Map<TransformationChainFingerprint, List<TransformedVariant>>> chainsByFingerprint, Provider<Set<List<TransformedVariant>>> compatibleChains) {
            this.candidateChains = candidateChains;
            this.targetAttributes = targetAttributes;
            this.chainsByFingerprint = chainsByFingerprint;
            this.compatibleChainsGroups = compatibleChains;
        }

        /**
         * Checks if there are any matches present in this result at all.
         *
         * @return {@code true} if so; {@code false} otherwise
         */
        public boolean hasAnyMatches() {
            return !compatibleChainsGroups.get().isEmpty();
        }

        /**
         * Return the single, truly distinct matching chain, if one exists.
         *
         * @return single truly distinct transformation chain in this result set if one exists; else {@link Optional#empty()}
         */
        public Optional<TransformedVariant> getSingleDistinctMatchingChain() {
            if (chainsByFingerprint.get().size() == 1) {
                TransformationChainFingerprint onlyFingerprint = Iterables.getOnlyElement(chainsByFingerprint.get().keySet());
                List<TransformedVariant> chainsWithOnlyFingerprint = chainsByFingerprint.get().get(onlyFingerprint);
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
            return chainsByFingerprint.get().values().stream()
                .map(transformedVariants -> transformedVariants.get(0))
                .collect(Collectors.toList());
        }

        /**
         * Return the single group of mutually compatible chains within the matching candidates, if one exists.
         *
         * @return single group of mutually compatible matching candidates if one exists; else {@link Optional#empty()}
         */
        public Optional<List<TransformedVariant>> getSingleGroupOfCompatibleChains() {
            if (compatibleChainsGroups.get().size() == 1) {
                return Optional.of(Iterables.getOnlyElement(compatibleChainsGroups.get()));
            } else {
                return Optional.empty();
            }
        }

        public ImmutableAttributes getTargetAttributes() {
            return targetAttributes;
        }

        public List<TransformedVariant> getCandidateChains() {
            return candidateChains;
        }
    }
}
