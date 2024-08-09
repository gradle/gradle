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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;

import java.util.ArrayList;
import java.util.List;

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
    private final ConsumerProvidedVariantFinder consumerProvidedVariantFinder;
    private final AttributesSchemaInternal schema;
    private final ImmutableAttributesFactory attributesFactory;
    private final TransformedVariantFactory transformedVariantFactory;
    private final TransformUpstreamDependenciesResolverFactory dependenciesResolverFactory;
    private final ResolutionFailureHandler failureProcessor;

    AttributeMatchingArtifactVariantSelector(
        ConsumerProvidedVariantFinder consumerProvidedVariantFinder,
        AttributesSchemaInternal schema,
        ImmutableAttributesFactory attributesFactory,
        TransformedVariantFactory transformedVariantFactory,
        TransformUpstreamDependenciesResolverFactory dependenciesResolverFactory,
        ResolutionFailureHandler failureProcessor
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
        } catch (Exception t) {
            return new BrokenResolvedArtifactSet(failureProcessor.unknownArtifactVariantSelectionFailure(schema, producer, requestAttributes, t));
        }
    }

    private ResolvedArtifactSet doSelect(ResolvedVariantSet producer, boolean allowNoMatchingVariants, ResolvedArtifactTransformer resolvedArtifactTransformer, AttributeMatchingExplanationBuilder explanationBuilder, ImmutableAttributes requestAttributes) {
        AttributeMatcher matcher = schema.withProducer(producer.getSchema());
        ImmutableAttributes componentRequested = attributesFactory.concat(requestAttributes, producer.getOverriddenAttributes());
        final List<ResolvedVariant> variants = producer.getVariants();

        List<? extends ResolvedVariant> matches = matcher.matchMultipleCandidates(variants, componentRequested, explanationBuilder);
        if (matches.size() == 1) {
            return matches.get(0).getArtifacts();
        } else if (matches.size() > 1) {
            throw failureProcessor.ambiguousArtifactsFailure(schema, matcher, producer, componentRequested, matches);
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
            throw failureProcessor.ambiguousArtifactTransformsFailure(schema, producer, componentRequested, transformedVariants);
        }

        if (allowNoMatchingVariants) {
            return ResolvedArtifactSet.EMPTY;
        }

        throw failureProcessor.noCompatibleArtifactFailure(schema, matcher, producer, componentRequested, variants);
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
        AttributeMatchingExplanationBuilder explanationBuilder
    ) {
        List<TransformedVariant> matches = matcher.matchMultipleCandidates(candidates, componentRequested, explanationBuilder);
        if (matches.size() == 1) {
            return matches;
        }

        assert !matches.isEmpty();

        List<TransformedVariant> differentTransforms = new ArrayList<>(1);

        // Choosing the last candidate here is arbitrary.
        TransformedVariant last = matches.get(matches.size() - 1);
        differentTransforms.add(last);

        // Find any other candidate which does not match with the last candidate.
        for (int i = 0; i < matches.size() - 1; i++) {
            TransformedVariant current = matches.get(i);
            if (!matcher.areMutuallyCompatible(current.getAttributes(), last.getAttributes())) {
                differentTransforms.add(current);
            }
        }

        return differentTransforms;
    }
}
