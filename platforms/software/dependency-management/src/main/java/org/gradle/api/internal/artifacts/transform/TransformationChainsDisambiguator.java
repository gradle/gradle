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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException;
import org.gradle.internal.component.resolution.failure.transform.TransformationChainData;
import org.gradle.internal.component.resolution.failure.transform.TransformedVariantConverter;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.lazy.Lazy;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Disambiguates a set of related transformation chains that all satisfy an attribute matching request.
 * <p>
 * This class accomplishes this goal by internally assessing the given chains to separate truly
 * distinct chains with unique fingerprints using a lazily computed field ({@link #preferredChainsByFingerprint}).
 * This internal assessment can be used to determine whether there is any ambiguity in the given chains.
 * <p>
 * It reports any ambiguity failures to the given {@link ResolutionFailureHandler}.
 * <p>
 * Requirements:
 * <ul>
 *     <li>All chains to assess <strong>MUST</strong>> produce results that are compatible with the target attributes used by the given {@link #attributeMatcher}.</li>
 *     <li>Chains may or may not be <strong>MUTUALLY COMPATIBLE</strong> with each other.</li>
 *     <li>If more than one chain is present, they <strong>may or may NOT</strong> be truly distinct (by fingerprint)</li>
 *     <li>All chains to assess <strong>MUST</strong> be of equal length.</li>
 * </ul>
 */
/* package */ final class TransformationChainsDisambiguator {
    private final ResolutionFailureHandler failureHandler;
    private final ResolvedVariantSet producer;
    private final ImmutableAttributes targetAttributes;
    private final AttributeMatcher attributeMatcher;

    /**
     * The preferred matching chains are the best matches of the chains supplied to the constructor for assessment.
     * Those chains are all <strong>COMPATIBLE</strong> with the target attributes, but may not all be <strong>EXACT</strong> matches,
     * which would be preferred.
     */
    private final List<TransformedVariant> preferredChains;

    /**
     * Each value in this lazily computed map contains an arbitrary representative of each group of transformation chains
     * within the preferred chains that produce the same fingerprint.  Each set thus represents the <strong>SAME</strong>
     * transformations applied in <strong>ANY</strong> sequence.
     * <p>
     * Fingerprinting is an expensive operation, so this map is computed only when needed.
     */
    private final Lazy<LinkedHashMap<TransformationChainData.TransformationChainFingerprint, TransformedVariant>> preferredChainsByFingerprint;

    /**
     * Create an assessment of a given list of transformation chains by fingerprinting those chains
     * to disambiguate which are mutually compatible and which are truly distinct.
     * <p>
     * All chains must be of the same length, and should all be compatible with a single set
     * of target attributes (which is not supplied to this class).  These requirements are
     * <strong>NOT</strong> verified by this constructor.
     *
     * @param failureHandler resolution failure handler to use to report any unresolvable ambiguity
     * @param producer the resolved variant set that produced the chains to assess
     * @param targetAttributes the attributes we are aiming to match via a transformation chain
     * @param attributeMatcher the attribute matcher to use to determine compatible results
     * @param chainsToAssess the candidate transformation chains to disambiguate
     */
    public TransformationChainsDisambiguator(ResolutionFailureHandler failureHandler, ResolvedVariantSet producer, ImmutableAttributes targetAttributes, AttributeMatcher attributeMatcher, List<TransformedVariant> chainsToAssess) {
        this.failureHandler = failureHandler;
        this.producer = producer;
        this.targetAttributes = targetAttributes;
        this.attributeMatcher = attributeMatcher;

        this.preferredChains = attributeMatcher.matchMultipleCandidates(chainsToAssess, targetAttributes);
        this.preferredChainsByFingerprint = Lazy.unsafe().of(() -> {
            TransformedVariantConverter transformedVariantConverter = new TransformedVariantConverter();

            // Fingerprint all preferred chains to build a map from each unique fingerprint -> all preferred chains with that fingerprint
            LinkedHashMap<TransformationChainData.TransformationChainFingerprint, TransformedVariant> result = new LinkedHashMap<>(preferredChains.size());
            preferredChains.forEach(chain -> {
                TransformationChainData.TransformationChainFingerprint fingerprint = transformedVariantConverter.convert(chain).fingerprint();
                result.putIfAbsent(fingerprint, chain);
            });

            return result;
        });
    }

    /**
     * Given a set of potential transformation chains, attempts to reduce the set to a single, unambiguous, compatible candidate.
     * <p>
     * If this isn't possible because there are multiple compatible matches, this checks if they are truly distinct,
     * and not just re-sequencings of the same chain.  If they are <strong>NOT</strong> distinct, it arbitrarily
     * returns one.
     * <p>
     * If multiple, compatible, truly distinct matches exist, we'll warn (for now), but this behavior is
     * deprecated.  As of Gradle 9.0, this will also fail.
     *
     * @return single, unambiguous, preferred chain for use, selected as described above
     */
    public Optional<TransformedVariant> disambiguate() {
        // If only a single preferred chain was found, then the ambiguity
        // was due to multiple compatible chains, containing only one EXACT match.  We will use the exact match
        // and can return early, skipping any fingerprinting work.
        if (preferredChains.size() == 1) {
            return Optional.of(preferredChains.get(0));
        }

        // Multiple unique fingerprints in the preferred chains is a problem, however, we will not fail the build yet.
        if (preferredChainsByFingerprint.get().size() > 1) {
            // To maintain behavior (for now), if the multiple matches are COMPATIBLE we will only emit a deprecation,
            // as this is what Gradle used to do.  In Gradle 9, we can remove this inner if and throw immediately.
            if (allPreferredChainsAreCompatible()) {
                warnThatMultipleDistinctChainsAreAvailable(producer, targetAttributes, failureHandler, getDistinctPreferredChainRepresentatives());
                return getArbitraryPreferredMatchingChain();
            } else {
                // At this point, there are multiple distinct chains that are not compatible with each other.  This is right out.
                // It has never been allowed and fails the build.  The error message should report one representative of each
                // distinct chain, so that the author can understand what's happening here and correct it.
                throw failureHandler.ambiguousArtifactTransformsFailure(producer, targetAttributes, getDistinctPreferredChainRepresentatives());
            }
        } else {
            return getArbitraryPreferredMatchingChain();
        }
    }

    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    private void warnThatMultipleDistinctChainsAreAvailable(ResolvedVariantSet targetVariantSet, ImmutableAttributes requestedAttributes, ResolutionFailureHandler failureHandler, Collection<TransformedVariant> trulyDistinctChains) {
        // Yes, building this context is ugly, but there's no sense extracting the formatting logic if this is going away in Gradle 9, just reuse it for now
        String context;
        try {
            throw failureHandler.ambiguousArtifactTransformsFailure(targetVariantSet, requestedAttributes, trulyDistinctChains);
        } catch (AbstractResolutionFailureException e) {
            int startIdx = e.getMessage().indexOf("Found multiple transformation chains");
            context = System.lineSeparator() + e.getMessage().substring(startIdx) + System.lineSeparator();
        }

        DeprecationLogger.deprecateBehaviour("There are multiple distinct artifact transformation chains of the same length that would satisfy this request.")
            .withAdvice("Remove one or more registered transforms, or add additional attributes to them to ensure only a single valid transformation chain exists.")
            .withContext(context)
            .willBecomeAnErrorInGradle9()
            .withUpgradeGuideSection(8, "deprecated_ambiguous_transformation_chains")
            .nagUser();
    }

    /**
     * Return an arbitrary preferred chain, if one exists.
     * <p>
     * It remains important to use the <strong>LAST</strong> compatible match, as this was the previous behavior,
     * and is tested in {@code DisambiguateArtifactTransformIntegrationTest}.
     *
     * @return first preferred transformation chain in this result set if one exists; else {@link Optional#empty()}
     */
    private Optional<TransformedVariant> getArbitraryPreferredMatchingChain() {
        return !preferredChains.isEmpty() ? Optional.ofNullable(preferredChains.get(preferredChains.size() - 1)) : Optional.empty();
    }

    /**
     * Check if all preferred chains are mutually compatible with each other.
     * <p>
     * This does <strong>NOT</strong>> trigger fingerprinting.
     *
     * @return {@code true} if all preferred chains are mutually compatible; {@code false} otherwise
     */
    private boolean allPreferredChainsAreCompatible() {
        TransformedVariant compareTo = preferredChains.get(0); // Compatibility is transitive, so arbitrarily pick the first chain to compare against all others
        return preferredChains.stream()
            .filter(current -> current != compareTo) // Skip the first chain, as it's the one we're comparing against, and comparisons are expensive
            .allMatch(current -> attributeMatcher.areMutuallyCompatible(compareTo.getAttributes(), current.getAttributes()));
    }

    /**
     * Return a representative of each fingerprint group.
     * <p>
     * For example, if, A, B, C and D each represent distinct sets of attributes, then chains
     * of A -> B -> C -> D and A -> C -> B -> D are merely re-sequencings of the same transformations and
     * are <strong>NOT</strong> truly distinct.  This is fine, as Gradle can just arbitrarily pick one,
     * since the different order that steps are run is <strong>PROBABLY</strong> not meaningful - the
     * <strong>SAME</strong> work will be done (though the order may still have efficiency implications).
     * These chains have the same fingerprint.
     * <p>
     * However, chains of A -> B -> C and A -> D -> C are <strong>NOT</strong> the same!  Even though they end up
     * producing a C with the same exact attributes, they represent <strong>DIFFERENT</strong> work being done, and Gradle
     * has no way to determine which path is better to select.  This
     * choice likely <strong>WILL</strong> have an impact, as different transforms could have very different performance
     * characteristics, and because the author likely expects one path to be taken, but won't know if
     * it was or wasn't.  These chains have different fingerprints.
     * <p>
     * So within {@link #preferredChains}, each unique fingerprint is associated with a list containing
     * potentially multiple chains.  The method will (arbitrarily) select the first such chain with a particular
     * fingerprint encountered within that list.  As each group of chains with the same fingerprint produces
     * the same result, all chains in that group are all necessarily mutually compatible.
     * <p>
     * This triggers fingerprinting.
     *
     * @return one arbitrary chain from each distinct set of chains with an identical fingerprint within the preferred chains
     */
    private Collection<TransformedVariant> getDistinctPreferredChainRepresentatives() {
        return preferredChainsByFingerprint.get().values();
    }
}
