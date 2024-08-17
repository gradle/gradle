/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.api.internal.artifacts.TransformRegistration;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.AttributeSchemaServiceFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.internal.Pair;

import java.util.ArrayList;
import java.util.List;

public class IsolatingTransformFinder {

    private final ImmutableAttributesFactory attributesFactory;
    private final AttributeSchemaServiceFactory serviceFactory;

    private volatile Pair<ImmutableList<TransformRegistration>, ConsumerProvidedVariantFinder.CachedTransforms> cache;

    public IsolatingTransformFinder(
        ImmutableAttributesFactory attributesFactory,
        AttributeSchemaServiceFactory serviceFactory
    ) {
        this.attributesFactory = attributesFactory;
        this.serviceFactory = serviceFactory;
    }

    public List<TransformedVariant> findTransformedVariants(
        AttributeMatcher matcher,
        ImmutableList<TransformRegistration> variantTransforms,
        List<ResolvedVariant> sources,
        ImmutableAttributes requested
    ) {
        ConsumerProvidedVariantFinder consumerProvidedVariantFinder = serviceFactory.getTransformSelector(matcher);

        Pair<ImmutableList<TransformRegistration>, ConsumerProvidedVariantFinder.CachedTransforms> cached = cache;
        if (cached == null || cached.getLeft() != variantTransforms) {
            cached = Pair.of(variantTransforms, consumerProvidedVariantFinder.cacheRegisteredTransforms(variantTransforms));
            cache = cached;
        }

        List<ConsumerProvidedVariantFinder.CachedVariant> result = consumerProvidedVariantFinder.findTransformedVariants(requested, cached.getRight(), sources);
        return extractResult(result, variantTransforms, sources);
    }

    private List<TransformedVariant> extractResult(
        List<ConsumerProvidedVariantFinder.CachedVariant> cachedVariants,
        List<TransformRegistration> transforms,
        List<ResolvedVariant> sources
    ) {
        List<TransformedVariant> output = new ArrayList<>(cachedVariants.size());
        for (ConsumerProvidedVariantFinder.CachedVariant variant : cachedVariants) {
            ResolvedVariant source = sources.get(variant.sourceIndex);
            VariantDefinition variantDefinition = createVariantChain(transforms, variant.chain, source.getAttributes());
            output.add(new TransformedVariant(source, variantDefinition));
        }
        return output;
    }

    private VariantDefinition createVariantChain(
        List<TransformRegistration> transforms,
        List<Integer> indices,
        ImmutableAttributes sourceAttribute
    ) {
        assert !indices.isEmpty();

        DefaultVariantDefinition previous = null;
        ImmutableAttributes prevAttributes = sourceAttribute;

        for (int i : indices) {
            TransformRegistration transform = transforms.get(i);
            previous = new DefaultVariantDefinition(
                previous,
                attributesFactory.concat(prevAttributes, transform.getTo()),
                transform.getTransformStep()
            );
            prevAttributes = previous.getTargetAttributes();
        }

        return previous;
    }

}
