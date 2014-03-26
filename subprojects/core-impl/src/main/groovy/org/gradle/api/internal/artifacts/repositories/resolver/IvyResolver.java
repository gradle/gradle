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

import com.google.common.collect.ImmutableSet;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.resolution.JvmLibraryJavadocArtifact;
import org.gradle.api.artifacts.resolution.JvmLibrarySourcesArtifact;
import org.gradle.api.artifacts.resolution.SoftwareArtifact;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DownloadedIvyModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.metadata.*;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.resolution.ComponentMetaDataArtifact;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;

import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

public class IvyResolver extends ExternalResourceResolver implements PatternBasedResolver {

    private final RepositoryTransport transport;
    private final boolean dynamicResolve;

    public IvyResolver(String name, RepositoryTransport transport,
                       LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> locallyAvailableResourceFinder,
                       boolean dynamicResolve, ResolverStrategy resolverStrategy) {
        super(name, transport.getRepository(), new ResourceVersionLister(transport.getRepository()),
                locallyAvailableResourceFinder, new DownloadedIvyModuleDescriptorParser(resolverStrategy),
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
    protected ModuleVersionArtifactMetaData getMetaDataArtifactFor(ModuleVersionIdentifier moduleVersionIdentifier) {
        DefaultModuleVersionArtifactIdentifier artifactId = new DefaultModuleVersionArtifactIdentifier(moduleVersionIdentifier, "ivy", "ivy", "xml");
        return new DefaultModuleVersionArtifactMetaData(artifactId);
    }

    public void addArtifactLocation(URI baseUri, String pattern) {
        String artifactPattern = transport.convertToPath(baseUri) + pattern;
        addArtifactPattern(artifactPattern);
    }

    public void addDescriptorLocation(URI baseUri, String pattern) {
        String descriptorPattern = transport.convertToPath(baseUri) + pattern;
        addIvyPattern(descriptorPattern);
    }

    public Set<? extends ComponentArtifactMetaData> getTypedArtifacts(ModuleVersionMetaData module, Class<? extends SoftwareArtifact> artifactType) {
        if (artifactType == JvmLibraryJavadocArtifact.class) {
            ConfigurationMetaData configuration = module.getConfiguration("javadoc");
            return configuration != null ? configuration.getArtifacts() : findOptionalArtifacts(module, "javadoc", "javadoc");
        }

        if (artifactType == JvmLibrarySourcesArtifact.class) {
            ConfigurationMetaData configuration = module.getConfiguration("sources");
            return configuration != null ? configuration.getArtifacts() : findOptionalArtifacts(module, "source", "sources");
        }

        if (artifactType == ComponentMetaDataArtifact.class) {
            Artifact ivyArtifact = DefaultArtifact.newIvyArtifact(IvyUtil.createModuleRevisionId(module.getId()), new Date());
            return ImmutableSet.of(module.artifact(ivyArtifact));
        }

        throw new IllegalArgumentException(String.format("Don't know how to get candidate artifacts of type %s", artifactType.getName()));
    }

    @Override
    protected Set<ComponentArtifactMetaData> getOptionalMainArtifacts(ModuleVersionMetaData module) {
        return Collections.emptySet();
    }
}
