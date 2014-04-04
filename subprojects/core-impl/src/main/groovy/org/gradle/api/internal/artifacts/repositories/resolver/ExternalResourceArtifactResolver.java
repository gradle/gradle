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

import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.MetaDataOnlyExternalResource;
import org.gradle.api.internal.externalresource.MissingExternalResource;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

// TODO:DAZ Extract this properly: make this static
class ExternalResourceArtifactResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalResourceArtifactResolver.class);

    private final ExternalResourceRepository repository;
    private final LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> locallyAvailableResourceFinder;
    private final List<String> ivyPatterns;
    private final List<String> artifactPatterns;
    private final boolean m2compatible;

    public ExternalResourceArtifactResolver(ExternalResourceRepository repository, LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> locallyAvailableResourceFinder,
                                            List<String> ivyPatterns, List<String> artifactPatterns, boolean m2compatible) {
        this.repository = repository;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.ivyPatterns = ivyPatterns;
        this.artifactPatterns = artifactPatterns;
        this.m2compatible = m2compatible;
    }

    public ExternalResource resolveMetaDataArtifact(ModuleVersionArtifactMetaData artifact) {
        return findStaticResourceUsingPatterns(ivyPatterns, artifact, true);
    }

    public ExternalResource resolveArtifact(ModuleVersionArtifactMetaData artifact) {
        return findStaticResourceUsingPatterns(artifactPatterns, artifact, true);
    }

    public boolean artifactExists(ModuleVersionArtifactMetaData artifact) {
        return findStaticResourceUsingPatterns(artifactPatterns, artifact, false) != null;
    }

    private ExternalResource findStaticResourceUsingPatterns(List<String> patternList, ModuleVersionArtifactMetaData artifact, boolean forDownload) {
        for (String pattern : patternList) {
            ResourcePattern resourcePattern = toResourcePattern(pattern);
            String resourceName = resourcePattern.toPath(artifact);
            LOGGER.debug("Loading {}", resourceName);
            ExternalResource resource = getResource(resourceName, artifact, forDownload);
            if (resource.exists()) {
                return resource;
            } else {
                LOGGER.debug("Resource not reachable for {}: res={}", artifact, resource);
                discardResource(resource);
            }
        }
        return null;
    }

    private ExternalResource getResource(String source, ModuleVersionArtifactMetaData target, boolean forDownload) {
        try {
            if (forDownload) {
                LocallyAvailableResourceCandidates localCandidates = locallyAvailableResourceFinder.findCandidates(target);
                ExternalResource resource = repository.getResource(source, localCandidates);
                return resource == null ? new MissingExternalResource(source) : resource;
            } else {
                // TODO - there's a potential problem here in that we don't carry correct isLocal data in MetaDataOnlyExternalResource
                ExternalResourceMetaData metaData = repository.getResourceMetaData(source);
                return metaData == null ? new MissingExternalResource(source) : new MetaDataOnlyExternalResource(source, metaData);
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not get resource '%s'.", source), e);
        }
    }

    protected void discardResource(ExternalResource resource) {
        try {
            resource.close();
        } catch (IOException e) {
            LOGGER.warn("Exception closing resource " + resource.getName(), e);
        }
    }

    protected ResourcePattern toResourcePattern(String pattern) {
        return m2compatible ? new M2ResourcePattern(pattern) : new IvyResourcePattern(pattern);
    }
}
