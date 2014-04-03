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

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.resolution.SoftwareArtifact;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RepositoryChain;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.DefaultDependencyMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.externalresource.DefaultLocallyAvailableExternalResource;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;
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
    private final ExternalResourceResolver moduleResolver;

    public ExternalResourceResolverDescriptorParseContext(RepositoryChain mainResolvers, ExternalResourceResolver moduleResolver) {
        this.mainResolvers = mainResolvers;
        this.moduleResolver = moduleResolver;
    }

    public boolean artifactExists(ModuleVersionArtifactMetaData artifact) {
        return moduleResolver.artifactExists(artifact);
    }

    public LocallyAvailableExternalResource getMetaDataArtifact(ModuleVersionIdentifier moduleVersionIdentifier, Class<? extends SoftwareArtifact> artifactType) {
        File resolvedArtifactFile = resolveMetaDataArtifactFile(moduleVersionIdentifier, mainResolvers.getDependencyResolver(), mainResolvers.getArtifactResolver(), artifactType);
        LocallyAvailableResource localResource = new DefaultLocallyAvailableResource(resolvedArtifactFile);
        return new DefaultLocallyAvailableExternalResource(resolvedArtifactFile.toURI().toString(), localResource);
    }

    private File resolveMetaDataArtifactFile(ModuleVersionIdentifier moduleVersionIdentifier, DependencyToModuleVersionResolver dependencyResolver,
                                             ArtifactResolver artifactResolver, Class<? extends SoftwareArtifact> artifactType) {
        BuildableComponentResolveResult moduleVersionResolveResult = new DefaultBuildableComponentResolveResult();
        dependencyResolver.resolve(new DefaultDependencyMetaData(new DefaultDependencyDescriptor(IvyUtil.createModuleRevisionId(moduleVersionIdentifier), true)), moduleVersionResolveResult);

        BuildableArtifactSetResolveResult moduleArtifactsResolveResult = new DefaultBuildableArtifactSetResolveResult();
        ArtifactTypeResolveContext context = new ArtifactTypeResolveContext(artifactType);
        artifactResolver.resolveModuleArtifacts(moduleVersionResolveResult.getMetaData(), context, moduleArtifactsResolveResult);

        BuildableArtifactResolveResult artifactResolveResult = new DefaultBuildableArtifactResolveResult();
        ComponentArtifactMetaData artifactMetaData = moduleArtifactsResolveResult.getArtifacts().iterator().next();
        artifactResolver.resolveArtifact(artifactMetaData, moduleVersionResolveResult.getMetaData().getSource(), artifactResolveResult);
        return artifactResolveResult.getFile();
    }
}
