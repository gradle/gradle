/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.metadata;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.repositories.maven.MavenMetadataLoader;
import org.gradle.api.internal.artifacts.repositories.maven.MavenVersionLister;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier;
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern;
import org.gradle.api.internal.artifacts.repositories.resolver.VersionLister;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;

import javax.inject.Inject;
import java.util.List;

public class DefaultMavenPomMetadataSource extends AbstractRepositoryMetadataSource<MutableMavenModuleResolveMetadata> {

    private final MetaDataParser<MutableMavenModuleResolveMetadata> pomParser;
    private final MavenMetadataValidator validator;
    private final MavenMetadataLoader mavenMetadataLoader;
    private final ChecksumService checksumService;

    @Inject
    public DefaultMavenPomMetadataSource(MetadataArtifactProvider metadataArtifactProvider, MetaDataParser<MutableMavenModuleResolveMetadata> pomParser, FileResourceRepository fileResourceRepository, MavenMetadataValidator validator, MavenMetadataLoader mavenMetadataLoader, ChecksumService checksumService) {
        super(metadataArtifactProvider, fileResourceRepository, checksumService);
        this.pomParser = pomParser;
        this.validator = validator;
        this.mavenMetadataLoader = mavenMetadataLoader;
        this.checksumService = checksumService;
    }

    @Override
    protected MetaDataParser.ParseResult<MutableMavenModuleResolveMetadata> parseMetaDataFromResource(ModuleComponentIdentifier moduleComponentIdentifier, LocallyAvailableExternalResource cachedResource, ExternalResourceArtifactResolver artifactResolver, DescriptorParseContext context, String repoName) {
       MetaDataParser.ParseResult<MutableMavenModuleResolveMetadata> parseResult = pomParser.parseMetaData(context, cachedResource);
        MutableMavenModuleResolveMetadata metaData = parseResult.getResult();
        if (metaData != null) {
            if (moduleComponentIdentifier instanceof MavenUniqueSnapshotComponentIdentifier) {
                // Snapshot POMs use -SNAPSHOT instead of the timestamp as version, so validate against the expected id
                MavenUniqueSnapshotComponentIdentifier snapshotComponentIdentifier = (MavenUniqueSnapshotComponentIdentifier) moduleComponentIdentifier;
                checkMetadataConsistency(snapshotComponentIdentifier.getSnapshotComponent(), metaData);

                metaData.setId(snapshotComponentIdentifier);
                metaData.setSnapshotTimestamp(snapshotComponentIdentifier.getTimestamp());
            } else {
                checkMetadataConsistency(moduleComponentIdentifier, metaData);
            }
            MutableMavenModuleResolveMetadata result = MavenResolver.processMetaData(metaData);
            result.getSources().add(new ModuleDescriptorHashModuleSource(
                checksumService.md5(cachedResource.getFile()),
                metaData.isChanging()
            ));
            if (validator.isUsableModule(repoName, result, artifactResolver)) {
                return parseResult;
            }
            return null;
        }
        return parseResult;
    }

    @Override
    public void listModuleVersions(ModuleDependencyMetadata dependency, ModuleIdentifier module, List<ResourcePattern> ivyPatterns, List<ResourcePattern> artifactPatterns, VersionLister versionLister, BuildableModuleVersionListingResolveResult result) {
        new MavenVersionLister(mavenMetadataLoader).listVersions(module, ivyPatterns, result);
    }

    /**
     * Checks if the POM looks valid to use as a metadata source.
     * In general this will true for all discovered POM files, but in `mavenLocal()` we ignore 'orphaned' POM files that
     * do not have a corresponding artifact.
     */
    public interface MavenMetadataValidator {
        boolean isUsableModule(String repoName, MutableMavenModuleResolveMetadata metadata, ExternalResourceArtifactResolver artifactResolver);
    }
}
