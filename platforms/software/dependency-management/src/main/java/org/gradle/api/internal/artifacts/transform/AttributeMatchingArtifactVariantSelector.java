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
import com.google.common.collect.Sets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.component.ArtifactVariantSelectionException;
import org.gradle.internal.component.SelectionFailureHandler;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder;
import org.gradle.internal.component.model.DescriberSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A {@link ArtifactVariantSelector} that uses attribute matching to select a matching set of artifacts.
 *
 * If no producer variant is compatible with the requested attributes, this selector will attempt to construct a chain of artifact
 * transforms that can produce a variant compatible with the requested attributes.
 *
 * An instance of {@link SelectionFailureHandler} is injected in the constructor
 * to allow the caller to handle failures in a consistent manner as during graph variant selection.
 */
public class AttributeMatchingArtifactVariantSelector implements ArtifactVariantSelector {
    private final ConsumerProvidedVariantFinder consumerProvidedVariantFinder;
    private final AttributesSchemaInternal schema;
    private final ImmutableAttributesFactory attributesFactory;
    private final TransformedVariantFactory transformedVariantFactory;
    private final TransformUpstreamDependenciesResolverFactory dependenciesResolverFactory;
    private final SelectionFailureHandler failureProcessor;

    AttributeMatchingArtifactVariantSelector(
        ConsumerProvidedVariantFinder consumerProvidedVariantFinder,
        AttributesSchemaInternal schema,
        ImmutableAttributesFactory attributesFactory,
        TransformedVariantFactory transformedVariantFactory,
        TransformUpstreamDependenciesResolverFactory dependenciesResolverFactory,
        SelectionFailureHandler failureProcessor
    ) {
        this.consumerProvidedVariantFinder = consumerProvidedVariantFinder;
        this.schema = schema;
        this.attributesFactory = attributesFactory;
        this.transformedVariantFactory = transformedVariantFactory;
        this.dependenciesResolverFactory = dependenciesResolverFactory;
        this.failureProcessor = failureProcessor;
    }

    @Override
    public ResolvedArtifactSet select(ResolvedVariantSet producer, ImmutableAttributes requestAttributes, boolean allowNoMatchingVariants, ResolvedArtifactTransformer resolvedArtifactTransformer) {
        try {
            return doSelect(producer, allowNoMatchingVariants, resolvedArtifactTransformer, AttributeMatchingExplanationBuilder.logging(), requestAttributes);
        } catch (ArtifactVariantSelectionException t) {
            return failureProcessor.unknownArtifactVariantSelectionFailure(schema, t);
        } catch (Exception t) {
            return failureProcessor.unknownArtifactVariantSelectionFailure(schema, producer, t);
        }
    }

    private ResolvedArtifactSet doSelect(ResolvedVariantSet producer, boolean allowNoMatchingVariants, ResolvedArtifactTransformer resolvedArtifactTransformer, AttributeMatchingExplanationBuilder explanationBuilder, ImmutableAttributes requestAttributes) {
        AttributeMatcher matcher = schema.withProducer(producer.getSchema());
        ImmutableAttributes componentRequested = attributesFactory.concat(requestAttributes, producer.getOverriddenAttributes());
        final List<ResolvedVariant> variants = ImmutableList.copyOf(producer.getVariants());

        List<? extends ResolvedVariant> matches = matcher.matches(variants, componentRequested, explanationBuilder);
        if (matches.size() == 1) {
            return matches.get(0).getArtifacts();
        } else if (matches.size() > 1) {
            // Request is ambiguous. Rerun matching again, except capture an explanation this time for reporting.
            TraceDiscardedVariants newExpBuilder = new TraceDiscardedVariants();
            matches = matcher.matches(variants, componentRequested, newExpBuilder);

            Set<ResolvedVariant> discarded = Cast.uncheckedCast(newExpBuilder.discarded);
            AttributeDescriber describer = DescriberSelector.selectDescriber(componentRequested, schema);
            throw failureProcessor.ambiguousArtifactVariantsFailure(schema, producer.asDescribable().getDisplayName(), componentRequested, matches, matcher, discarded, describer);
        }

        // We found no matches. Attempt to construct artifact transform chains which produce matching variants.
        List<TransformedVariant> transformedVariants = consumerProvidedVariantFinder.findTransformedVariants(variants, componentRequested);

        // If there are multiple potential artifact transform variants, perform attribute matching to attempt to find the best.
        if (transformedVariants.size() > 1) {
            transformedVariants = tryDisambiguate(matcher, transformedVariants, componentRequested, explanationBuilder);
        }

        if (transformedVariants.size() == 1) {
            TransformedVariant result = transformedVariants.get(0);
            return resolvedArtifactTransformer.asTransformed(result.getRoot(), result.getTransformedVariantDefinition(), dependenciesResolverFactory, transformedVariantFactory);
        }

        if (!transformedVariants.isEmpty()) {
            throw failureProcessor.ambiguousArtifactTransformationFailure(schema, producer.asDescribable().getDisplayName(), componentRequested, transformedVariants);
        }

        if (allowNoMatchingVariants) {
            return ResolvedArtifactSet.EMPTY;
        }

        throw failureProcessor.noMatchingArtifactVariantFailure(schema, producer.asDescribable().getDisplayName(), componentRequested, variants, matcher, DescriberSelector.selectDescriber(componentRequested, schema));
    }

