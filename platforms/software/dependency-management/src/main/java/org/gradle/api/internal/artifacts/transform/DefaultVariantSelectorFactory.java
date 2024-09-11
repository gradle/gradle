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

import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.configurations.ResolutionResultProvider;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity;

import javax.annotation.Nullable;
import javax.inject.Inject;

public class DefaultVariantSelectorFactory implements VariantSelectorFactory {
    private final ConsumerProvidedVariantFinder consumerProvidedVariantFinder;
    private final AttributesSchemaInternal schema;
    private final ImmutableAttributesFactory attributesFactory;
    private final TransformedVariantFactory transformedVariantFactory;
    private final ResolutionFailureHandler failureProcessor;
    private final DomainObjectContext domainObjectContext;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final TaskDependencyFactory taskDependencyFactory;


    @Inject
    public DefaultVariantSelectorFactory(
        ConsumerProvidedVariantFinder consumerProvidedVariantFinder,
        AttributesSchemaInternal schema,
        ImmutableAttributesFactory attributesFactory,
        TransformedVariantFactory transformedVariantFactory,
        ResolutionFailureHandler failureProcessor,
        DomainObjectContext domainObjectContext,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        TaskDependencyFactory taskDependencyFactory
    ) {
        this.consumerProvidedVariantFinder = consumerProvidedVariantFinder;
        this.schema = schema;
        this.attributesFactory = attributesFactory;
        this.transformedVariantFactory = transformedVariantFactory;
        this.failureProcessor = failureProcessor;
        this.domainObjectContext = domainObjectContext;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.taskDependencyFactory = taskDependencyFactory;
    }

    @Override
    public ArtifactVariantSelector create(
        ResolutionHost resolutionHost,
        ImmutableAttributes requestAttributes,
        @Nullable ConfigurationIdentity configurationId,
        ResolutionStrategy.SortOrder artifactDependencySortOrder,
        ResolutionResultProvider<ResolverResults> resolverResults,
        ResolutionResultProvider<ResolverResults> strictResolverResults
    ) {
        TransformUpstreamDependenciesResolver dependenciesResolver = new DefaultTransformUpstreamDependenciesResolver(
            resolutionHost,
            configurationId,
            requestAttributes,
            artifactDependencySortOrder,
            strictResolverResults,
            resolverResults,
            domainObjectContext,
            calculatedValueContainerFactory,
            attributesFactory,
            taskDependencyFactory
        );

        return new AttributeMatchingArtifactVariantSelector(
            schema,
            dependenciesResolver,
            consumerProvidedVariantFinder,
            attributesFactory,
            transformedVariantFactory,
            failureProcessor
        );
    }
}
