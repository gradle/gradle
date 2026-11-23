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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.attributes.AttributeSchemaServices;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesEntry;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.api.internal.attributes.matching.AttributeMatchingCandidate;
import org.gradle.internal.Try;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@link ArtifactVariantSelector} that uses attribute matching to select a matching set of artifacts.
 * <p>
 * If no producer variant is compatible with the requested attributes, this selector will attempt to construct a chain of artifact
 * transforms that can produce a variant compatible with the requested attributes.
 * <p>
 * A primary variant without artifacts whose configuration declares secondary variants is a candidate like any other, and is selected when it is the best match.
 * Prior to Gradle 11, selecting it requires the {@link FeaturePreviews.Feature#CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS} feature preview.
 * Without it, such a variant is not eligible for selection, and a deprecation warning is emitted when considering it would change the result:
 * when it, or a transformation of it, would be selected, or when it would make the match ambiguous.
 * With it, selecting such a variant emits a warning, since the request is likely underspecified.
 * See {@link SelectionWarningHelper} for how often each is reported.
 * <p>
 * An instance of {@link ResolutionFailureHandler} is injected in the constructor
 * to allow the caller to handle failures in a consistent manner as during graph variant selection.
 */
public class AttributeMatchingArtifactVariantSelector implements ArtifactVariantSelector {
    private static final Logger LOGGER = LoggerFactory.getLogger(AttributeMatchingArtifactVariantSelector.class);

    private final ImmutableAttributesSchema consumerSchema;
    private final AttributesFactory attributesFactory;
    private final AttributeSchemaServices attributeSchemaServices;
    private final ResolutionFailureHandler failureHandler;
    private final TransformationChainSelector transformationChainSelector;
    private final boolean artifactlessPrimaryVariantSelectionEnabled;
    private final SelectionWarningHelper selectionWarningHelper = new SelectionWarningHelper();

    public AttributeMatchingArtifactVariantSelector(
        ImmutableAttributesSchema consumerSchema,
        ConsumerProvidedVariantFinder transformationChainBuilder,
        AttributesFactory attributesFactory,
        AttributeSchemaServices attributeSchemaServices,
        ResolutionFailureHandler failureHandler,
        boolean artifactlessPrimaryVariantSelectionEnabled
    ) {
        this.consumerSchema = consumerSchema;
        this.attributesFactory = attributesFactory;
        this.attributeSchemaServices = attributeSchemaServices;
        this.failureHandler = failureHandler;
        this.transformationChainSelector = new TransformationChainSelector(transformationChainBuilder, failureHandler);
        this.artifactlessPrimaryVariantSelectionEnabled = artifactlessPrimaryVariantSelectionEnabled;
    }

    @Override
    public ResolvedArtifactSet select(
        ResolvedVariantSet producer,
        ImmutableAttributes requestAttributes,
        boolean allowNoMatchingVariants
    ) {
        try {
            AttributeMatcher attributeMatcher = attributeSchemaServices.getMatcher(consumerSchema, producer.getProducerSchema());
            ImmutableAttributes targetAttributes = attributesFactory.concat(requestAttributes, producer.getOverriddenAttributes());

            MemoizingSelector selector = new MemoizingSelector(producer, targetAttributes, attributeMatcher, failureHandler, transformationChainSelector);

            // A configuration whose primary variant has no artifacts, but which declares secondary variants, keeps its
            // primary variant as a candidate, marked as previously unselectable. Prior to Gradle 9.10, such a variant was
            // not a candidate at all, since the configuration did not expose it.
            ResolvedVariant primary = findPrimaryWithoutArtifacts(producer);
            if (primary == null) {
                // There is nothing to exclude, so select among all candidates
                return selector.select(allowNoMatchingVariants);
            }

            MemoizingSelector selectorWithoutPrimary = new MemoizingSelector(excluding(producer, primary), targetAttributes, attributeMatcher, failureHandler, transformationChainSelector);

            if (artifactlessPrimaryVariantSelectionEnabled) {
                // Select among all candidates, as will be the default starting with Gradle 11, when only this branch remains
                selectionWarningHelper.warnIfPrimarySelected(selector, selectorWithoutPrimary, primary);
                return selector.select(allowNoMatchingVariants);
            } else {
                // Select among the other variants, as prior to Gradle 9.10, so a failure lists only those
                selectionWarningHelper.nagIfConsideringPrimaryChangesResult(selector, selectorWithoutPrimary, primary);
                return selectorWithoutPrimary.select(allowNoMatchingVariants);
            }
        } catch (Exception t) {
            return new BrokenResolvedArtifactSet(failureHandler.unknownArtifactVariantSelectionFailure(producer, requestAttributes, t));
        }
    }

    /**
     * Returns the primary variant without artifacts among the producer's candidates, or {@code null} if there is none.
     * <p>
     * This runs for every selection from every producer, most of which have no such variant, so it loops rather than streams to avoid allocating.
     */
    @Nullable
    private static ResolvedVariant findPrimaryWithoutArtifacts(ResolvedVariantSet producer) {
        for (ResolvedVariant candidate : producer.getCandidates()) {
            if (candidate.isPreviouslyUnselectable()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Returns the given variants without the given variant.
     */
    private static ResolvedVariantSet excluding(ResolvedVariantSet variants, ResolvedVariant variant) {
        ImmutableList<ResolvedVariant> others = variants.getCandidates().stream()
            .filter(candidate -> candidate != variant)
            .collect(ImmutableList.toImmutableList());
        return variants.subset(others);
    }

    /**
     * Holds the warning and deprecation logic about primary variants without artifacts: a deprecation when such a variant is ignored,
     * but considering it would change the result, and a warning when the feature preview selects one.
     * <p>
     * A selector serves every artifact selection from one resolution result, which may be queried many times, from many build script locations,
     * and possibly concurrently. The deprecation logger reports a message again for each location, so each is reported only once per selector here:
     * <ul>
     *     <li>The warning once per producer and variant, since its advice requires a selection among the other variants that selecting did not need.
     *     Later selections of the same variant with other requests, and so other advice, are not reported.</li>
     *     <li>The deprecation once per distinct message, so once per producer, variant, and advice. Its advice reuses the selection among the other
     *     variants that the result needs anyway, and different requests can need different advice.</li>
     * </ul>
     * Separate resolutions, such as of different configurations, have separate selectors, so each reports them again.
     * <p>
     * This class is meant to be removed entirely in Gradle 11, along with the warning when the feature preview, then the default,
     * selects a primary variant without artifacts.
     */
    private static final class SelectionWarningHelper {
        private final Set<String> reported = ConcurrentHashMap.newKeySet();

        /**
         * Warns, once per producer and variant, if the selection among all candidates selects the primary variant without artifacts,
         * since selecting a variant without artifacts likely means the request is underspecified.
         */
        void warnIfPrimarySelected(
            MemoizingSelector selector,
            MemoizingSelector selectorWithoutPrimary,
            ResolvedVariant primaryWithoutArtifacts
        ) {
            if (!wouldSelect(selector, primaryWithoutArtifacts)) {
                return;
            }
            String summary = "Selecting artifacts from " + selector.variants.asDescribable().getDisplayName() + " selected variant '" + primaryWithoutArtifacts.asDescribable().getDisplayName() + "', which has no artifacts.";
            // The advice requires a selection among the other variants that selecting did not need, so it is only computed for a new warning
            if (reported.add(summary)) {
                LOGGER.warn("{} {}.", summary, adviceForSelectedPrimary(selectorWithoutPrimary, primaryWithoutArtifacts));
            }
        }

        /**
         * Would selecting among all candidates select the primary variant without artifacts, or a transformation of it, or fail since it is part of an ambiguous match?
         * <p>
         * Deciding evaluates the primary variant, which selecting prior to Gradle 9.10 never did. So a failure while deciding counts as not selecting it,
         * rather than failing a selection that would otherwise succeed.
         */
        private static boolean wouldConsideringPrimaryChangeResult(
            MemoizingSelector selector,
            MemoizingSelector selectorWithoutPrimary,
            ResolvedVariant primaryWithoutArtifacts
        ) {
            try {
                List<ResolvedVariant> matches = selector.getMatches();
                if (!matches.isEmpty()) {
                    return matches.contains(primaryWithoutArtifacts);
                }
                if (hasAmbiguousTransformationChains(selector)) {
                    // Chains of transformations of the primary variant are part of the ambiguity, unless the other variants' chains are ambiguous by themselves
                    return !hasAmbiguousTransformationChains(selectorWithoutPrimary);
                }
                return wouldSelect(selector, primaryWithoutArtifacts);
            } catch (RuntimeException e) {
                return false;
            }
        }

        /**
         * Emits the deprecation for ignoring a primary variant without artifacts, if considering it would change the result, once per distinct message.
         * <p>
         * The deprecation logger reports a message again for each build script location that triggers it, which for a selection is merely where
         * resolution happened, rather than where the request was declared. The advice queries the selection among the other variants, which
         * is memoized and is then reused to select the result, so building it costs no further matching.
         */
        void nagIfConsideringPrimaryChangesResult(
            MemoizingSelector selector,
            MemoizingSelector selectorWithoutPrimary,
            ResolvedVariant primaryWithoutArtifacts
        ) {
            if (!wouldConsideringPrimaryChangeResult(selector, selectorWithoutPrimary, primaryWithoutArtifacts)) {
                return;
            }
            String summary = "Selecting artifacts from " + selector.variants.asDescribable().getDisplayName() + " ignored variant '" + primaryWithoutArtifacts.asDescribable().getDisplayName() + "' because it has no artifacts.";
            String advice = adviceForSelectedPrimary(selectorWithoutPrimary, primaryWithoutArtifacts) + "; or enable the '" +
                FeaturePreviews.Feature.CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS.name() + "' feature preview to opt into the new behavior.";
            if (reported.add(summary + " " + advice)) {
                DeprecationLogger.deprecateBehaviour(summary)
                    .withProblemName("Ignoring primary variants without artifacts")
                    .withAdvice(advice)
                    .startingWithGradle11("primary variants without artifacts will be selected when they are the best match")
                    .withUpgradeGuideSection(9, "variants_with_no_artifacts")
                    .nagUser();
            }
        }

        /**
         * Returns the two ways to keep the primary variant without artifacts from being the best match: requesting an attribute it lacks, or adding attributes to it.
         */
        private static String adviceForSelectedPrimary(MemoizingSelector selectorWithoutPrimary, ResolvedVariant primaryWithoutArtifacts) {
            Set<String> attributesNotOnPrimary = attributesNotOnPrimary(selectorWithoutPrimary, primaryWithoutArtifacts);
            String moreAttributesInRequest = attributesNotOnPrimary.isEmpty()
                ? "Add attributes to the request so that it only matches the intended variant"
                : "Add one of these attributes to the request to make it more precise: " + String.join(", ", attributesNotOnPrimary);
            return moreAttributesInRequest + "; or add attributes to variant '" + primaryWithoutArtifacts.asDescribable().getDisplayName() + "' so that it is no longer the best match";
        }

        /**
         * Would the selector select the given variant, or a transformation of it?
         * <p>
         * A selection that fails, such as for ambiguous chains of transformations, selects nothing.
         * <p>
         * Starting with Gradle 11, the deprecation no longer needs this, but the warning when the feature preview, then the default,
         * selects a primary variant without artifacts still does. It can only be removed if that warning is removed too.
         */
        private static boolean wouldSelect(MemoizingSelector selector, ResolvedVariant variant) {
            try {
                List<ResolvedVariant> matchingVariants = selector.getMatches();
                if (!matchingVariants.isEmpty()) {
                    return matchingVariants.size() == 1 && matchingVariants.get(0) == variant;
                }
                TransformedVariant selectedTransformationChain = selector.getTransformationChain();
                return selectedTransformationChain != null && selectedTransformationChain.getRoot() == variant;
            } catch (RuntimeException e) {
                return false;
            }
        }

        /**
         * Do several of the selector's chains of transformations match the request, so that they cannot be disambiguated?
         */
        private static boolean hasAmbiguousTransformationChains(MemoizingSelector selector) {
            try {
                selector.getTransformationChain();
                return false;
            } catch (AbstractResolutionFailureException e) {
                return true;
            }
        }

        /**
         * Returns the variants that the selector matches directly, or else its single chain of transformations, if any.
         * <p>
         * Several are returned if the match is ambiguous, and none if the chains of transformations are ambiguous.
         */
        private static List<? extends AttributeMatchingCandidate> selectedCandidates(MemoizingSelector selector) {
            List<ResolvedVariant> matchingVariants = selector.getMatches();
            if (!matchingVariants.isEmpty() || hasAmbiguousTransformationChains(selector)) {
                return matchingVariants;
            }
            TransformedVariant selectedTransformationChain = selector.getTransformationChain();
            return selectedTransformationChain != null ? ImmutableList.of(selectedTransformationChain) : ImmutableList.of();
        }

        /**
         * Returns the attributes, as {@code name=value}, that the single variant selected without the primary variant declares, but the primary
         * variant lacks or has with another value, excluding attributes already requested and empty values. Nothing is suggested if no single
         * variant is selected.
         * <p>
         * Requesting one of these makes the request more precise, so that the primary variant is no longer the best match.
         */
        private static Set<String> attributesNotOnPrimary(MemoizingSelector selectorWithoutPrimary, ResolvedVariant primaryWithoutArtifacts) {
            List<? extends AttributeMatchingCandidate> selectedCandidates;
            try {
                selectedCandidates = selectedCandidates(selectorWithoutPrimary);
            } catch (RuntimeException e) {
                // Advice must not fail a selection
                return ImmutableSet.of();
            }
            if (selectedCandidates.size() != 1) {
                // Without a single variant to prefer, no attribute is safe to suggest
                return ImmutableSet.of();
            }

            ImmutableSet.Builder<String> attributes = ImmutableSet.builder();
            for (ImmutableAttributesEntry<?> entry : selectedCandidates.get(0).getAttributes().getEntries()) {
                String name = entry.getKey().getName();
                String value = String.valueOf(entry.getIsolatedValue());
                if (value.isEmpty() || selectorWithoutPrimary.targetAttributes.findEntry(name) != null) {
                    continue;
                }
                ImmutableAttributesEntry<?> primaryEntry = primaryWithoutArtifacts.getAttributes().findEntry(name);
                if (primaryEntry == null || !Objects.equals(primaryEntry.getIsolatedValue(), entry.getIsolatedValue())) {
                    attributes.add(name + "=" + value);
                }
            }
            return attributes.build();
        }
    }

    /**
     * Selects among a set of variants as Gradle always has: a single direct match, or else a single chain of transformations.
     * <p>
     * Deciding about the primary variant without artifacts queries it as well as selecting,
     * so a {@code MemoizingSelector} computes nothing up front, and computes each of these at most once, remembering any failure:
     * <ul>
     *     <li>{@link #getMatches()}: the direct attribute matches, after the matcher's disambiguation.</li>
     *     <li>{@link #getTransformationChain()}: the single best chain of transformations, which fails if the chains are ambiguous.</li>
     * </ul>
     */
    private static final class MemoizingSelector {
        private final ResolvedVariantSet variants;
        private final ImmutableAttributes targetAttributes;
        private final AttributeMatcher matcher;
        private final ResolutionFailureHandler failureHandler;
        private final TransformationChainSelector transformationChainSelector;

        @Nullable
        private Try<List<ResolvedVariant>> matches;
        @Nullable
        private Try<@Nullable TransformedVariant> transformationChain;

        MemoizingSelector(
            ResolvedVariantSet variants,
            ImmutableAttributes targetAttributes,
            AttributeMatcher matcher,
            ResolutionFailureHandler failureHandler,
            TransformationChainSelector transformationChainSelector
        ) {
            this.variants = variants;
            this.targetAttributes = targetAttributes;
            this.matcher = matcher;
            this.failureHandler = failureHandler;
            this.transformationChainSelector = transformationChainSelector;
        }

        /**
         * Returns the variants that match the requested attributes directly.
         * <p>
         * A failure to match, such as an attribute rule that throws, is remembered and thrown again.
         */
        List<ResolvedVariant> getMatches() {
            if (matches == null) {
                matches = Try.ofFailable(() -> matcher.matchMultipleCandidates(variants.getCandidates(), targetAttributes));
            }
            return matches.get();
        }

        /**
         * Returns the chain of transformations that produces a suitable virtual variant, or {@code null} if there is none.
         * <p>
         * Any failure to select a chain is remembered and thrown again.
         *
         * @throws AbstractResolutionFailureException if several chains match and cannot be disambiguated
         */
        @Nullable
        TransformedVariant getTransformationChain() {
            if (transformationChain == null) {
                transformationChain = Try.ofFailable(() -> transformationChainSelector.selectTransformationChain(variants, targetAttributes, matcher).orElse(null));
            }
            return transformationChain.get();
        }

        /**
         * Selects the artifacts of the single direct match, or else of the single chain of transformations that produces a suitable virtual variant.
         *
         * @return the selected artifacts, or {@link ResolvedArtifactSet#EMPTY} if none match and {@code allowNoMatchingVariants} is true
         * @throws AbstractResolutionFailureException if several variants match, several chains match and cannot be disambiguated,
         * or none match and {@code allowNoMatchingVariants} is false
         */
        ResolvedArtifactSet select(boolean allowNoMatchingVariants) {
            List<ResolvedVariant> matchingVariants = getMatches();
            if (matchingVariants.size() == 1) {
                return matchingVariants.get(0).getArtifacts();
            } else if (matchingVariants.size() > 1) {
                throw failureHandler.ambiguousArtifactsFailure(matcher, variants, targetAttributes, matchingVariants);
            }

            // We found no matching variant.  Attempt to select a chain of transformations that produces a suitable virtual variant.
            TransformedVariant selectedTransformationChain = getTransformationChain();
            if (selectedTransformationChain != null) {
                return variants.transformCandidate(selectedTransformationChain.getRoot(), selectedTransformationChain.getTransformedVariantDefinition());
            }

            if (allowNoMatchingVariants) {
                return ResolvedArtifactSet.EMPTY;
            }
            throw failureHandler.noCompatibleArtifactFailure(matcher, variants, targetAttributes);
        }
    }
}
