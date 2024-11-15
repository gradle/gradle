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
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;

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

    public AttributeMatchingArtifactVariantSelector(
        ImmutableAttributesSchema consumerSchema,
        ConsumerProvidedVariantFinder transformationChainBuilder,
        AttributesFactory attributesFactory,
        AttributeSchemaServices attributeSchemaServices,
        ResolutionFailureHandler failureHandler
    ) {
        this.consumerSchema = consumerSchema;
        this.attributesFactory = attributesFactory;
        this.attributeSchemaServices = attributeSchemaServices;
        this.failureHandler = failureHandler;
        this.transformationChainSelector = new TransformationChainSelector(transformationChainBuilder, failureHandler);
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

        // Check for matching variant without using artifact transforms.  If we found only one, return it.  If we found multiple matches, that's ambiguity.
        List<ResolvedVariant> matchingVariants = matcher.matchMultipleCandidates(producer.getCandidates(), targetAttributes);
        if (matchingVariants.size() == 1) {
            return Iterables.getOnlyElement(matchingVariants).getArtifacts();
        } else if (matchingVariants.size() > 1) {
            throw failureHandler.ambiguousArtifactsFailure(matcher, producer, targetAttributes, matchingVariants);
        }

        // We found no matching variant.  Attempt to select a chain of transformations that produces a suitable virtual variant.
        Optional<TransformedVariant> selectedTransformationChain = transformationChainSelector.selectTransformationChain(producer, targetAttributes, matcher);
        if (selectedTransformationChain.isPresent()) {
            return producer.transformCandidate(selectedTransformationChain.get().getRoot(), selectedTransformationChain.get().getTransformedVariantDefinition());
        }

        // At this point, there is no possibility of a match for the request.  That could be okay if allowed, else it's a failure.
        if (allowNoMatchingVariants) {
            return ResolvedArtifactSet.EMPTY;
        } else {
            throw failureHandler.noCompatibleArtifactFailure(matcher, producer, targetAttributes);
        }
    }
}
