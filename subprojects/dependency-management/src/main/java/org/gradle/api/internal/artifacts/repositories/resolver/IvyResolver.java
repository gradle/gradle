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
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources;
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataArtifactProvider;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.model.IvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.MetadataSourcedComponentArtifacts;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;

import javax.annotation.Nullable;
import java.net.URI;

public class IvyResolver extends ExternalResourceResolver<IvyModuleResolveMetadata> implements PatternBasedResolver {

    private final boolean dynamicResolve;
    private boolean m2Compatible;
    private final IvyLocalRepositoryAccess localRepositoryAccess;
    private final IvyRemoteRepositoryAccess remoteRepositoryAccess;

    public IvyResolver(String name,
                       RepositoryTransport transport,
                       LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                       boolean dynamicResolve,
                       FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                       ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                       @Nullable InstantiatingAction<ComponentMetadataSupplierDetails> componentMetadataSupplierFactory,
                       @Nullable InstantiatingAction<ComponentMetadataListerDetails> componentMetadataVersionListerFactory,
                       ImmutableMetadataSources repositoryContentFilter,
                       MetadataArtifactProvider metadataArtifactProvider, Instantiator injector) {
        super(name, transport.isLocal(), transport.getRepository(), transport.getResourceAccessor(), locallyAvailableResourceFinder, artifactFileStore, moduleIdentifierFactory, repositoryContentFilter, metadataArtifactProvider, componentMetadataSupplierFactory, componentMetadataVersionListerFactory, injector);
        this.dynamicResolve = dynamicResolve;
        this.localRepositoryAccess = new IvyLocalRepositoryAccess();
        this.remoteRepositoryAccess = new IvyRemoteRepositoryAccess();
    }

    @Override
    public String toString() {
        return "Ivy repository '" + getName() + "'";
    }

    @Override
    protected void appendId(BuildCacheHasher hasher) {
        super.appendId(hasher);
        hasher.putBoolean(isM2compatible());
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
    public void setM2compatible(boolean m2compatible) {
        this.m2Compatible = m2compatible;
    }

    @Override
    public void addArtifactLocation(URI baseUri, String pattern) {
        addArtifactPattern(toResourcePattern(baseUri, pattern));
    }

    @Override
    public void addDescriptorLocation(URI baseUri, String pattern) {
        addIvyPattern(toResourcePattern(baseUri, pattern));
    }

    private ResourcePattern toResourcePattern(URI baseUri, String pattern) {
        return isM2compatible() ? new M2ResourcePattern(baseUri, pattern) : new IvyResourcePattern(baseUri, pattern);
    }

    @Override
    public ModuleComponentRepositoryAccess getLocalAccess() {
        return localRepositoryAccess;
    }

    @Override
    public ModuleComponentRepositoryAccess getRemoteAccess() {
        return remoteRepositoryAccess;
    }

    private class IvyLocalRepositoryAccess extends LocalRepositoryAccess {
        @Override
        protected void resolveModuleArtifacts(IvyModuleResolveMetadata module, BuildableComponentArtifactsResolveResult result) {
            result.resolved(new MetadataSourcedComponentArtifacts());
        }

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
        @Override
        protected void resolveModuleArtifacts(IvyModuleResolveMetadata module, BuildableComponentArtifactsResolveResult result) {
            // Configuration artifacts are determined locally
        }

        @Override
        protected void resolveJavadocArtifacts(IvyModuleResolveMetadata module, BuildableArtifactSetResolveResult result) {
            // Probe for artifact with classifier
            result.resolved(findOptionalArtifacts(module, "javadoc", "javadoc"));
        }

        @Override
        protected void resolveSourceArtifacts(IvyModuleResolveMetadata module, BuildableArtifactSetResolveResult result) {
            // Probe for artifact with classifier
            result.resolved(findOptionalArtifacts(module, "source", "sources"));
        }
    }
}
