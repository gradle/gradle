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

import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultArtifactIdentifier;
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DownloadedIvyModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;

import java.net.URI;

public class IvyResolver extends ExternalResourceResolver implements PatternBasedResolver {

    private final RepositoryTransport transport;
    private final boolean dynamicResolve;

    public IvyResolver(String name, RepositoryTransport transport,
                       LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder,
                       ModuleMetadataProcessor metadataProcessor,
                       boolean dynamicResolve, ResolverStrategy resolverStrategy) {
        super(name, transport.getRepository(), new ResourceVersionLister(transport.getRepository()),
                locallyAvailableResourceFinder, new DownloadedIvyModuleDescriptorParser(resolverStrategy), metadataProcessor,
                resolverStrategy);
        this.transport = transport;
        this.transport.configureCacheManager(this);
        this.dynamicResolve = dynamicResolve;
    }

    @Override
    public boolean isDynamicResolveMode() {
        return dynamicResolve;
    }

    @Nullable
    protected ArtifactIdentifier getMetaDataArtifactFor(ModuleVersionIdentifier moduleVersionIdentifier) {
        return new DefaultArtifactIdentifier(moduleVersionIdentifier, "ivy", "ivy", "xml", null);
    }

    public void addArtifactLocation(URI baseUri, String pattern) {
        String artifactPattern = transport.convertToPath(baseUri) + pattern;
        addArtifactPattern(artifactPattern);
    }

    public void addDescriptorLocation(URI baseUri, String pattern) {
        String descriptorPattern = transport.convertToPath(baseUri) + pattern;
        addIvyPattern(descriptorPattern);
    }
}
