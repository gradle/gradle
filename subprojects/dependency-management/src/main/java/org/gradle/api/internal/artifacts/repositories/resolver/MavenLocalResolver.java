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

import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.ModuleMetadataParser;
import org.gradle.api.internal.artifacts.repositories.ImmutableMetadataSources;
import org.gradle.api.internal.artifacts.repositories.MetadataArtifactProvider;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.MutableMavenModuleResolveMetadata;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;

import java.net.URI;

public class MavenLocalResolver extends MavenResolver {
    public MavenLocalResolver(String name, URI rootUri, RepositoryTransport transport,
                              LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                              FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                              MetaDataParser<MutableMavenModuleResolveMetadata> pomParser,
                              ModuleMetadataParser metadataParser,
                              ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                              CacheAwareExternalResourceAccessor cacheAwareExternalResourceAccessor,
                              FileResourceRepository fileResourceRepository,
                              boolean preferGradleMetadata,
                              ImmutableMetadataSources repositoryContentFilter,
                              MetadataArtifactProvider metadataArtifactProvider) {
        super(name, rootUri, transport, locallyAvailableResourceFinder, artifactFileStore, pomParser, metadataParser, moduleIdentifierFactory, cacheAwareExternalResourceAccessor, null, fileResourceRepository, preferGradleMetadata, repositoryContentFilter, metadataArtifactProvider);
    }

}
