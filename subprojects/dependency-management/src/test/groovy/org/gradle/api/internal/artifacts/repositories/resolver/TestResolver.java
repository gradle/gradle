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

package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.artifacts.ComponentMetadataSupplier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;

public class TestResolver extends ExternalResourceResolver<ModuleComponentResolveMetadata, MutableModuleComponentResolveMetadata> {
    ExternalResourceArtifactResolver artifactResolver;

    public TestResolver(String name, boolean local, ExternalResourceRepository repository, CacheAwareExternalResourceAccessor cachingResourceAccessor, VersionLister versionLister, LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder, FileStore<ModuleComponentArtifactIdentifier> artifactFileStore, ImmutableModuleIdentifierFactory moduleIdentifierFactory, FileResourceRepository fileResourceRepository) {
        super(name, local, repository, cachingResourceAccessor, versionLister, locallyAvailableResourceFinder, artifactFileStore, moduleIdentifierFactory, fileResourceRepository);
    }

    @Override
    protected Class<ModuleComponentResolveMetadata> getSupportedMetadataType() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected MutableModuleComponentResolveMetadata createMissingComponentMetadata(ModuleComponentIdentifier moduleComponentIdentifier) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected MutableModuleComponentResolveMetadata parseMetaDataFromResource(ModuleComponentIdentifier moduleComponentIdentifier, LocallyAvailableExternalResource cachedResource, DescriptorParseContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean isMetaDataArtifact(ArtifactType artifactType) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected IvyArtifactName getMetaDataArtifactName(String moduleName) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void appendId(BuildCacheHasher hasher) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModuleComponentRepositoryAccess getLocalAccess() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ExternalResourceArtifactResolver createArtifactResolver() {
        return artifactResolver;
    }

    @Override
    public ModuleComponentRepositoryAccess getRemoteAccess() {
        return new RemoteRepositoryAccess() {
            @Override
            protected void resolveModuleArtifacts(ModuleComponentResolveMetadata module, BuildableComponentArtifactsResolveResult result) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void resolveJavadocArtifacts(ModuleComponentResolveMetadata module, BuildableArtifactSetResolveResult result) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void resolveSourceArtifacts(ModuleComponentResolveMetadata module, BuildableArtifactSetResolveResult result) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public ComponentMetadataSupplier createMetadataSupplier() {
        throw new UnsupportedOperationException();
    }
}
