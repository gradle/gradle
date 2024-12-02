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
 * <p>
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
            return Optional.of(candidateChains.get(0));
        } else if (candidateChains.size() > 1) {
            TransformationChainsDisambiguator transformationChainsDisambiguator = new TransformationChainsDisambiguator(failureHandler, producer, targetAttributes, attributeMatcher, candidateChains);
            return transformationChainsDisambiguator.disambiguate();
        } else {
            return Optional.empty();
        }
    }
}
