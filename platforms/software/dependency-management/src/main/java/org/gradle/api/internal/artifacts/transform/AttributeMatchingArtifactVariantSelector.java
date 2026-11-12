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

import org.gradle.api.Describable;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.Attribute;
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
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@link ArtifactVariantSelector} that uses attribute matching to select a matching set of artifacts.
 * <p>
 * If no producer variant is compatible with the requested attributes, this selector will attempt to construct a chain of artifact
 * transforms that can produce a variant compatible with the requested attributes.
 * <p>
 * A primary variant without artifacts whose configuration declares secondary variants is selected when it is the best match.
 * Since it lacks the attributes of artifacts, only the requested attributes decide whether it is a better match than a variant
 * with artifacts, and a transformation of a variant with artifacts is preferred over it unless it matches every requested attribute.
 * Prior to Gradle 10, selecting it requires the {@link FeaturePreviews.Feature#CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS} feature preview.
 * Without it, such a variant is not eligible for selection, and a deprecation warning is emitted when it would be selected.
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
    private final boolean considerArtifactlessPrimaryVariants;

    public AttributeMatchingArtifactVariantSelector(
        ImmutableAttributesSchema consumerSchema,
        ConsumerProvidedVariantFinder transformationChainBuilder,
        AttributesFactory attributesFactory,
        AttributeSchemaServices attributeSchemaServices,
        ResolutionFailureHandler failureHandler,
        boolean considerArtifactlessPrimaryVariants
    ) {
        this.consumerSchema = consumerSchema;
        this.attributesFactory = attributesFactory;
        this.attributeSchemaServices = attributeSchemaServices;
        this.failureHandler = failureHandler;
        this.transformationChainSelector = new TransformationChainSelector(transformationChainBuilder, failureHandler);
        this.considerArtifactlessPrimaryVariants = considerArtifactlessPrimaryVariants;
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

        // A configuration whose primary variant has no artifacts, but which declares secondary variants, keeps its
        // primary variant as a candidate, marked as previously unselectable. Prior to Gradle 9.10, such a variant was
        // dropped before selection. Producers without such a variant are selected from as usual.
        ResolvedVariant primaryWithoutArtifacts = findPreviouslyUnselectable(producer);
        if (primaryWithoutArtifacts == null) {
            return selectOrFail(new VariantSelection(producer, targetAttributes, matcher), producer, targetAttributes, matcher, allowNoMatchingVariants);
        }

        // The other variants are the candidates prior to Gradle 9.10. Select among them, rather than among all candidates,
        // unless the primary variant would be selected. Otherwise, the attribute matcher would prefer the primary variant
        // merely because it lacks the attributes of artifacts, such as artifactType, that the other variants declare.
        // Deciding whether the primary variant would be selected queries the same selections as selecting does, so share them.
        ResolvedVariantSet otherVariants = new WithoutPreviouslyUnselectableVariants(producer);
        VariantSelection otherSelection = new VariantSelection(otherVariants, targetAttributes, matcher);
        VariantSelection allSelection = new VariantSelection(producer, targetAttributes, matcher);
        boolean primaryWouldBeSelected = isPrimaryWithoutArtifactsSelected(primaryWithoutArtifacts, otherSelection, allSelection, targetAttributes, matcher);

        if (considerArtifactlessPrimaryVariants) {
            if (primaryWouldBeSelected) {
                // The primary variant matches directly, or only a transformation of it matches
                return matcher.isMatchingCandidate(primaryWithoutArtifacts.getAttributes(), targetAttributes)
                    ? primaryWithoutArtifacts.getArtifacts()
                    : selectOrFail(allSelection, producer, targetAttributes, matcher, allowNoMatchingVariants);
            } else {
                // The primary variant is eligible, so report it if nothing matches
                return selectOrFail(otherSelection, producer, targetAttributes, matcher, allowNoMatchingVariants);
            }
        } else {
            // Without the feature preview, the primary variant is not eligible for selection, even if it would be selected.
            // Select among the other variants, as prior to Gradle 9.10, and warn if the primary variant would be selected.
            // If the other variants fail to resolve, the request fails as before, but would resolve to no artifacts with the
            // primary variant, so warn before failing.
            ResolvedArtifactSet selected;
            try {
                selected = otherSelection.select();
            } catch (AbstractResolutionFailureException e) {
                if (primaryWouldBeSelected) {
                    nagAboutIgnoredVariant(producer, primaryWithoutArtifacts);
                }
                throw e;
            }

            // A lenient request that no other variant matches resolves to no artifacts either way,
            // since the primary variant has none, so it does not warn.
            if (primaryWouldBeSelected && (selected != null || !allowNoMatchingVariants)) {
                nagAboutIgnoredVariant(producer, primaryWithoutArtifacts);
            }
            return selected != null ? selected : noMatch(otherVariants, targetAttributes, matcher, allowNoMatchingVariants);
        }
    }

    /**
     * Would the primary variant without artifacts be selected, if it were eligible?
     * <p>
     * It is selected when:
     * <ul>
     *     <li>It matches the requested attributes strictly better than every other matching variant. Only the requested attributes count,
     *     so that lacking an attribute, such as the {@code artifactType} of its missing artifacts, does not make it a better match.</li>
     *     <li>No other variant matches, and it matches every requested attribute, so it is preferred over a transformation as usual.</li>
     *     <li>Nothing else matches, either directly or through a transformation.</li>
     * </ul>
     * <p>
     * The given selections are shared with the selection that follows, so that their queries run at most once.
     */
    private boolean isPrimaryWithoutArtifactsSelected(
        ResolvedVariant primaryWithoutArtifacts,
        VariantSelection otherSelection,
        VariantSelection allSelection,
        ImmutableAttributes targetAttributes,
        AttributeMatcher matcher
    ) {
        if (!matcher.isMatchingCandidate(primaryWithoutArtifacts.getAttributes(), targetAttributes)) {
            // Only a transformation of the primary variant can match
            return !otherSelection.isAnyVariantSelectable() && allSelection.isAnyVariantSelectable();
        }
        List<ResolvedVariant> otherMatches = otherSelection.getMatches();
        if (!otherMatches.isEmpty()) {
            return isStrictlyBetterMatch(primaryWithoutArtifacts, otherMatches, targetAttributes, matcher);
        }
        return declaresAllAttributes(primaryWithoutArtifacts, targetAttributes) || !otherSelection.isAnyVariantSelectable();
    }

    /**
     * Matches the candidates using only their requested attributes, so that the remaining attributes cannot disambiguate them.
     */
    private boolean isStrictlyBetterMatch(
        ResolvedVariant candidate,
        List<ResolvedVariant> others,
        ImmutableAttributes targetAttributes,
        AttributeMatcher matcher
    ) {
        List<AttributeMatchingCandidate> requestedOnly = new ArrayList<>(others.size() + 1);
        requestedOnly.add(withRequestedAttributesOnly(candidate, targetAttributes));
        for (ResolvedVariant other : others) {
            requestedOnly.add(withRequestedAttributesOnly(other, targetAttributes));
        }
        List<AttributeMatchingCandidate> matches = matcher.matchMultipleCandidates(requestedOnly, targetAttributes);
        return matches.size() == 1 && matches.get(0) == requestedOnly.get(0);
    }

    private AttributeMatchingCandidate withRequestedAttributesOnly(ResolvedVariant variant, ImmutableAttributes targetAttributes) {
        ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
        for (Attribute<?> attribute : targetAttributes.keySet()) {
            ImmutableAttributesEntry<?> entry = variant.getAttributes().findEntry(attribute.getName());
            if (entry != null) {
                attributes = concat(attributes, entry);
            }
        }
        ImmutableAttributes requestedOnly = attributes;
        return () -> requestedOnly;
    }

    private <T> ImmutableAttributes concat(ImmutableAttributes attributes, ImmutableAttributesEntry<T> entry) {
        return attributesFactory.concat(attributes, entry.getKey(), entry.getValue());
    }

    private static boolean declaresAllAttributes(ResolvedVariant variant, ImmutableAttributes targetAttributes) {
        for (Attribute<?> attribute : targetAttributes.keySet()) {
            if (variant.getAttributes().findEntry(attribute.getName()) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Selects from the given variants, or fails listing the reported variants as the candidates if none match.
     */
    private ResolvedArtifactSet selectOrFail(
        VariantSelection selection,
        ResolvedVariantSet reportedVariants,
        ImmutableAttributes targetAttributes,
        AttributeMatcher matcher,
        boolean allowNoMatchingVariants
    ) {
        ResolvedArtifactSet selected = selection.select();
        return selected != null ? selected : noMatch(reportedVariants, targetAttributes, matcher, allowNoMatchingVariants);
    }

    /**
     * At this point, there is no possibility of a match for the request.  That could be okay if allowed, else it's a failure.
     */
    private ResolvedArtifactSet noMatch(
        ResolvedVariantSet producer,
        ImmutableAttributes targetAttributes,
        AttributeMatcher matcher,
        boolean allowNoMatchingVariants
    ) {
        if (allowNoMatchingVariants) {
            return ResolvedArtifactSet.EMPTY;
        }
        throw failureHandler.noCompatibleArtifactFailure(matcher, producer, targetAttributes);
    }

    @Nullable
    private static ResolvedVariant findPreviouslyUnselectable(ResolvedVariantSet producer) {
        for (ResolvedVariant candidate : producer.getCandidates()) {
            if (candidate.isPreviouslyUnselectable()) {
                return candidate;
            }
        }
        return null;
    }

    private static void nagAboutIgnoredVariant(ResolvedVariantSet producer, ResolvedVariant ignoredVariant) {
        DeprecationLogger.deprecateBehaviour(
                "Selecting artifacts from " + producer.asDescribable().getDisplayName() + " ignored variant '" + ignoredVariant.asDescribable().getDisplayName() + "' because it has no artifacts."
            )
            .withAdvice(
                "Add attributes to the request so that it only matches the intended variant, or enable the '" +
                    FeaturePreviews.Feature.CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS.name() + "' feature preview to opt into the new behavior."
            )
            .startingWithGradle10("primary variants without artifacts will be selected when they are the best match")
            .withUpgradeGuideSection(9, "variants_with_no_artifacts")
            .nagUser();
    }

    /**
     * Selection among a set of variants. The matching variants and the chain of transformations are each looked up at most once,
     * when first needed, so that deciding whether to select the primary variant without artifacts and the selection that follows
     * share their queries.
     */
    private final class VariantSelection {
        private final ResolvedVariantSet variants;
        private final ImmutableAttributes targetAttributes;
        private final AttributeMatcher matcher;

        @Nullable
        private List<ResolvedVariant> matches;
        private boolean transformationChainSelected;
        @Nullable
        private TransformedVariant transformationChain;
        @Nullable
        private AbstractResolutionFailureException transformationChainFailure;

        VariantSelection(ResolvedVariantSet variants, ImmutableAttributes targetAttributes, AttributeMatcher matcher) {
            this.variants = variants;
            this.targetAttributes = targetAttributes;
            this.matcher = matcher;
        }

        /**
         * Returns the variants that match the requested attributes directly.
         */
        List<ResolvedVariant> getMatches() {
            if (matches == null) {
                matches = matcher.matchMultipleCandidates(variants.getCandidates(), targetAttributes);
            }
            return matches;
        }

        /**
         * Returns the chain of transformations that produces a suitable virtual variant, or {@code null} if there is none.
         *
         * @throws AbstractResolutionFailureException if several chains match and cannot be disambiguated
         */
        @Nullable
        private TransformedVariant getTransformationChain() {
            if (!transformationChainSelected) {
                transformationChainSelected = true;
                try {
                    transformationChain = transformationChainSelector.selectTransformationChain(variants, targetAttributes, matcher).orElse(null);
                } catch (AbstractResolutionFailureException e) {
                    transformationChainFailure = e;
                }
            }
            if (transformationChainFailure != null) {
                throw transformationChainFailure;
            }
            return transformationChain;
        }

        /**
         * Can anything be selected, directly or through a transformation?
         * <p>
         * Ambiguous transformations count, since selecting reports them.
         */
        boolean isAnyVariantSelectable() {
            if (!getMatches().isEmpty()) {
                return true;
            }
            try {
                return getTransformationChain() != null;
            } catch (AbstractResolutionFailureException e) {
                return true;
            }
        }

        /**
         * Returns the matched artifacts, or the artifacts of a chain of transformations that produces a suitable virtual variant,
         * or {@code null} if neither exists.
         */
        @Nullable
        ResolvedArtifactSet select() {
            // Check for matching variant without using artifact transforms.  If we found only one match, return it.
            // If we found multiple matches, there is ambiguity.
            List<ResolvedVariant> matchingVariants = getMatches();
            if (matchingVariants.size() == 1) {
                return matchingVariants.get(0).getArtifacts();
            } else if (matchingVariants.size() > 1) {
                throw failureHandler.ambiguousArtifactsFailure(matcher, variants, targetAttributes, matchingVariants);
            }

            // We found no matching variant.  Attempt to select a chain of transformations that produces a suitable virtual variant.
            TransformedVariant selectedTransformationChain = getTransformationChain();
            return selectedTransformationChain != null
                ? variants.transformCandidate(selectedTransformationChain.getRoot(), selectedTransformationChain.getTransformedVariantDefinition())
                : null;
        }
    }

    /**
     * A view of a producer without its primary variant without artifacts, matching the candidates prior to Gradle 9.10.
     */
    private static final class WithoutPreviouslyUnselectableVariants implements ResolvedVariantSet {
        private final ResolvedVariantSet delegate;
        private final List<ResolvedVariant> candidates;

        WithoutPreviouslyUnselectableVariants(ResolvedVariantSet delegate) {
            this.delegate = delegate;
            this.candidates = delegate.getCandidates().stream()
                .filter(candidate -> !candidate.isPreviouslyUnselectable())
                .collect(Collectors.toList());
        }

        @Override
        public ComponentIdentifier getComponentIdentifier() {
            return delegate.getComponentIdentifier();
        }

        @Override
        public Describable asDescribable() {
            return delegate.asDescribable();
        }

        @Override
        public ImmutableAttributesSchema getProducerSchema() {
            return delegate.getProducerSchema();
        }

        @Override
        public List<ResolvedVariant> getCandidates() {
            return candidates;
        }

        @Override
        public ImmutableAttributes getOverriddenAttributes() {
            return delegate.getOverriddenAttributes();
        }

        @Override
        public ResolvedArtifactSet transformCandidate(ResolvedVariant sourceVariant, VariantDefinition variantDefinition) {
            return delegate.transformCandidate(sourceVariant, variantDefinition);
        }
    }
}
