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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.Iterables;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.attributes.AttributeSchemaServices;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException;
import org.gradle.internal.component.resolution.failure.transform.TransformationChainData.TransformationFingerprint;
import org.gradle.internal.component.resolution.failure.transform.TransformedVariantConverter;
import org.gradle.internal.deprecation.DeprecationLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A {@link ArtifactVariantSelector} that uses attribute matching to select a matching set of artifacts.
 *
 * If no producer variant is compatible with the requested attributes, this selector will attempt to construct a chain of artifact
 * transforms that can produce a variant compatible with the requested attributes.
 *
 * An instance of {@link ResolutionFailureHandler} is injected in the constructor
 * to allow the caller to handle failures in a consistent manner as during graph variant selection.
 */
public class AttributeMatchingArtifactVariantSelector implements ArtifactVariantSelector {
    private final ImmutableAttributesSchema consumerSchema;
    private final TransformUpstreamDependenciesResolver dependenciesResolver;
    private final ConsumerProvidedVariantFinder consumerProvidedVariantFinder;
    private final AttributesFactory attributesFactory;
    private final AttributeSchemaServices attributeSchemaServices;
    private final TransformedVariantFactory transformedVariantFactory;
    private final ResolutionFailureHandler failureProcessor; // TODO: rename to failure handler
    private final TransformedVariantConverter transformedVariantConverter = new TransformedVariantConverter();

    AttributeMatchingArtifactVariantSelector(
        ImmutableAttributesSchema consumerSchema,
        TransformUpstreamDependenciesResolver dependenciesResolver,
        ConsumerProvidedVariantFinder consumerProvidedVariantFinder,
        AttributesFactory attributesFactory,
        AttributeSchemaServices attributeSchemaServices,
        TransformedVariantFactory transformedVariantFactory,
        ResolutionFailureHandler failureProcessor
    ) {
        this.consumerSchema = consumerSchema;
        this.dependenciesResolver = dependenciesResolver;
        this.consumerProvidedVariantFinder = consumerProvidedVariantFinder;
        this.attributesFactory = attributesFactory;
        this.attributeSchemaServices = attributeSchemaServices;
        this.transformedVariantFactory = transformedVariantFactory;
        this.failureProcessor = failureProcessor;
    }

    @Override
    public ResolvedArtifactSet select(ResolvedVariantSet producer, ImmutableAttributes requestAttributes, boolean allowNoMatchingVariants, ResolvedArtifactTransformer resolvedArtifactTransformer) {
        try {
            return doSelect(producer, allowNoMatchingVariants, resolvedArtifactTransformer, AttributeMatchingExplanationBuilder.logging(), requestAttributes);
        } catch (Exception t) {
            return new BrokenResolvedArtifactSet(failureProcessor.unknownArtifactVariantSelectionFailure(producer, requestAttributes, t));
        }
    }

    private ResolvedArtifactSet doSelect(ResolvedVariantSet producer, boolean allowNoMatchingVariants, ResolvedArtifactTransformer resolvedArtifactTransformer, AttributeMatchingExplanationBuilder explanationBuilder, ImmutableAttributes requestAttributes) {
        AttributeMatcher matcher = attributeSchemaServices.getMatcher(consumerSchema, producer.getSchema());
        ImmutableAttributes componentRequested = attributesFactory.concat(requestAttributes, producer.getOverriddenAttributes());
        final List<ResolvedVariant> variants = producer.getVariants();

        List<? extends ResolvedVariant> matches = matcher.matchMultipleCandidates(variants, componentRequested, explanationBuilder);
        if (matches.size() == 1) {
            return matches.get(0).getArtifacts();
        } else if (matches.size() > 1) {
            throw failureProcessor.ambiguousArtifactsFailure(matcher, producer, componentRequested, matches);
        }

        // We found no matches. Attempt to construct artifact transform chains which produce matching variants.
        List<TransformedVariant> transformedVariants = consumerProvidedVariantFinder.findTransformedVariants(variants, componentRequested);

        // If there are multiple potential artifact transform variants, perform attribute matching to attempt to find the best.
        if (transformedVariants.size() > 1) {
            transformedVariants = tryDisambiguate(matcher, transformedVariants, componentRequested, explanationBuilder, producer, componentRequested, failureProcessor, transformedVariantConverter);
        }

        if (transformedVariants.size() == 1) {
            TransformedVariant result = transformedVariants.get(0);
            return resolvedArtifactTransformer.asTransformed(result.getRoot(), result.getTransformedVariantDefinition(), dependenciesResolver, transformedVariantFactory);
        }

        if (allowNoMatchingVariants) {
            return ResolvedArtifactSet.EMPTY;
        }

        throw failureProcessor.noCompatibleArtifactFailure(matcher, producer, componentRequested, variants);
    }

