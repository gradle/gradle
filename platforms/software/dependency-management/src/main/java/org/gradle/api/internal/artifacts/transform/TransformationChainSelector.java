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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException;
import org.gradle.internal.deprecation.DeprecationLogger;

import java.util.List;
import java.util.Optional;

/**
 * Responsible for selecting a suitable transformation chain for a request.
 *
 * A suitable chain produces a resulting variant that matches the set of target attributes.
 * It is also only suitable IFF there are no other possible chains which 1) match, 2) are
 * truly distinct chains, 3) and are mutually compatible.  If there are other chains that
 * satisfy these conditions, we have ambiguity, and selection fails.
 * <p>
 * This class is not meant to take any action on the resulting chain.  It only encapsulates
 * the logic for finding candidate chains and selecting the appropriate chain if possible.
 *
 * It reports any ambiguity failures to the given {@link ResolutionFailureHandler}.
 */
/* package */ final class TransformationChainSelector {
    private final ConsumerProvidedVariantFinder transformationChainFinder;
    private final ResolutionFailureHandler failureHandler;

    public TransformationChainSelector(ConsumerProvidedVariantFinder transformationChainFinder, ResolutionFailureHandler failureHandler) {
        this.transformationChainFinder = transformationChainFinder;
        this.failureHandler = failureHandler;
    }

    /**
     * Selects the transformation chain to use to satisfy a request.
     * <p>
     * This method uses the {@link ConsumerProvidedVariantFinder} to finds all matching chains that
     * would satisfy the request.  If there is a single result, it uses that one.  If there are multiple
     * results, it attempts to disambiguate them.  If there are none, it returns {@link Optional#empty()}.
     *
     * @return result of selection, as described
     */
    public Optional<TransformedVariant> selectTransformationChain(ResolvedVariantSet producer, ImmutableAttributes targetAttributes, AttributeMatcher attributeMatcher) {
        // It's important to note that this produces all COMPATIBLE chains, meaning it's MORE PERMISSIVE than it
        // needs to be.  For example, if libraryelements=classes is requested, and there are 2 chains available
        // that will result in variants that only differ in libraryelements=classes and libraryelements=jar, and
        // these are compatible attribute values, both are returned at this point, despite the exact match being clearly preferable.
        List<TransformedVariant> candidateChains = transformationChainFinder.findCandidateTransformationChains(producer.getCandidates(), targetAttributes);
        if (candidateChains.size() == 1) {
            return Optional.of(Iterables.getOnlyElement(candidateChains));
        } else if (candidateChains.size() > 1) {
            return disambiguateTransformationChains(producer, targetAttributes, attributeMatcher, candidateChains);
        } else {
            return Optional.empty();
        }
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
    private Optional<TransformedVariant> disambiguateTransformationChains(ResolvedVariantSet producer, ImmutableAttributes targetAttributes, AttributeMatcher attributeMatcher, List<TransformedVariant> candidateChains) {
        AssessedTransformationChains assessedChains = new AssessedTransformationChains(targetAttributes, attributeMatcher, candidateChains);

        // After assessing the candidate chains, if a single distinct chain found, then the ambiguity was due to re-sequencings of the same set of transforms.
        Optional<TransformedVariant> singleDistinctMatchingChain = assessedChains.getSingleDistinctMatchingChain();
        if (singleDistinctMatchingChain.isPresent()) {
            return singleDistinctMatchingChain;
        }

        //  At this point, we have real ambiguity.  There are more than one compatible matches with distinct fingerprints.
        // The build author needs to be notified and should address this ambiguity.
        List<TransformedVariant> singleGroupOfCompatibleChains = assessedChains.getSingleGroupOfCompatibleChains();
        if (!singleGroupOfCompatibleChains.isEmpty()) {
            // However, we will not necessarily fail the build just yet.  To maintain behavior (for now), we will not fail and
            // only emit a deprecation if the multiple matches are COMPATIBLE, as this is what the build used to do.  This can error in Gradle 9.
            warnThatMultipleDistinctChainsAreAvailable(producer, targetAttributes, failureHandler, assessedChains.getDistinctMatchingChainRepresentatives());
            return Optional.of(singleGroupOfCompatibleChains.get(singleGroupOfCompatibleChains.size() - 1)); // Important to use LAST compatible match, as this is the previous behavior, and is tests in DisambiguateArtifactTransformIntegrationTest
        }

        // At this point, there are multiple distinct chains that are not compatible with each other.  This is right out.
        // It has never been allowed and fails the build.  The error message should report one representative of each
        // distinct chain, so that the author can understand what's happening here and correct it.
        throw failureHandler.ambiguousArtifactTransformsFailure(producer, targetAttributes, assessedChains.getDistinctMatchingChainRepresentatives());
    }

    private void warnThatMultipleDistinctChainsAreAvailable(ResolvedVariantSet targetVariantSet, ImmutableAttributes requestedAttributes, ResolutionFailureHandler failureHandler, List<TransformedVariant> trulyDistinctChains) {
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
}
