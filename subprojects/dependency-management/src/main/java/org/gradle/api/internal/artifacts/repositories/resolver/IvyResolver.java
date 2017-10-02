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

import org.gradle.api.artifacts.ComponentMetadataSupplier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextualMetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyXmlModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.DefaultMutableIvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.IvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.MetadataSourcedComponentArtifacts;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.MutableIvyModuleResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;

import java.net.URI;

public class IvyResolver extends ExternalResourceResolver<IvyModuleResolveMetadata, MutableIvyModuleResolveMetadata> implements PatternBasedResolver {

    private final boolean dynamicResolve;
    private final MetaDataParser<MutableIvyModuleResolveMetadata> metaDataParser;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final Factory<ComponentMetadataSupplier> componentMetadataSupplierFactory;
    private boolean m2Compatible;
    private final IvyLocalRepositoryAccess localRepositoryAccess;
    private final IvyRemoteRepositoryAccess remoteRepositoryAccess;

    public IvyResolver(String name, RepositoryTransport transport,
                       LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                       boolean dynamicResolve, FileStore<ModuleComponentArtifactIdentifier> artifactFileStore, IvyContextManager ivyContextManager,
                       ImmutableModuleIdentifierFactory moduleIdentifierFactory, Factory<ComponentMetadataSupplier> componentMetadataSupplierFactory,
                       FileResourceRepository fileResourceRepository) {
        super(name, transport.isLocal(), transport.getRepository(), transport.getResourceAccessor(), new ResourceVersionLister(transport.getRepository()), locallyAvailableResourceFinder, artifactFileStore, moduleIdentifierFactory, fileResourceRepository);
        this.componentMetadataSupplierFactory = componentMetadataSupplierFactory;
        this.metaDataParser = new IvyContextualMetaDataParser<MutableIvyModuleResolveMetadata>(ivyContextManager, new IvyXmlModuleDescriptorParser(new IvyModuleDescriptorConverter(moduleIdentifierFactory), moduleIdentifierFactory, fileResourceRepository));
        this.dynamicResolve = dynamicResolve;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.localRepositoryAccess = new IvyLocalRepositoryAccess();
        this.remoteRepositoryAccess = new IvyRemoteRepositoryAccess();
    }

    @Override
    public String toString() {
        return "Ivy repository '" + getName() + "'";
    }

    @Override
    protected void appendId(BuildCacheHasher hasher) {
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

    @Override
    protected IvyArtifactName getMetaDataArtifactName(String moduleName) {
        return new DefaultIvyArtifactName("ivy", "ivy", "xml");
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

    @Override
    public ComponentMetadataSupplier createMetadataSupplier() {
        return componentMetadataSupplierFactory.create();
    }

    @Override
    protected MutableIvyModuleResolveMetadata createMissingComponentMetadata(ModuleComponentIdentifier moduleComponentIdentifier) {
        ModuleVersionIdentifier mvi = moduleIdentifierFactory.moduleWithVersion(moduleComponentIdentifier.getGroup(), moduleComponentIdentifier.getModule(), moduleComponentIdentifier.getVersion());
        return DefaultMutableIvyModuleResolveMetadata.missing(mvi, moduleComponentIdentifier);
    }

    @Override
    protected MutableIvyModuleResolveMetadata parseMetaDataFromResource(ModuleComponentIdentifier moduleComponentIdentifier, LocallyAvailableExternalResource cachedResource, DescriptorParseContext context) {
        MutableIvyModuleResolveMetadata metaData = metaDataParser.parseMetaData(context, cachedResource);
        checkMetadataConsistency(moduleComponentIdentifier, metaData);
        return metaData;
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
