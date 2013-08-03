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
package org.gradle.api.internal.artifacts.repositories.legacy;

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.cache.CacheMetadataOptions;
import org.apache.ivy.core.cache.ModuleDescriptorWriter;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.parser.ModuleDescriptorParser;
import org.apache.ivy.plugins.parser.ModuleDescriptorParserRegistry;
import org.apache.ivy.plugins.parser.ParserSettings;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.IvyContextualiser;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;

abstract class AbstractRepositoryCacheManager implements RepositoryCacheManager {
    protected final String name;

    public AbstractRepositoryCacheManager(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void saveResolvers(ModuleDescriptor descriptor, String metadataResolverName, String artifactResolverName) {
    }

    public ArtifactOrigin getSavedArtifactOrigin(Artifact artifact) {
        return null;
    }

    public ResolvedModuleRevision findModuleInCache(DependencyDescriptor dd, ModuleRevisionId requestedRevisionId, CacheMetadataOptions options, String expectedResolver) {
        return null;
    }

    public void originalToCachedModuleDescriptor(DependencyResolver resolver, ResolvedResource originalMetadataRef, Artifact requestedMetadataArtifact, ResolvedModuleRevision rmr, ModuleDescriptorWriter writer) {
    }

    public void clean() {
    }

    public void saveResolvedRevision(ModuleRevisionId dynamicMrid, String revision) {
    }

    protected ModuleDescriptor parseModuleDescriptor(DependencyResolver resolver, Artifact moduleArtifact, CacheMetadataOptions options, File artifactFile, Resource resource) throws ParseException {
        ModuleRevisionId moduleRevisionId = moduleArtifact.getId().getModuleRevisionId();
        try {
            IvySettings ivySettings = IvyContextualiser.getIvyContext().getSettings();
            ParserSettings parserSettings = new LegacyResolverParserSettings(ivySettings, resolver, moduleRevisionId);
            ModuleDescriptorParser parser = ModuleDescriptorParserRegistry.getInstance().getParser(resource);
            return parser.parseDescriptor(parserSettings, new URL(artifactFile.toURI().toASCIIString()), resource, options.isValidate());
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

}
