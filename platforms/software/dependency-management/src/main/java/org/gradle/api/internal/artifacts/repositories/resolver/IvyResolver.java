/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.artifacts.ComponentMetadataListerDetails;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.repositories.descriptor.IvyRepositoryDescriptor;
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources;
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataArtifactProvider;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;

import javax.annotation.Nullable;

public class IvyResolver extends ExternalResourceResolver<IvyModuleResolveMetadata> {

    private final boolean dynamicResolve;
    private final boolean m2Compatible;
    private final IvyLocalRepositoryAccess localRepositoryAccess;
    private final IvyRemoteRepositoryAccess remoteRepositoryAccess;

    public IvyResolver(
        IvyRepositoryDescriptor descriptor,
        RepositoryTransport transport,
        LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
        boolean dynamicResolve,
        FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
        @Nullable InstantiatingAction<ComponentMetadataSupplierDetails> componentMetadataSupplierFactory,
        @Nullable InstantiatingAction<ComponentMetadataListerDetails> componentMetadataVersionListerFactory,
        ImmutableMetadataSources metadataSources,
        MetadataArtifactProvider metadataArtifactProvider,
        Instantiator injector, ChecksumService checksumService
    ) {
        super(
            descriptor,
            transport.isLocal(),
            transport.getRepository(),
            transport.getResourceAccessor(),
            locallyAvailableResourceFinder,
            artifactFileStore,
            metadataSources,
            metadataArtifactProvider,
            componentMetadataSupplierFactory,
            componentMetadataVersionListerFactory,
            injector,
            checksumService);
        this.dynamicResolve = dynamicResolve;
        this.m2Compatible = descriptor.isM2Compatible();
        this.localRepositoryAccess = new IvyLocalRepositoryAccess();
        this.remoteRepositoryAccess = new IvyRemoteRepositoryAccess();
    }

    @Override
    public String toString() {
        return "Ivy repository '" + getName() + "'";
    }

    @Override
    protected Class<IvyModuleResolveMetadata> getSupportedMetadataType() {
        return IvyModuleResolveMetadata.class;
    }

    @Override
    public boolean isDynamicResolveMode() {
        return dynamicResolve;
    }

    @Override
    protected boolean isMetaDataArtifact(ArtifactType artifactType) {
        return artifactType == ArtifactType.IVY_DESCRIPTOR;
    }

    public boolean isM2compatible() {
        return m2Compatible;
    }

    @Override
    public ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> getLocalAccess() {
        return localRepositoryAccess;
    }

    @Override
    public ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> getRemoteAccess() {
        return remoteRepositoryAccess;
    }

    private class IvyLocalRepositoryAccess extends LocalRepositoryAccess {

        @Override
        protected void resolveJavadocArtifacts(IvyModuleResolveMetadata module, BuildableArtifactSetResolveResult result) {
            ConfigurationMetadata configuration = module.getConfiguration("javadoc");
            if (configuration != null) {
                result.resolved(configuration.getArtifacts());
            }
        }

        @Override
        protected void resolveSourceArtifacts(IvyModuleResolveMetadata module, BuildableArtifactSetResolveResult result) {
            ConfigurationMetadata configuration = module.getConfiguration("sources");
            if (configuration != null) {
                result.resolved(configuration.getArtifacts());
            }
        }
    }

    private class IvyRemoteRepositoryAccess extends RemoteRepositoryAccess {

    }
}
