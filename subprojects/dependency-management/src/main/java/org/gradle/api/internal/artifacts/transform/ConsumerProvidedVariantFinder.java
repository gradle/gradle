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

import com.google.common.collect.Maps;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ArtifactTransformRegistration;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.model.AttributeMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finds all the variants that can be created from a given producer variant using
 * the consumer's variant transformations. Transformations can be chained. If multiple
 * chains can lead to the same outcome, the shortest path is selected.
 *
 * Caches the results, as often the same request is made for many components in a
 * dependency graph.
 */
public class ConsumerProvidedVariantFinder {
    private final VariantTransformRegistry variantTransforms;
    private final AttributesSchemaInternal schema;
    private final ImmutableAttributesFactory attributesFactory;
    private final Map<AttributeContainer, AttributeSpecificCache> attributeSpecificCache = Maps.newConcurrentMap();

    public ConsumerProvidedVariantFinder(VariantTransformRegistry variantTransforms, AttributesSchemaInternal schema, ImmutableAttributesFactory attributesFactory) {
        this.variantTransforms = variantTransforms;
        this.schema = schema;
        this.attributesFactory = attributesFactory;
    }

    public ConsumerVariantMatchResult collectConsumerVariants(AttributeContainerInternal actual, AttributeContainerInternal requested) {
        AttributeSpecificCache toCache = getCache(requested);
        return toCache.transforms.computeIfAbsent(actual, attrs -> findProducersFor(actual, requested).asImmutable());
    }

    private MutableConsumerVariantMatchResult findProducersFor(AttributeContainerInternal actual, AttributeContainerInternal requested) {
        // Prefer direct transformation over indirect transformation
        List<ArtifactTransformRegistration> candidates = new ArrayList<>();
        List<ArtifactTransformRegistration> transforms = variantTransforms.getTransforms();
        int nbOfTransforms = transforms.size();
        MutableConsumerVariantMatchResult result = new MutableConsumerVariantMatchResult(nbOfTransforms * nbOfTransforms);
        for (ArtifactTransformRegistration registration : transforms) {
            if (matchAttributes(registration.getTo(), requested)) {
                if (matchAttributes(actual, registration.getFrom())) {
                    ImmutableAttributes variantAttributes = attributesFactory.concat(actual.asImmutable(), registration.getTo().asImmutable());
                    if (matchAttributes(variantAttributes, requested)) {
                        result.matched(variantAttributes, registration.getTransformationStep(), 1);
                    }
                }
                candidates.add(registration);
            }
        }
        if (result.hasMatches()) {
            return result;
        }

        for (ArtifactTransformRegistration candidate : candidates) {
            AttributeContainerInternal requestedPrevious = computeRequestedAttributes(requested, candidate);
            ConsumerVariantMatchResult inputVariants = collectConsumerVariants(actual, requestedPrevious);
            if (!inputVariants.hasMatches()) {
                continue;
            }
            for (MutableConsumerVariantMatchResult.ConsumerVariant inputVariant : inputVariants.getMatches()) {
                ImmutableAttributes variantAttributes = attributesFactory.concat(inputVariant.attributes.asImmutable(), candidate.getTo().asImmutable());
                Transformation transformation = new TransformationChain(inputVariant.transformation, candidate.getTransformationStep());
                result.matched(variantAttributes, transformation, inputVariant.depth + 1);
            }
        }
        return result;
    }

    private AttributeContainerInternal computeRequestedAttributes(AttributeContainerInternal result, ArtifactTransformRegistration transform) {
        return attributesFactory.concat(result.asImmutable(), transform.getFrom().asImmutable()).asImmutable();
    }

    private AttributeSpecificCache getCache(AttributeContainer attributes) {
        AttributeSpecificCache cache = attributeSpecificCache.get(attributes);
        if (cache == null) {
            cache = new AttributeSpecificCache();
            attributeSpecificCache.put(attributes, cache);
        }
        return cache;
    }

    private boolean matchAttributes(AttributeContainerInternal actual, AttributeContainerInternal requested) {
        AttributeMatcher schemaToMatchOn = schema.matcher();
        Map<AttributeContainer, Boolean> cache = getCache(requested).ignoreExtraActual;
        Boolean match = cache.get(actual);
        if (match == null) {
            match = schemaToMatchOn.isMatching(actual, requested);
            cache.put(actual, match);
        }
        return match;
    }

    private static class AttributeSpecificCache {
        private final Map<AttributeContainer, Boolean> ignoreExtraActual = Maps.newConcurrentMap();
        private final Map<AttributeContainer, ConsumerVariantMatchResult> transforms = Maps.newConcurrentMap();
    }

}
