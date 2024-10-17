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
import org.gradle.api.internal.artifacts.transform.TransformationChainsAssessor.AssessedTransformChains;
import org.gradle.api.internal.attributes.AttributeSchemaServices;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException;
import org.gradle.internal.deprecation.DeprecationLogger;

import java.util.List;
import java.util.Optional;

/**
 * A {@link ArtifactVariantSelector} that uses attribute matching to select a matching set of artifacts.
 * <p>
 * If no producer variant is compatible with the requested attributes, this selector will attempt to construct a chain of artifact
 * transforms that can produce a variant compatible with the requested attributes.
 * <p>
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
    private final TransformationChainsAssessor transformationChainsAssessor;
    private final ResolutionFailureHandler failureHandler;

    AttributeMatchingArtifactVariantSelector(
        ImmutableAttributesSchema consumerSchema,
        TransformUpstreamDependenciesResolver dependenciesResolver,
        ConsumerProvidedVariantFinder consumerProvidedVariantFinder,
        AttributesFactory attributesFactory,
        AttributeSchemaServices attributeSchemaServices,
        TransformedVariantFactory transformedVariantFactory,
        TransformationChainsAssessor transformationChainsAssessor,
        ResolutionFailureHandler failureHandler
    ) {
        this.consumerSchema = consumerSchema;
        this.dependenciesResolver = dependenciesResolver;
        this.consumerProvidedVariantFinder = consumerProvidedVariantFinder;
        this.attributesFactory = attributesFactory;
        this.attributeSchemaServices = attributeSchemaServices;
        this.transformedVariantFactory = transformedVariantFactory;
        this.transformationChainsAssessor = transformationChainsAssessor;
        this.failureHandler = failureHandler;
    }

    @Override
    public ResolvedArtifactSet select(ResolvedVariantSet producer, ImmutableAttributes requestAttributes, boolean allowNoMatchingVariants, ResolvedArtifactTransformer resolvedArtifactTransformer) {
        try {
            return doSelect(producer, allowNoMatchingVariants, resolvedArtifactTransformer, requestAttributes);
        } catch (Exception t) {
            return new BrokenResolvedArtifactSet(failureHandler.unknownArtifactVariantSelectionFailure(producer, requestAttributes, t));
        }
    }

    private ResolvedArtifactSet doSelect(ResolvedVariantSet producer, boolean allowNoMatchingVariants, ResolvedArtifactTransformer resolvedArtifactTransformer, ImmutableAttributes requestAttributes) {
        AttributeMatcher matcher = attributeSchemaServices.getMatcher(consumerSchema, producer.getSchema());
        ImmutableAttributes requestedAttributes = attributesFactory.concat(requestAttributes, producer.getOverriddenAttributes());
        final List<ResolvedVariant> variants = producer.getVariants();

        // Check for matching variant without using artifact transforms.  If we found only one, return it.  If we found multiple matches, that's ambiguity.
        List<? extends ResolvedVariant> matches = matcher.matchMultipleCandidates(variants, requestedAttributes, AttributeMatchingExplanationBuilder.logging());
        if (matches.size() == 1) {
            return matches.get(0).getArtifacts();
        } else if (matches.size() > 1) {
            throw failureHandler.ambiguousArtifactsFailure(matcher, producer, requestedAttributes, matches);
        }

        // We found no matching variant.  Construct potential artifact transform chains and attempt to select one that produces a suitable virtual variant.
        List<TransformedVariant> candidateTransformationChains = consumerProvidedVariantFinder.findTransformedVariants(variants, requestedAttributes);
        Optional<TransformedVariant> selectedTransformationChain = Optional.empty();
        if (candidateTransformationChains.size() == 1) {
            // There is a single candidate transformation chain, so use that one.
            selectedTransformationChain = Optional.of(candidateTransformationChains.get(0));
        } else if (candidateTransformationChains.size() > 1) {
            // If there are multiple potential transformation chains, perform disambiguation to attempt to find the best.
            selectedTransformationChain = selectTransformationChain(matcher, candidateTransformationChains, requestedAttributes, producer);
        }

        // If we determined a suitable transformation chain, use it.
        if (selectedTransformationChain.isPresent()) {
            return resolvedArtifactTransformer.asTransformed(selectedTransformationChain.get().getRoot(), selectedTransformationChain.get().getTransformedVariantDefinition(), dependenciesResolver, transformedVariantFactory);
        }

        // At this point, there is no possibility of a match for the request.  That could be okay if allowed, else it's a failure.
        if (allowNoMatchingVariants) {
            return ResolvedArtifactSet.EMPTY;
        } else {
            throw failureHandler.noCompatibleArtifactFailure(matcher, producer, requestedAttributes, variants);
        }
    }

    /**
     * Given a set of potential transform chains, attempt to reduce the set to the single compatible candidate.
     * <p>
     * If this isn't possible because there are multiple compatible matches, check if they are truly distinct,
     * and not just re-sequencings of the same chain.  If they are <strong>NOT</strong> distinct, arbitrarily
     * return one.
     * <p>
     * If multiple, compatible, truly distinct matches exist, we'll warn (for now), but this behavior is
     * deprecated.  As of Gradle 9.0, this will also fail.
     *
     * @return single preferred chain for use, selected as described above, or {@link Optional#empty()} if
     *          no compatible chains were found in the given candidates
     */
    private Optional<TransformedVariant> selectTransformationChain(
        AttributeMatcher matcher,
        List<TransformedVariant> candidateChains,
        ImmutableAttributes requestedAttributes,
        ResolvedVariantSet targetVariantSet
    ) {
        AssessedTransformChains result = transformationChainsAssessor.assessCandidates(matcher, candidateChains, requestedAttributes);

        if (!result.hasAnyMatches()) {
            return Optional.empty();
        }

        /*
         After disambiguation, if we have a single distinct chain found, then the ambiguity
         was entirely due to re-sequencings of the same set of transforms.

         For example, chains of A -> B -> C -> D and A -> C -> B -> D are merely re-sequencings of the same chain and
         are not truly distinct.  This is fine, Gradle will just arbitrarily pick one, as the different order
         that steps are run is PROBABLY not meaningful - the SAME work will be done.
        */
        Optional<TransformedVariant> singleDistinctMatchingChain = result.getSingleDistinctMatchingChain();
        if (singleDistinctMatchingChain.isPresent()) {
            return singleDistinctMatchingChain;
        }

        /*
         At this point, we have ambiguity.  There are more than one compatible matches with distinct fingerprints.

         For example, if matches contains chains of A -> B -> C and A -> D -> C this is NOT okay!  Even if they end up
         producing a C with the same exact attributes, they represent DIFFERENT work being done, and Gradle
         has no way to determine which is better to select and must make an arbitrary choice.  This
         choice will likely have impact, as different transforms could have very different performance
         characteristics, and because the author likely expects one path to be taken, but won't know if
         it was or wasn't should Gradle arbitrarily pick one.

         The build author needs to be notified and should address this ambiguity.
        */
        Optional<List<TransformedVariant>> singleGroupOfCompatibleChains = result.getSingleGroupOfCompatibleChains();
        if (singleGroupOfCompatibleChains.isPresent()) {
            /*
             However, we will not necessarily fail the build just yet.  To maintain behavior, we will not fail and
             only emit a deprecation if the multiple matches are COMPATIBLE, as this is what the build used to do.
            */
            warnThatMultipleDistinctChainsAreAvailable(targetVariantSet, requestedAttributes, failureHandler, result.getDistinctMatchingChainRepresentatives());
            return singleGroupOfCompatibleChains.map(Iterables::getLast); // Important to use LAST compatible match, as this is the previous behavior, and is tests in DisambiguateArtifactTransformIntegrationTest
        }

        /*
         At this point, there are multiple distinct chains that are not compatible with each other.  This is right out.
         It has never been allowed and fails the build.  The error message should report one representative of each
         distinct chain, so that the author can understand what's happening here and correct it.
        */
        throw failureHandler.ambiguousArtifactTransformsFailure(targetVariantSet, result.getTargetAttributes(), result.getDistinctMatchingChainRepresentatives());
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
