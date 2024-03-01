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

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.repositories.descriptor.UrlRepositoryDescriptor;
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources;
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataArtifactProvider;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;
import org.gradle.util.TestUtil;

public class TestResolver extends ExternalResourceResolver<ModuleComponentResolveMetadata> {
    ExternalResourceArtifactResolver artifactResolver;

    protected TestResolver(UrlRepositoryDescriptor descriptor, boolean local, ExternalResourceRepository repository, CacheAwareExternalResourceAccessor cachingResourceAccessor, LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder, FileStore<ModuleComponentArtifactIdentifier> artifactFileStore, ImmutableMetadataSources metadataSources, MetadataArtifactProvider metadataArtifactProvider) {
        super(descriptor, local, repository, cachingResourceAccessor, locallyAvailableResourceFinder, artifactFileStore, metadataSources, metadataArtifactProvider, null, null, null, TestUtil.getChecksumService());
    }

    @Override
    protected Class<ModuleComponentResolveMetadata> getSupportedMetadataType() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean isMetaDataArtifact(ArtifactType artifactType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> getLocalAccess() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ExternalResourceArtifactResolver createArtifactResolver() {
        return artifactResolver;
    }

    @Override
    public ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> getRemoteAccess() {
        return new RemoteRepositoryAccess() {

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

    interface MutableTestResolveMetadata extends MutableModuleComponentResolveMetadata {
    }
}