    /**
     * Given a set of potential transform chains, attempt to reduce the set to a minimal set of preferred candidates.
     * Ideally, this method would return a single candidate.
     * <p>
     * This method starts by performing attribute matching on the candidates. This leverages disambiguation rules
     * from the {@link AttributeMatcher} to reduce the set of candidates. Return a single candidate only one remains.
     * <p>
     * If there are multiple results after disambiguation, return a subset of the results such that all candidates have
     * incompatible attributes values when matched with the <strong>last</strong> candidate. In some cases, this step is
     * able to arbitrarily reduces the candidate set to a single candidate as long as all remaining candidates are
     * compatible with each other.
     */
    private static List<TransformedVariant> tryDisambiguate(
        AttributeMatcher matcher,
        List<TransformedVariant> candidates,
        ImmutableAttributes componentRequested,
        AttributeMatchingExplanationBuilder explanationBuilder,
        ResolvedVariantSet targetVariantSet,
        ImmutableAttributes requestedAttributes,
        ResolutionFailureHandler failureHandler,
        TransformedVariantConverter transformedVariantConverter
    ) {
        List<TransformedVariant> matches = matcher.matchMultipleCandidates(candidates, componentRequested, explanationBuilder);
        if (matches.size() == 1) {
            return matches;
        }

        assert !matches.isEmpty();

        /*
         At this point, we may have ambiguity.  More than 1 match is okay IFF there are only 1 truly distinct
         transform chains in the set of matches.

         As chains of A -> B -> C -> D and A -> C -> B -> D are merely re-sequencings of the same chain they are
         not truly distinct.  This is fine , Gradle will just arbitrarily pick one, as the different order
         that steps are run is PROBABLY not meaningful - the same work will be done.

         However, if matches contains chains of A -> B -> C and A -> D -> C this is NOT okay!  Even if they end up
         producing a C with the same exact attributes, they represent DIFFERENT work being done, selected
         arbitrarily, and could have significant impact on the overall build time.  The build author should address
         this ambiguity.
        */

        // These are arbitrarily selected chains that produce incompatible results
        List<TransformedVariant> chainsProducingDistinctResults = new ArrayList<>(1);

        // Choosing the last candidate as the winner here is arbitrary, but should be preserved for backwards compatibility
        TransformedVariant arbitrarilySelectedWinningChain = matches.get(matches.size() - 1);
        chainsProducingDistinctResults.add(arbitrarilySelectedWinningChain);

        // Find all other candidate chains which do not result in a mutually compatible result with the arbitrary selection
        for (int i = 0; i < matches.size() - 1; i++) {
            TransformedVariant current = matches.get(i);
            if (!matcher.areMutuallyCompatible(current.getAttributes(), arbitrarilySelectedWinningChain.getAttributes())) {
                chainsProducingDistinctResults.add(current);
            }
        }

        // At this point, we know we have multiple matches, and we know how many of those matches produce incompatible results (chainsProducingDistinctResults)
        if (chainsProducingDistinctResults.size() == 1) {
            // If the distinct results are produced by different chains (like A -> B -> X vs. A -> C -> X; not just different sequences, like
            // A -> B -> C -> X vs. A -> C -> B -> X), then this is a problem, so assert that this isn't the case
            assertNotTrulyDistinctChains(targetVariantSet, requestedAttributes, failureHandler, transformedVariantConverter, matches);
            return chainsProducingDistinctResults;
        } else if (chainsProducingDistinctResults.size() > 1) {
            // The distinct results contains at least 2 chains that are NOT just re-sequencings of each other, so we DEFINITELY have
            // some truly distinct chains in there = find them and fail, reporting them
            List<TransformedVariant> trulyDistinctChains = findTrulyDistinctTransformationChains(matches, transformedVariantConverter);
            throw failureHandler.ambiguousArtifactTransformsFailure(targetVariantSet, componentRequested, trulyDistinctChains);
        } else {
            // This is impossible, we have at least one match, so we have at least one distinct result chain
            throw new IllegalStateException("No different transformation chains found out of: " + matches.size() + " matches; this can't happen!");
        }
    }

    /**
     * Checks that the given input chains are truly distinct, logging a deprecation warning if they are not.
     *
     * @param matches the input chains to inspect
     */
    private static void assertNotTrulyDistinctChains(ResolvedVariantSet targetVariantSet, ImmutableAttributes requestedAttributes, ResolutionFailureHandler failureHandler, TransformedVariantConverter transformedVariantConverter, List<TransformedVariant> matches) {
        // We chose some arbitrary transformation chain even though they were mutually compatible
        // Is this questionable behavior - were the "different" chains just re-sequencings of the same chain?
        List<TransformedVariant> trulyDistinctChains = findTrulyDistinctTransformationChains(matches, transformedVariantConverter);
        if (trulyDistinctChains.size() > 1) {
            // Return arbitrary chain for now, but this IS a problem, hence the deprecation warning

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

    /**
     * Determines the truly distinct chains within a given set of chains.
     * <p>
     * Truly distinct chains either produce incompatible results, or produce compatible results via a set of transforms
     * that are not merely re-sequencings of the same transforms already present in other given chains.
     *
     * @param chains the input chains to search
     * @param transformedVariantConverter converter of transform chain data
     * @return list of truly distinct chains, in input chain encounter order
     */
    private static List<TransformedVariant> findTrulyDistinctTransformationChains(List<TransformedVariant> chains, TransformedVariantConverter transformedVariantConverter) {
        // Map from a fingerprint to the variants that contain such a fingerprint
        Map<TransformationFingerprint, List<TransformedVariant>> distinctChains = new LinkedHashMap<>();

        // Map each transformation chain's unique fingerprint to the list of chains sharing it
        chains.forEach(chain -> {
            TransformationFingerprint fingerprint = Iterables.getOnlyElement(transformedVariantConverter.convert(Collections.singletonList(chain))).fingerprint();
            distinctChains.computeIfAbsent(fingerprint, f -> new ArrayList<>()).add(chain);
        });

        // Return an arbitrary representative of each unique transformation chain
        return distinctChains.values().stream()
            .map(transformedVariants -> transformedVariants.iterator().next())
            .collect(Collectors.toList());
    }
}
