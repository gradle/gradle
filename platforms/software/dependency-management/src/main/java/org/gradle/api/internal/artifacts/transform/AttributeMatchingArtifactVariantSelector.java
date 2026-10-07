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
import org.gradle.internal.deprecation.DeprecationLogger;
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

        // Two-pass strategy when any candidate carries the org.gradle.fallback-variant attribute and
        // the consumer did not request it: first try with fallback-variant=false injected on the
        // consumer side, which prunes the auto-tagged primary at the compatibility check. If that pass
        // produces an answer (direct match, ambiguity, or transform chain), use it. Otherwise fall
        // through to a second pass with the original request, so that an empty fallback primary can
        // still act as a "nothing to resolve" backstop. See FallbackVariantSupport for the
        // producer-side tagging contract.
        boolean consumerRequestsFallbackVariant = targetAttributes.findEntry(FallbackVariant.FALLBACK_VARIANT_ATTRIBUTE.getName()) != null;
        if (!consumerRequestsFallbackVariant && anyCandidateCarriesFallbackVariant(producer.getCandidates())) {
            ImmutableAttributes augmentedAttrs = fallbackVariantSupport.augmentConsumerWithDefault(targetAttributes, attributesFactory);
            ResolvedArtifactSet augmentedResult = tryMatchOrTransform(producer, augmentedAttrs, targetAttributes, matcher);
            if (augmentedResult != null) {
                return augmentedResult;
            }

            // Running the second pass is deprecated regardless of its outcome. The deprecation fires
            // before the pass so that a subsequent failure still surfaces it. Lenient requests skip
            // it: they tolerate no match, so the outcome is unchanged.
            if (!allowNoMatchingVariants) {
                nagAboutFallbackPass(producer);
            }
        }

        ResolvedArtifactSet result = tryMatchOrTransform(producer, targetAttributes, targetAttributes, matcher);
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

    private static void nagAboutFallbackPass(ResolvedVariantSet producer) {
            DeprecationLogger.deprecateBehaviour(
                "Resolving artifacts for " + producer.asDescribable().getDisplayName() +
                    " required a fallback selection pass because no variant satisfied the request."
            )
                .withAdvice(
                    "To match a variant tagged '" + FallbackVariant.FALLBACK_VARIANT_ATTRIBUTE.getName() +
                        "=" + FallbackVariant.TRUE + "', request '" + FallbackVariant.FALLBACK_VARIANT_ATTRIBUTE.getName() +
                        "=" + FallbackVariant.TRUE + "' explicitly on the consumer. Otherwise, expose a variant that " +
                        "carries the attributes this request needs."
                )
                .willBecomeAnErrorInNextMajorGradleVersion()
                .withUpgradeGuideSection(9, "variants_with_no_artifacts")
                .nagUser();
    }

    /**
     * Attempts a single matching pass with the given consumer attributes.
     * <p>
     * Returns the matched artifact set when matching or a transform chain succeeds, or {@code null} when neither
     * direct matching nor a transform chain produced a candidate (signalling that the caller
     * may try a different attribute set or escalate to a failure).
     *
     * @param attrs the attributes to match with
     * @param reportAttrs the attributes to report in failures, excluding any injected by Gradle
     */
    @Nullable
    private ResolvedArtifactSet tryMatchOrTransform(
        ResolvedVariantSet producer,
        ImmutableAttributes attrs,
        ImmutableAttributes reportAttrs,
        AttributeMatcher matcher
    ) {
        List<ResolvedVariant> matchingVariants = matcher.matchMultipleCandidates(producer.getCandidates(), attrs);
        if (matchingVariants.size() == 1) {
            return matchingVariants.get(0).getArtifacts();
        } else if (matchingVariants.size() > 1) {
            throw failureHandler.ambiguousArtifactsFailure(matcher, producer, reportAttrs, matchingVariants);
        }

        Optional<TransformedVariant> selectedTransformationChain = transformationChainSelector.selectTransformationChain(producer, attrs, reportAttrs, matcher);
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
