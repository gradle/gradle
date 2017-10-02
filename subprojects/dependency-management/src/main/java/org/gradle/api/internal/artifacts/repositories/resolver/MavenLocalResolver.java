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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.ModuleMetadataParser;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.MutableMavenModuleResolveMetadata;
import org.gradle.internal.resolve.result.DefaultResourceAwareResolveResult;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URI;

public class MavenLocalResolver extends MavenResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenResolver.class);

    public MavenLocalResolver(String name, URI rootUri, RepositoryTransport transport,
                              LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                              FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                              MetaDataParser<MutableMavenModuleResolveMetadata> pomParser,
                              ModuleMetadataParser metadataParser,
                              ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                              CacheAwareExternalResourceAccessor cacheAwareExternalResourceAccessor,
                              FileResourceRepository fileResourceRepository,
                              boolean preferGradleMetadata) {
        super(name, rootUri, transport, locallyAvailableResourceFinder, artifactFileStore, pomParser, metadataParser, moduleIdentifierFactory, cacheAwareExternalResourceAccessor, null, fileResourceRepository, preferGradleMetadata);
    }

    @Override
    @Nullable
    protected MutableMavenModuleResolveMetadata parseMetaDataFromArtifact(ModuleComponentIdentifier moduleComponentIdentifier, ExternalResourceArtifactResolver artifactResolver, ResourceAwareResolveResult result) {
        MutableMavenModuleResolveMetadata metadata = super.parseMetaDataFromArtifact(moduleComponentIdentifier, artifactResolver, result);
        if (metadata == null) {
            return null;
        }

        if (isOrphanedPom(metadata, artifactResolver)) {
            return null;
        }
        return metadata;
    }

    private boolean isOrphanedPom(MutableMavenModuleResolveMetadata metaData, ExternalResourceArtifactResolver artifactResolver) {
        if (metaData.isPomPackaging()) {
            return false;
        }

        // check custom packaging
        ModuleComponentArtifactMetadata artifact;
        if (metaData.isKnownJarPackaging()) {
            artifact = metaData.artifact("jar", "jar", null);
        } else {
            artifact = metaData.artifact(metaData.getPackaging(), metaData.getPackaging(), null);
        }

        if (artifactResolver.artifactExists(artifact, new DefaultResourceAwareResolveResult())) {
            return false;
        }

        LOGGER.debug("POM file found for module '{}' in repository '{}' but no artifact found. Ignoring.", metaData.getId(), getName());
        return true;
    }
}
