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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern;
import org.gradle.api.internal.artifacts.repositories.resolver.VersionLister;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * A metadata source which simply verifies the existence of a given artifact and does not
 * attempt to fetch any further metadata from other external sources.
 */
public class DefaultArtifactMetadataSource implements MetadataSource<MutableModuleComponentResolveMetadata> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalResourceResolver.class);
    private final MutableModuleMetadataFactory<? extends MutableModuleComponentResolveMetadata> mutableModuleMetadataFactory;

    public DefaultArtifactMetadataSource(MutableModuleMetadataFactory<? extends MutableModuleComponentResolveMetadata> mutableModuleMetadataFactory) {
        this.mutableModuleMetadataFactory = mutableModuleMetadataFactory;
    }

    @Override
    public MutableModuleComponentResolveMetadata create(String repositoryName, ComponentResolvers componentResolvers, ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata prescribedMetaData, ExternalResourceArtifactResolver artifactResolver, BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata> result) {
        IvyArtifactName artifact = getPrimaryArtifact(moduleComponentIdentifier, prescribedMetaData);
        if (!artifactResolver.artifactExists(new DefaultModuleComponentArtifactMetadata(moduleComponentIdentifier, artifact), result)) {
            return null;
        }

        LOGGER.debug("Using default metadata for artifact in module '{}' and repository '{}'.", moduleComponentIdentifier, repositoryName);

        MutableModuleComponentResolveMetadata metadata = mutableModuleMetadataFactory.missing(moduleComponentIdentifier);

        // For empty metadata, we use a hash based on the identifier
        HashCode descriptorHash = Hashing.md5().hashString(moduleComponentIdentifier.toString());
        metadata.getSources().add(new ModuleDescriptorHashModuleSource(descriptorHash, false));
        return metadata;
    }

    private static IvyArtifactName getPrimaryArtifact(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata overrideMetadata) {
        if (overrideMetadata.getArtifact() != null) {
            return overrideMetadata.getArtifact();
        }
        return new DefaultIvyArtifactName(moduleComponentIdentifier.getModule(), "jar", "jar");
    }

    @Override
    public void listModuleVersions(ModuleComponentSelector selector, ComponentOverrideMetadata overrideMetadata, List<ResourcePattern> ivyPatterns, List<ResourcePattern> artifactPatterns, VersionLister versionLister, BuildableModuleVersionListingResolveResult result) {
        // List modules with missing metadata files
        IvyArtifactName dependencyArtifact = getPrimaryArtifact(selector, overrideMetadata);
        versionLister.listVersions(selector.getModuleIdentifier(), dependencyArtifact, artifactPatterns, result);
    }

    private static IvyArtifactName getPrimaryArtifact(ModuleComponentSelector selector, ComponentOverrideMetadata overrideMetadata) {
        if (overrideMetadata.getArtifact() != null) {
            return overrideMetadata.getArtifact();
        }
        return new DefaultIvyArtifactName(selector.getModule(), "jar", "jar");
    }

}
