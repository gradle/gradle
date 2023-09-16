/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.SelectionFailureHandler;

public class DefaultVariantSelectorFactory implements VariantSelectorFactory {
    private final ConsumerProvidedVariantFinder consumerProvidedVariantFinder;
    private final AttributesSchemaInternal schema;
    private final ImmutableAttributesFactory attributesFactory;
    private final TransformedVariantFactory transformedVariantFactory;
    private final SelectionFailureHandler failureProcessor;

    public DefaultVariantSelectorFactory(
        ConsumerProvidedVariantFinder consumerProvidedVariantFinder,
        AttributesSchemaInternal schema,
        ImmutableAttributesFactory attributesFactory,
        TransformedVariantFactory transformedVariantFactory,
        SelectionFailureHandler failureProcessor
    ) {
        this.consumerProvidedVariantFinder = consumerProvidedVariantFinder;
        this.schema = schema;
        this.attributesFactory = attributesFactory;
        this.transformedVariantFactory = transformedVariantFactory;
        this.failureProcessor = failureProcessor;
    }

    @Override
    public ArtifactVariantSelector create(AttributeContainerInternal consumerAttributes, boolean allowNoMatchingVariants, TransformUpstreamDependenciesResolverFactory dependenciesResolverFactory) {
        return new AttributeMatchingArtifactVariantSelector(consumerProvidedVariantFinder, schema, attributesFactory, transformedVariantFactory, consumerAttributes.asImmutable(), allowNoMatchingVariants, dependenciesResolverFactory, failureProcessor);
    }
}
