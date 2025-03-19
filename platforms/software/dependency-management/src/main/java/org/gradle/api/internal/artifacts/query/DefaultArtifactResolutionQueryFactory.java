/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.query;

import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory;
import org.gradle.api.internal.artifacts.RepositoriesSupplier;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ExternalModuleComponentResolverFactory;
import org.gradle.api.internal.component.ComponentTypeRegistry;

import javax.inject.Inject;

public class DefaultArtifactResolutionQueryFactory implements ArtifactResolutionQueryFactory {
    private final ResolutionStrategyFactory resolutionStrategyFactory;
    private final RepositoriesSupplier repositoriesSupplier;
    private final ExternalModuleComponentResolverFactory ivyFactory;
    private final ComponentMetadataProcessorFactory componentMetadataProcessorFactory;
    private final ComponentTypeRegistry componentTypeRegistry;

    @Inject
    public DefaultArtifactResolutionQueryFactory(
        ResolutionStrategyFactory resolutionStrategyFactory,
        RepositoriesSupplier repositoriesSupplier,
        ExternalModuleComponentResolverFactory ivyFactory,
        ComponentMetadataProcessorFactory componentMetadataProcessorFactory,
        ComponentTypeRegistry componentTypeRegistry
    ) {
        this.resolutionStrategyFactory = resolutionStrategyFactory;
        this.repositoriesSupplier = repositoriesSupplier;
        this.ivyFactory = ivyFactory;
        this.componentMetadataProcessorFactory = componentMetadataProcessorFactory;
        this.componentTypeRegistry = componentTypeRegistry;
    }

    @Override
    public ArtifactResolutionQuery createArtifactResolutionQuery() {
        return new DefaultArtifactResolutionQuery(resolutionStrategyFactory, repositoriesSupplier, ivyFactory, componentMetadataProcessorFactory, componentTypeRegistry);
    }
}