    /**
     * Attempt to disambiguate between multiple potential transform candidates. This first performs attribute matching on the {@code candidates}.
     * If that does not produce a single result, then a subset of the results of attribute matching is returned, where the candidates which have
     * incompatible attributes values with the <strong>last</strong> candidate are included.
     */
    private static List<TransformedVariant> tryDisambiguate(
        AttributeMatcher matcher,
        List<TransformedVariant> candidates,
        ImmutableAttributes componentRequested,
        AttributeMatchingExplanationBuilder explanationBuilder
    ) {
        List<TransformedVariant> matches = matcher.matches(candidates, componentRequested, explanationBuilder);
        if (matches.size() == 1) {
            return matches;
        }

        assert matches.size() > 0;

        List<TransformedVariant> differentTransforms = new ArrayList<>(1);

        // Choosing the last candidate here is arbitrary.
        TransformedVariant last = matches.get(matches.size() - 1);
        differentTransforms.add(last);

        // Find any other candidate which does not match with the last candidate.
        for (int i = 0; i < matches.size() - 1; i++) {
            TransformedVariant current = matches.get(i);
            if (candidatesDifferent(matcher, current, last)) {
                differentTransforms.add(current);
            }
        }

        return differentTransforms;
    }

    /**
     * Determines whether two candidates differ based on their attributes.
     *
     * @return true if for each shared candidate key, according to the attribute schema, the corresponding attribute value
     *      in each candidate is compatible. false otherwise.
     */
    private static boolean candidatesDifferent(AttributeMatcher matcher, TransformedVariant firstCandidate, TransformedVariant secondCandidate) {
        // We check both directions to verify these candidates differ. If a.matches(b) but !b.matches(a), we still consider variants a and b to be matching.
        // This is because attribute schema compatibility rules can be directional, where for two attribute values x and y, x may be compatible with y
        // while y may not be compatible with x. We accept compatibility in either direction as sufficient for this method.
        return !matcher.isMatching(firstCandidate.getAttributes(), secondCandidate.getAttributes()) &&
            !matcher.isMatching(secondCandidate.getAttributes(), firstCandidate.getAttributes());
    }

    private static class TraceDiscardedVariants implements AttributeMatchingExplanationBuilder {

        private final Set<HasAttributes> discarded = Sets.newHashSet();

        @Override
        public boolean canSkipExplanation() {
            return false;
        }

        @Override
        public <T extends HasAttributes> void candidateDoesNotMatchAttributes(T candidate, AttributeContainerInternal requested) {
            recordDiscardedCandidate(candidate);
        }

        public <T extends HasAttributes> void recordDiscardedCandidate(T candidate) {
            discarded.add(candidate);
        }

        @Override
        public <T extends HasAttributes> void candidateAttributeDoesNotMatch(T candidate, Attribute<?> attribute, Object requestedValue, AttributeValue<?> candidateValue) {
            recordDiscardedCandidate(candidate);
        }
    }
}
