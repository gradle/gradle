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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.api.internal.artifacts.repositories.maven.MavenMetadataLoader;
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern;
import org.gradle.api.internal.artifacts.repositories.resolver.VersionLister;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;
import org.gradle.internal.resource.local.FileResourceRepository;

import javax.inject.Inject;
import java.util.List;

import static org.gradle.api.internal.artifacts.repositories.metadata.DefaultArtifactMetadataSource.getPrimaryDependencyArtifact;

public class MavenLocalPomMetadataSource extends DefaultMavenPomMetadataSource {

    @Inject
    public MavenLocalPomMetadataSource(MetadataArtifactProvider metadataArtifactProvider, MetaDataParser<MutableMavenModuleResolveMetadata> pomParser, FileResourceRepository fileResourceRepository, MavenMetadataValidator validator, MavenMetadataLoader mavenMetadataLoader, ChecksumService checksumService) {
        super(metadataArtifactProvider, pomParser, fileResourceRepository, validator, mavenMetadataLoader, checksumService);
    }

    @Override
    public void listModuleVersions(ModuleDependencyMetadata dependency, ModuleIdentifier module, List<ResourcePattern> ivyPatterns, List<ResourcePattern> artifactPatterns, VersionLister versionLister, BuildableModuleVersionListingResolveResult result) {
        IvyArtifactName dependencyArtifact = getPrimaryDependencyArtifact(dependency);
        versionLister.listVersions(module, dependencyArtifact, artifactPatterns, result);
    }
}
