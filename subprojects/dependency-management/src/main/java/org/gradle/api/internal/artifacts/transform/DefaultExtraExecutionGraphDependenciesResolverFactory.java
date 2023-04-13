/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.configurations.ResolutionResultProvider;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity;

public class DefaultExtraExecutionGraphDependenciesResolverFactory implements ExtraExecutionGraphDependenciesResolverFactory {
    public static final TransformUpstreamDependenciesResolver NO_DEPENDENCIES_RESOLVER = transformationStep -> DefaultTransformUpstreamDependenciesResolver.NO_DEPENDENCIES;

    private final DomainObjectContext owner;
    private final FilteredResultFactory filteredResultFactory;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final ConfigurationIdentity configurationIdentity;
    private final ResolutionResultProvider<ResolutionResult> resolutionResultProvider;

    public DefaultExtraExecutionGraphDependenciesResolverFactory(
        ConfigurationIdentity configurationIdentity, ResolutionResultProvider<ResolutionResult> resolutionResultProvider,
        DomainObjectContext owner,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        FilteredResultFactory filteredResultFactory
    ) {
        this.configurationIdentity = configurationIdentity;
        this.resolutionResultProvider = resolutionResultProvider;
        this.owner = owner;
        this.filteredResultFactory = filteredResultFactory;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    @Override
    public TransformUpstreamDependenciesResolver create(ComponentIdentifier componentIdentifier, TransformationChain transformationChain) {
        if (!transformationChain.requiresDependencies()) {
            return NO_DEPENDENCIES_RESOLVER;
        }
        return new DefaultTransformUpstreamDependenciesResolver(componentIdentifier, configurationIdentity, resolutionResultProvider, owner, filteredResultFactory, calculatedValueContainerFactory);
    }
}
