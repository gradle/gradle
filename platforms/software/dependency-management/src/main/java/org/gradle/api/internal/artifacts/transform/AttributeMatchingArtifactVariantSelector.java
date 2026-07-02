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

import org.gradle.api.attributes.FallbackVariant;
import org.gradle.api.internal.artifacts.dsl.dependencies.FallbackVariantSupport;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.attributes.AttributeSchemaServices;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.jspecify.annotations.Nullable;

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
    private final AttributesFactory attributesFactory;
    private final AttributeSchemaServices attributeSchemaServices;
    private final ResolutionFailureHandler failureHandler;
    private final TransformationChainSelector transformationChainSelector;
    private final FallbackVariantSupport fallbackVariantSupport;

    public AttributeMatchingArtifactVariantSelector(
        ImmutableAttributesSchema consumerSchema,
        ConsumerProvidedVariantFinder transformationChainBuilder,
        AttributesFactory attributesFactory,
        AttributeSchemaServices attributeSchemaServices,
        ResolutionFailureHandler failureHandler,
        FallbackVariantSupport fallbackVariantSupport
    ) {
        this.consumerSchema = consumerSchema;
        this.attributesFactory = attributesFactory;
        this.attributeSchemaServices = attributeSchemaServices;
        this.failureHandler = failureHandler;
        this.transformationChainSelector = new TransformationChainSelector(transformationChainBuilder, failureHandler);
        this.fallbackVariantSupport = fallbackVariantSupport;
    }

    @Override
    public ResolvedArtifactSet select(
        ResolvedVariantSet producer,
        ImmutableAttributes requestAttributes,
        boolean allowNoMatchingVariants
    ) {
        try {
            return doSelect(producer, requestAttributes, allowNoMatchingVariants);
        } catch (Exception t) {
            return new BrokenResolvedArtifactSet(failureHandler.unknownArtifactVariantSelectionFailure(producer, requestAttributes, t));
        }
    }

    private ResolvedArtifactSet doSelect(
        ResolvedVariantSet producer,
        ImmutableAttributes requestAttributes,
        boolean allowNoMatchingVariants
    ) {
        AttributeMatcher matcher = attributeSchemaServices.getMatcher(consumerSchema, producer.getProducerSchema());
        ImmutableAttributes targetAttributes = attributesFactory.concat(requestAttributes, producer.getOverriddenAttributes());

        // Two-pass strategy when any candidate carries the org.gradle.fallback-variant attribute:
        // first try with fallback-variant=false injected on the consumer side, which prunes the
        // auto-tagged primary at the compatibility check. If that pass produces an answer (direct
        // match, ambiguity, or transform chain), use it. Otherwise fall through to a second pass
        // with the original (unaugmented) request, so that an empty fallback primary can still
        // act as a "nothing to resolve" backstop when neither a secondary nor a transform chain
        // can satisfy the request. See FallbackVariantSupport for the producer-side tagging contract.
        if (anyCandidateCarriesFallbackVariant(producer.getCandidates())) {
            ImmutableAttributes augmentedAttrs = fallbackVariantSupport.augmentConsumerWithDefault(targetAttributes, attributesFactory);
            ResolvedArtifactSet augmentedResult = tryMatchOrTransform(producer, augmentedAttrs, matcher, true);
            if (augmentedResult != null) {
                return augmentedResult;
            }
        }

        // Augmented pass produced no match and no transform chain. Try using the original attributes for
        // matching so that the fallback primary can still satisfy a no-op resolution.
        ResolvedArtifactSet result = tryMatchOrTransform(producer, targetAttributes, matcher, false);
        if (result != null) {
            return result;
        }

        // At this point, there is no possibility of a match for the request.  That could be okay if allowed, else it's a failure.
        if (allowNoMatchingVariants) {
            return ResolvedArtifactSet.EMPTY;
        } else {
            throw failureHandler.noCompatibleArtifactFailure(matcher, producer, targetAttributes);
        }
    }

    /**
     * Attempts a single matching pass with the given consumer attributes.
     * <p>
     * Returns the matched artifact set when matching or a transform chain succeeds, or {@code null} when neither
     * direct matching nor a transform chain produced a candidate (signalling that the caller
     * may try a different attribute set or escalate to a failure).
     *
     * @param augmentedTry Whether this request includes an injected fallback variant attribute
     */
    @Nullable
    private ResolvedArtifactSet tryMatchOrTransform(
        ResolvedVariantSet producer,
        ImmutableAttributes attrs,
        AttributeMatcher matcher,
        boolean augmentedTry
    ) {
        List<ResolvedVariant> matchingVariants = matcher.matchMultipleCandidates(producer.getCandidates(), attrs);
        if (matchingVariants.size() == 1) {
            return matchingVariants.get(0).getArtifacts();
        } else if (matchingVariants.size() > 1) {
            var reportAttrs = augmentedTry ? fallbackVariantSupport.removeFallbackVariant(attrs, attributesFactory) : attrs;
            throw failureHandler.ambiguousArtifactsFailure(matcher, producer, reportAttrs, matchingVariants);
        }

        Optional<TransformedVariant> selectedTransformationChain = transformationChainSelector.selectTransformationChain(producer, attrs, matcher);
        return selectedTransformationChain.map(transformedVariant -> producer.transformCandidate(transformedVariant.getRoot(), transformedVariant.getTransformedVariantDefinition())).orElse(null);
    }

    private static boolean anyCandidateCarriesFallbackVariant(List<? extends ResolvedVariant> candidates) {
        for (ResolvedVariant candidate : candidates) {
            if (candidate.getAttributes().findEntry(FallbackVariant.FALLBACK_VARIANT_ATTRIBUTE.getName()) != null) {
                return true;
            }
        }
        return false;
    }
}
