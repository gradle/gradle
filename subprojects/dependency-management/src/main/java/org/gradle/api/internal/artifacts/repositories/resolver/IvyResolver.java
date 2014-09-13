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

import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.external.model.DefaultIvyModuleResolveMetaData;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetaData;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DownloadedIvyModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.internal.component.model.ConfigurationMetaData;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resource.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;

import java.net.URI;

public class IvyResolver extends ExternalResourceResolver implements PatternBasedResolver {

    private final boolean dynamicResolve;
    private final MetaDataParser metaDataParser;
    private boolean m2Compatible;

    public IvyResolver(String name, RepositoryTransport transport,
                       LocallyAvailableResourceFinder<ModuleComponentArtifactMetaData> locallyAvailableResourceFinder,
                       boolean dynamicResolve, ResolverStrategy resolverStrategy, FileStore<ModuleComponentArtifactMetaData> artifactFileStore) {
        super(name, transport.isLocal(), transport.getRepository(), transport.getResourceAccessor(), new ResourceVersionLister(transport.getRepository()), locallyAvailableResourceFinder, artifactFileStore);
        this.metaDataParser = new DownloadedIvyModuleDescriptorParser(resolverStrategy);
        this.dynamicResolve = dynamicResolve;
    }

    @Override
    public String toString() {
        return String.format("Ivy repository '%s'", getName());
    }

    @Override
    public boolean isDynamicResolveMode() {
        return dynamicResolve;
    }

    protected boolean isMetaDataArtifact(ArtifactType artifactType) {
        return artifactType == ArtifactType.IVY_DESCRIPTOR;
    }

    @Override
    protected IvyArtifactName getMetaDataArtifactName(String moduleName) {
        return new DefaultIvyArtifactName("ivy", "ivy", "xml");
    }

    @Override
    public boolean isM2compatible() {
        return m2Compatible;
    }

    public void setM2compatible(boolean m2compatible) {
        this.m2Compatible = m2compatible;
    }

    public void addArtifactLocation(URI baseUri, String pattern) {
        addArtifactPattern(toResourcePattern(baseUri, pattern));
    }

    public void addDescriptorLocation(URI baseUri, String pattern) {
        addIvyPattern(toResourcePattern(baseUri, pattern));
    }

    protected ResourcePattern toResourcePattern(URI baseUri, String pattern) {
        return isM2compatible() ? new M2ResourcePattern(baseUri, pattern) : new IvyResourcePattern(baseUri, pattern);
    }

    public ModuleComponentRepositoryAccess getLocalAccess() {
        return new IvyLocalRepositoryAccess();
    }

    public ModuleComponentRepositoryAccess getRemoteAccess() {
        return new IvyRemoteRepositoryAccess();
    }

    protected MutableModuleComponentResolveMetaData createMetaDataForDependency(DependencyMetaData dependency) {
        return new DefaultIvyModuleResolveMetaData(dependency);
    }

    protected MutableModuleComponentResolveMetaData parseMetaDataFromResource(LocallyAvailableExternalResource cachedResource, DescriptorParseContext context) {
        return metaDataParser.parseMetaData(context, cachedResource);
    }

    private class IvyLocalRepositoryAccess extends LocalRepositoryAccess {

        protected void resolveConfigurationArtifacts(ModuleComponentResolveMetaData module, ConfigurationMetaData configuration, BuildableArtifactSetResolveResult result) {
            result.resolved(configuration.getArtifacts());
        }

        @Override
        protected void resolveJavadocArtifacts(ModuleComponentResolveMetaData module, BuildableArtifactSetResolveResult result) {
            ConfigurationMetaData configuration = module.getConfiguration("javadoc");
            if (configuration != null) {
                result.resolved(configuration.getArtifacts());
            }
        }

        @Override
        protected void resolveSourceArtifacts(ModuleComponentResolveMetaData module, BuildableArtifactSetResolveResult result) {
            ConfigurationMetaData configuration = module.getConfiguration("sources");
            if (configuration != null) {
                result.resolved(configuration.getArtifacts());
            }
        }
    }

    private class IvyRemoteRepositoryAccess extends RemoteRepositoryAccess {
        @Override
        protected void resolveConfigurationArtifacts(ModuleComponentResolveMetaData module, ConfigurationMetaData configuration, BuildableArtifactSetResolveResult result) {
            // Configuration artifacts are determined locally
        }

        @Override
        protected void resolveJavadocArtifacts(ModuleComponentResolveMetaData module, BuildableArtifactSetResolveResult result) {
            // Probe for artifact with classifier
            result.resolved(findOptionalArtifacts(module, "javadoc", "javadoc"));
        }

        @Override
        protected void resolveSourceArtifacts(ModuleComponentResolveMetaData module, BuildableArtifactSetResolveResult result) {
            // Probe for artifact with classifier
            result.resolved(findOptionalArtifacts(module, "source", "sources"));
        }
    }
}
