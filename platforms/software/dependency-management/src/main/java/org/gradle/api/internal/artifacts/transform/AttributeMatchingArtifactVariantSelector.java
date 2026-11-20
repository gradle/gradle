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
import java.util.Optional;

/**
 * A {@link ArtifactVariantSelector} that uses attribute matching to select a matching set of artifacts.
 * <p>
 * If no producer variant is compatible with the requested attributes, this selector will attempt to construct a chain of artifact
 * transforms that can produce a variant compatible with the requested attributes.
 * <p>
 * A primary variant without artifacts whose configuration declares secondary variants is selected when it is the best match.
 * Since it lacks the attributes of artifacts, only the requested attributes decide whether it is a better match than a variant
 * with artifacts, and a transformation of a variant with artifacts is preferred over it unless it declares every requested attribute.
 * Prior to Gradle 11, selecting it requires the {@link FeaturePreviews.Feature#CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS} feature preview.
 * Without it, such a variant is not eligible for selection, and a deprecation warning is emitted when selecting it would change the result.
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
    private final boolean artifactlessPrimaryVariantSelectionEnabled;

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
            VariantSelectionStrategy selectionStrategy = determineSelectionStrategy(producer, attributeMatcher, targetAttributes);

            return selectionStrategy.select(allowNoMatchingVariants);
        } catch (Exception t) {
            return new BrokenResolvedArtifactSet(failureHandler.unknownArtifactVariantSelectionFailure(producer, requestAttributes, t));
        }
    }

    private VariantSelectionStrategy determineSelectionStrategy(
        ResolvedVariantSet producer,
        AttributeMatcher attributeMatcher,
        ImmutableAttributes targetAttributes
    ) {
        // A configuration whose primary variant has no artifacts, but which declares secondary variants, keeps its
        // primary variant as a candidate, marked as previously unselectable. Prior to Gradle 9.10, such a variant was
        // dropped before selection.
        Optional<ResolvedVariant> primaryWithoutArtifacts = findPrimaryWithoutArtifacts(producer);

        // Selects among all candidates, as Gradle always has. Its candidates include the primary variant without artifacts,
        // so this is only used independently if such a primary doesn't exist. Otherwise, the new strategy builds on it.
        SimpleSelectionStrategy simpleSelectionStrategy = new SimpleSelectionStrategy(producer, targetAttributes, attributeMatcher);

        if (primaryWithoutArtifacts.isEmpty()) {
            // If no primary variant without artifacts, selection works the same as it always has.
            return simpleSelectionStrategy;
        } else {
            // Primary variant without artifacts exists; we need a new strategy.
            NewVariantSelectionStrategy newVariantStrategy = new NewVariantSelectionStrategy(simpleSelectionStrategy, primaryWithoutArtifacts.get());
            if (artifactlessPrimaryVariantSelectionEnabled) {
                // With the feature preview, select as we will do starting with Gradle 11, when the preview becomes the default.
                return newVariantStrategy;
            } else {
                // Without it, the primary variant is not eligible, but we'll warn if it WOULD be selected with the preview, so that people can adapt their builds.
                return new AdvanceWarningVariantSelectionStrategy(newVariantStrategy);
            }
        }
    }

    /**
     * Returns the primary variant without artifacts among the producer's candidates, if there is one.
     * <p>
     * This runs for every selection from every producer, most of which have no such variant, so it loops rather than streams to avoid allocating.
     */
    private static Optional<ResolvedVariant> findPrimaryWithoutArtifacts(ResolvedVariantSet producer) {
        for (ResolvedVariant candidate : producer.getCandidates()) {
            if (candidate.isPreviouslyUnselectable()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * A way of selecting artifacts (producing a {@link ResolvedArtifactSet}) from a set of variants.
     */
    private interface VariantSelectionStrategy {
        /**
         * Selects the artifacts that match the request, or fails if none match.
         *
         * @return the selected artifacts, or {@link ResolvedArtifactSet#EMPTY} if none match and {@code allowNoMatchingVariants} is true
         * @throws AbstractResolutionFailureException if the match is ambiguous, or if none match and {@code allowNoMatchingVariants} is false
         */
        ResolvedArtifactSet select(boolean allowNoMatchingVariants);
    }

    /**
     * Selects among a set of variants as Gradle always has: a single direct match, or else a single chain of transformations.
     * <p>
     * {@link NewVariantSelectionStrategy} builds on it, and queries it both to decide about the primary variant without artifacts and to select.
     * So a {@code SimpleSelectionStrategy} computes nothing up front. It answers three questions, computing each answer at most once:
     * <ul>
     *     <li>{@link #getMatches()}: the direct attribute matches, after the matcher's disambiguation.</li>
     *     <li>{@link #getTransformationChain()}: the single best chain of transformations. It throws if the chains are ambiguous, and remembers that failure.</li>
     *     <li>{@link #selectIfMatching()}: one match returns that variant's artifacts. Several matches throw an ambiguity failure. Zero matches fall back to the chain:
     *     its transformed artifacts, or {@code null} if there is no chain.</li>
     * </ul>
     */
    private final class SimpleSelectionStrategy implements VariantSelectionStrategy {
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

        SimpleSelectionStrategy(ResolvedVariantSet variants, ImmutableAttributes targetAttributes, AttributeMatcher matcher) {
            this.variants = variants;
            this.targetAttributes = targetAttributes;
            this.matcher = matcher;
        }

        /**
         * Returns a selection for the same request among these variants, without the given variant.
         */
        SimpleSelectionStrategy excluding(ResolvedVariant variant) {
            ImmutableList<ResolvedVariant> others = variants.getCandidates().stream()
                .filter(candidate -> candidate != variant)
                .collect(ImmutableList.toImmutableList());
            return new SimpleSelectionStrategy(variants.subset(others), targetAttributes, matcher);
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
         * Does any variant match the request directly, or through a chain of transformations?
         * <p>
         * Ambiguous chains count, since selecting reports them.
         */
        boolean matchesDirectlyOrViaTransformations() {
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
         * Do several chains of transformations match the request, so that they cannot be disambiguated?
         */
        boolean hasAmbiguousTransformationChains() {
            try {
                getTransformationChain();
                return false;
            } catch (AbstractResolutionFailureException e) {
                return true;
            }
        }

        @Override
        public ResolvedArtifactSet select(boolean allowNoMatchingVariants) {
            return selectOrFail(variants, allowNoMatchingVariants);
        }

        /**
         * Selects as {@link #select(boolean)} does, but fails listing the reported variants as the candidates if nothing matches.
         * <p>
         * When nothing matches, that could be okay (and just a no match) if this is allowed, else it's a thrown failure.
         *
         * @throws AbstractResolutionFailureException if the match is ambiguous, or if nothing matches and {@code allowNoMatchingVariants} is false
         */
        ResolvedArtifactSet selectOrFail(ResolvedVariantSet reportedVariants, boolean allowNoMatchingVariants) {
            ResolvedArtifactSet selected = selectIfMatching();
            if (selected != null) {
                return selected;
            }
            if (allowNoMatchingVariants) {
                return ResolvedArtifactSet.EMPTY;
            }
            throw failureHandler.noCompatibleArtifactFailure(matcher, reportedVariants, targetAttributes);
        }

        /**
         * Returns the matched artifacts, or the artifacts of a chain of transformations that produces a suitable virtual variant,
         * or {@code null} if neither exists.
         *
         * @throws AbstractResolutionFailureException if several variants match, or several chains match and cannot be disambiguated
         */
        @Nullable
        ResolvedArtifactSet selectIfMatching() {
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
     * Selects artifacts as will be the default starting in Gradle 11, from a producer whose primary variant has no artifacts,
     * by choosing between two selections over different candidates.
     * <ul>
     *     <li>{@code simpleSelectionStrategyWithEverything} selects among all candidates, including the primary variant without artifacts. Left to itself, the attribute
     *     matcher could favor that variant merely because it lacks the attributes of artifacts, such as {@code artifactType}, that the other variants declare.</li>
     *     <li>{@code simpleSelectionStrategyWithoutPrimary} selects among the other variants only, as prior to Gradle 9.10.</li>
     * </ul>
     * <p>
     * {@link #wouldPrimaryWithoutArtifactsBeSelected()} chooses between them. It compares the primary variant with the direct matches of {@code simpleSelectionStrategyWithoutPrimary}
     * on the requested attributes only, and asks both selections whether anything matches through a transformation.
     * <ul>
     *     <li>If it chooses the primary variant, and that variant matches directly, its artifacts are returned. Otherwise, only a transformation of it matches,
     *     so {@code simpleSelectionStrategyWithEverything} selects that transformation, or fails if its chains of transformations are ambiguous.</li>
     *     <li>Otherwise, {@code simpleSelectionStrategyWithoutPrimary} selects, but if nothing matches, the failure lists all candidates, including the primary variant.</li>
     * </ul>
     * <p>
     * Both selections compute nothing up front, so each of their queries runs at most once, whichever of deciding or selecting needs it first.
     */
    private final class NewVariantSelectionStrategy implements VariantSelectionStrategy {
        private final ResolvedVariantSet variants;
        private final ResolvedVariant primaryWithoutArtifacts;
        private final ImmutableAttributes targetAttributes;
        private final AttributeMatcher matcher;
        private final SimpleSelectionStrategy simpleSelectionStrategyWithEverything;
        private final SimpleSelectionStrategy simpleSelectionStrategyWithoutPrimary;

        NewVariantSelectionStrategy(SimpleSelectionStrategy simpleSelectionStrategy, ResolvedVariant primaryWithoutArtifacts) {
            this.variants = simpleSelectionStrategy.variants;
            this.primaryWithoutArtifacts = primaryWithoutArtifacts;
            this.targetAttributes = simpleSelectionStrategy.targetAttributes;
            this.matcher = simpleSelectionStrategy.matcher;
            this.simpleSelectionStrategyWithEverything = simpleSelectionStrategy;
            this.simpleSelectionStrategyWithoutPrimary = simpleSelectionStrategy.excluding(primaryWithoutArtifacts);
        }

        @Override
        public ResolvedArtifactSet select(boolean allowNoMatchingVariants) {
            if (wouldPrimaryWithoutArtifactsBeSelected()) {
                // The primary variant matches directly, or only a transformation of it matches.
                return primaryMatchesDirectly() ? primaryWithoutArtifacts.getArtifacts() : simpleSelectionStrategyWithEverything.select(allowNoMatchingVariants);
            } else {
                // The primary variant without artifacts is not selected, so select among the other variants.
                // It is still eligible, so if nothing matches, the failure lists it among the candidates with why it does not match.
                return simpleSelectionStrategyWithoutPrimary.selectOrFail(variants, allowNoMatchingVariants);
            }
        }

        /**
         * Would the primary variant without artifacts, or a transformation of it, be selected starting with Gradle 11?
         * <p>
         * It is selected when:
         * <ul>
         *     <li>It matches the requested attributes strictly better than every other matching variant. Only the requested attributes count,
         *     so that lacking an attribute, such as the {@code artifactType} of its missing artifacts, does not make it a better match.</li>
         *     <li>No other variant matches directly, and it declares every requested attribute, so it is preferred over a transformation of another variant.</li>
         *     <li>Nothing else matches, either directly or through a transformation.</li>
         * </ul>
         * <p>
         * Ambiguous chains of transformations count as matching. So when the other variants' chains are ambiguous, a primary variant that matches
         * directly, but does not declare every requested attribute, is not selected, and selecting fails with that ambiguity.
         */
        boolean wouldPrimaryWithoutArtifactsBeSelected() {
            if (!primaryMatchesDirectly()) {
                // Only a transformation of the primary variant can match. Ambiguous chains count as matching, so if the primary
                // variant's chains are ambiguous, it is considered selected, and selecting then fails with that ambiguity instead.
                return !simpleSelectionStrategyWithoutPrimary.matchesDirectlyOrViaTransformations() && simpleSelectionStrategyWithEverything.matchesDirectlyOrViaTransformations();
            }
            List<ResolvedVariant> otherMatches = simpleSelectionStrategyWithoutPrimary.getMatches();
            if (!otherMatches.isEmpty()) {
                return isStrictlyBetterMatch(primaryWithoutArtifacts, otherMatches);
            }
            return declaresAllAttributes(primaryWithoutArtifacts) || !simpleSelectionStrategyWithoutPrimary.matchesDirectlyOrViaTransformations();
        }

        /**
         * Would selecting fail starting with Gradle 11, since the primary variant without artifacts is selected, but only ambiguous chains of transformations of it match?
         * <p>
         * Only meaningful when {@link #wouldPrimaryWithoutArtifactsBeSelected()} is true.
         */
        boolean wouldSelectingPrimaryWithoutArtifactsFail() {
            return !primaryMatchesDirectly() && simpleSelectionStrategyWithEverything.hasAmbiguousTransformationChains();
        }

        private boolean primaryMatchesDirectly() {
            return matcher.isMatchingCandidate(primaryWithoutArtifacts.getAttributes(), targetAttributes);
        }

        /**
         * Is the candidate the only best match among itself and the others, comparing only their requested attributes,
         * so that the remaining attributes cannot disambiguate them?
         */
        private boolean isStrictlyBetterMatch(ResolvedVariant candidate, List<ResolvedVariant> others) {
            List<AttributeMatchingCandidate> requestedOnly = new ArrayList<>(others.size() + 1);
            requestedOnly.add(withRequestedAttributesOnly(candidate));
            for (ResolvedVariant other : others) {
                requestedOnly.add(withRequestedAttributesOnly(other));
            }
            List<AttributeMatchingCandidate> matches = matcher.matchMultipleCandidates(requestedOnly, targetAttributes);
            return matches.size() == 1 && matches.get(0) == requestedOnly.get(0);
        }

        private AttributeMatchingCandidate withRequestedAttributesOnly(ResolvedVariant variant) {
            ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
            for (Attribute<?> attribute : targetAttributes.keySet()) {
                ImmutableAttributesEntry<?> entry = variant.getAttributes().findEntry(attribute.getName());
                if (entry != null) {
                    attributes = append(attributes, entry);
                }
            }
            ImmutableAttributes requestedOnly = attributes;
            return () -> requestedOnly;
        }

        /**
         * Captures the entry's wildcard, so that its key and value share a type.
         */
        private <T> ImmutableAttributes append(ImmutableAttributes attributes, ImmutableAttributesEntry<T> entry) {
            return attributesFactory.concat(attributes, entry.getKey(), entry.getValue());
        }

        private boolean declaresAllAttributes(ResolvedVariant variant) {
            return targetAttributes.keySet().stream()
                .allMatch(attribute -> variant.getAttributes().findEntry(attribute.getName()) != null);
        }
    }

    /**
     * Selects artifacts as Gradle did prior to 9.10, ignoring the primary variant without artifacts, and warns when the selection
     * starting with Gradle 11 would select that variant, or a transformation of it, instead.
     * <p>
     * It delegates to a {@link NewVariantSelectionStrategy} without calling its {@code select}. It asks the delegate whether
     * the primary variant would be selected, to decide whether to warn, and selects with the delegate's {@code simpleSelectionStrategyWithoutPrimary},
     * which excludes the primary variant. Both share the delegate's cached matches and transformation chains.
     * <p>
     * A lenient request that no other variant matches resolves to no artifacts either way, so it does not warn, unless only ambiguous
     * chains of transformations of the primary variant match, since selecting those fails starting with Gradle 11.
     * <p>
     * Deciding whether to warn evaluates the primary variant, which selecting prior to Gradle 9.10 never did. So a failure while deciding
     * skips the warning, rather than failing a selection that would otherwise succeed.
     */
    private final class AdvanceWarningVariantSelectionStrategy implements VariantSelectionStrategy {
        private final NewVariantSelectionStrategy newVariantSelectionStrategy;

        AdvanceWarningVariantSelectionStrategy(NewVariantSelectionStrategy newVariantSelectionStrategy) {
            this.newVariantSelectionStrategy = newVariantSelectionStrategy;
        }

        @Override
        public ResolvedArtifactSet select(boolean allowNoMatchingVariants) {
            if (resultWillChange(allowNoMatchingVariants)) {
                nagAboutIgnoredVariant();
            }
            // Prior to Gradle 9.10, the primary variant was dropped before selection, so a failure lists only the other variants
            return newVariantSelectionStrategy.simpleSelectionStrategyWithoutPrimary.select(allowNoMatchingVariants);
        }

        /**
         * Will the result change starting with Gradle 11, where the primary variant, or a transformation of it, resolves to no artifacts?
         * <ul>
         *     <li>If another variant matches, its artifacts are selected now, or selecting fails if the match is ambiguous, so the result will change.</li>
         *     <li>If no other variant matches and the request is strict, selecting fails now, so the result will change.</li>
         *     <li>If no other variant matches and the request is lenient, it resolves to no artifacts now, so the result is the same,
         *     unless selecting the primary variant fails since only ambiguous chains of transformations of it match.</li>
         * </ul>
         */
        private boolean resultWillChange(boolean allowNoMatchingVariants) {
            try {
                return newVariantSelectionStrategy.wouldPrimaryWithoutArtifactsBeSelected()
                    && (newVariantSelectionStrategy.simpleSelectionStrategyWithoutPrimary.matchesDirectlyOrViaTransformations()
                        || !allowNoMatchingVariants
                        || newVariantSelectionStrategy.wouldSelectingPrimaryWithoutArtifactsFail());
            } catch (RuntimeException e) {
                // Deciding must not fail a selection that does not involve the primary variant
                return false;
            }
        }

        private void nagAboutIgnoredVariant() {
            DeprecationLogger.deprecateBehaviour(
                    "Selecting artifacts from " + newVariantSelectionStrategy.variants.asDescribable().getDisplayName() + " ignored variant '" + newVariantSelectionStrategy.primaryWithoutArtifacts.asDescribable().getDisplayName() + "' because it has no artifacts."
                )
                .withAdvice(
                    "Add attributes to the request so that it only matches the intended variant, or enable the '" +
                        FeaturePreviews.Feature.CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS.name() + "' feature preview to opt into the new behavior."
                )
                .startingWithGradle11("primary variants without artifacts will be selected when they are the best match")
                .withUpgradeGuideSection(9, "variants_with_no_artifacts")
                .nagUser();
        }
    }
}
