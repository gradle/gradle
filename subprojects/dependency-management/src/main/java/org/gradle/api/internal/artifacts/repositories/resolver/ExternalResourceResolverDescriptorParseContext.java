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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RepositoryChain;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.DefaultDependencyMetaData;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentResolver;
import org.gradle.internal.resolve.result.*;
import org.gradle.internal.resource.DefaultLocallyAvailableExternalResource;
import org.gradle.internal.resource.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource;
import org.gradle.internal.resource.local.LocallyAvailableResource;

import java.io.File;

/**
 * ParserSettings that control the scope of searches carried out during parsing.
 * If the parser asks for a resolver for the currently resolving revision, the resolver scope is only the repository where the module was resolved.
 * If the parser asks for a resolver for a different revision, the resolver scope is all repositories.
 */
public class ExternalResourceResolverDescriptorParseContext implements DescriptorParseContext {
    private final RepositoryChain mainResolvers;

    public ExternalResourceResolverDescriptorParseContext(RepositoryChain mainResolvers) {
        this.mainResolvers = mainResolvers;
    }

    public LocallyAvailableExternalResource getMetaDataArtifact(ModuleVersionIdentifier moduleVersionIdentifier, ArtifactType artifactType) {
        File resolvedArtifactFile = resolveMetaDataArtifactFile(moduleVersionIdentifier, mainResolvers.getDependencyResolver(), mainResolvers.getArtifactResolver(), artifactType);
        LocallyAvailableResource localResource = new DefaultLocallyAvailableResource(resolvedArtifactFile);
        return new DefaultLocallyAvailableExternalResource(resolvedArtifactFile.toURI(), localResource);
    }

    private File resolveMetaDataArtifactFile(ModuleVersionIdentifier moduleVersionIdentifier, DependencyToComponentResolver dependencyResolver,
                                             ArtifactResolver artifactResolver, ArtifactType artifactType) {
        BuildableComponentResolveResult moduleVersionResolveResult = new DefaultBuildableComponentResolveResult();
        dependencyResolver.resolve(new DefaultDependencyMetaData(moduleVersionIdentifier), moduleVersionResolveResult);

        BuildableArtifactSetResolveResult moduleArtifactsResolveResult = new DefaultBuildableArtifactSetResolveResult();
        artifactResolver.resolveModuleArtifacts(moduleVersionResolveResult.getMetaData(), artifactType, moduleArtifactsResolveResult);

        BuildableArtifactResolveResult artifactResolveResult = new DefaultBuildableArtifactResolveResult();
        ComponentArtifactMetaData artifactMetaData = moduleArtifactsResolveResult.getArtifacts().iterator().next();
        artifactResolver.resolveArtifact(artifactMetaData, moduleVersionResolveResult.getMetaData().getSource(), artifactResolveResult);
        return artifactResolveResult.getFile();
    }
}
