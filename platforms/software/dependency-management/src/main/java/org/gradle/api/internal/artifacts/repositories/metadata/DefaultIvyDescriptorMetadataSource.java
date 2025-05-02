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
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern;
import org.gradle.api.internal.artifacts.repositories.resolver.VersionLister;
import org.gradle.internal.component.external.model.ivy.MutableIvyModuleResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;

import javax.inject.Inject;
import java.util.List;

public class DefaultIvyDescriptorMetadataSource extends AbstractRepositoryMetadataSource<MutableIvyModuleResolveMetadata> {

    private final MetaDataParser<MutableIvyModuleResolveMetadata> metaDataParser;
    private final ChecksumService checksumService;

    @Inject
    public DefaultIvyDescriptorMetadataSource(MetadataArtifactProvider metadataArtifactProvider, MetaDataParser<MutableIvyModuleResolveMetadata> metaDataParser, FileResourceRepository fileResourceRepository, ChecksumService checksumService) {
        super(metadataArtifactProvider, fileResourceRepository, checksumService);
        this.metaDataParser = metaDataParser;
        this.checksumService = checksumService;
    }

    @Override
    protected MetaDataParser.ParseResult<MutableIvyModuleResolveMetadata> parseMetaDataFromResource(ModuleComponentIdentifier moduleComponentIdentifier, LocallyAvailableExternalResource cachedResource, ExternalResourceArtifactResolver artifactResolver, DescriptorParseContext context, String repoName) {
        MetaDataParser.ParseResult<MutableIvyModuleResolveMetadata> parseResult = metaDataParser.parseMetaData(context, cachedResource);
        MutableIvyModuleResolveMetadata metaData = parseResult.getResult();
        if (metaData != null) {
            metaData.getSources().add(new ModuleDescriptorHashModuleSource(
                checksumService.md5(cachedResource.getFile()),
                metaData.isChanging()
            ));
            checkMetadataConsistency(moduleComponentIdentifier, metaData);
        }
        return parseResult;
    }

    @Override
    public void listModuleVersions(ModuleComponentSelector selector, ComponentOverrideMetadata overrideMetadata, List<ResourcePattern> ivyPatterns, List<ResourcePattern> artifactPatterns, VersionLister versionLister, BuildableModuleVersionListingResolveResult result) {
        // List modules based on metadata files (artifact version is not considered in listVersionsForAllPatterns())
        ModuleIdentifier module = selector.getModuleIdentifier();
        IvyArtifactName metaDataArtifact = metadataArtifactProvider.getMetaDataArtifactName(module.getName());
        versionLister.listVersions(module, metaDataArtifact, ivyPatterns, result);
    }
}
