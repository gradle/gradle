/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMetadataFileSource;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata;
import org.gradle.internal.component.model.MutableModuleSources;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;

import java.io.File;

/**
 * ParserSettings that control the scope of searches carried out during parsing.
 * If the parser asks for a resolver for the currently resolving revision, the resolver scope is only the repository where the module was resolved.
 * If the parser asks for a resolver for a different revision, the resolver scope is all repositories.
 */
public class ExternalResourceResolverDescriptorParseContext implements DescriptorParseContext {
    private final ComponentResolvers mainResolvers;
    private final FileResourceRepository fileResourceRepository;
    private final MutableModuleSources sources = new MutableModuleSources();
    private final ChecksumService checksumService;

    public ExternalResourceResolverDescriptorParseContext(ComponentResolvers mainResolvers, FileResourceRepository fileResourceRepository, ChecksumService checksumService) {
        this.mainResolvers = mainResolvers;
        this.fileResourceRepository = fileResourceRepository;
        this.checksumService = checksumService;
    }

    @Override
    public LocallyAvailableExternalResource getMetaDataArtifact(ModuleComponentIdentifier moduleComponentIdentifier, ArtifactType artifactType) {
        return resolveMetaDataArtifactFile(moduleComponentIdentifier, mainResolvers.getComponentResolver(), mainResolvers.getArtifactResolver(), artifactType);
    }

    @Override
    public LocallyAvailableExternalResource getMetaDataArtifact(ModuleDependencyMetadata dependencyMetadata, VersionSelector acceptor, ArtifactType artifactType) {
        BuildableComponentIdResolveResult idResolveResult = new DefaultBuildableComponentIdResolveResult();
        mainResolvers.getComponentIdResolver().resolve(dependencyMetadata, acceptor, null, idResolveResult);
        return getMetaDataArtifact((ModuleComponentIdentifier) idResolveResult.getId(), artifactType);
    }

    private LocallyAvailableExternalResource resolveMetaDataArtifactFile(ModuleComponentIdentifier moduleComponentIdentifier, ComponentMetaDataResolver componentResolver,
                                                                         ArtifactResolver artifactResolver, ArtifactType artifactType) {
        BuildableComponentResolveResult moduleVersionResolveResult = new DefaultBuildableComponentResolveResult();
        componentResolver.resolve(moduleComponentIdentifier, DefaultComponentOverrideMetadata.EMPTY, moduleVersionResolveResult);

        BuildableArtifactSetResolveResult moduleArtifactsResolveResult = new DefaultBuildableArtifactSetResolveResult();
        artifactResolver.resolveArtifactsWithType(moduleVersionResolveResult.getMetadata(), artifactType, moduleArtifactsResolveResult);

        BuildableArtifactResolveResult artifactResolveResult = new DefaultBuildableArtifactResolveResult();
        ComponentArtifactMetadata artifactMetaData = moduleArtifactsResolveResult.getResult().iterator().next();
        artifactResolver.resolveArtifact(artifactMetaData, moduleVersionResolveResult.getMetadata().getSources(), artifactResolveResult);
        File file = artifactResolveResult.getResult();
        LocallyAvailableExternalResource resource = fileResourceRepository.resource(file);
        ComponentArtifactIdentifier id = artifactMetaData.getId();
        if (id instanceof ModuleComponentArtifactIdentifier) {
            sources.add(new DefaultMetadataFileSource((ModuleComponentArtifactIdentifier) id, file, checksumService.sha1(file)));
        }
        return resource;
    }

    public void appendSources(MutableModuleSources sources) {
        this.sources.withSources(sources::add);
    }
}
