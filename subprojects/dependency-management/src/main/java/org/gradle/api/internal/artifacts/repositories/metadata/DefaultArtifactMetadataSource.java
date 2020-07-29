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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern;
import org.gradle.api.internal.artifacts.repositories.resolver.VersionLister;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.List;

public class DefaultArtifactMetadataSource extends AbstractMetadataSource<MutableModuleComponentResolveMetadata> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalResourceResolver.class);
    private final MutableModuleMetadataFactory<? extends MutableModuleComponentResolveMetadata> mutableModuleMetadataFactory;
    private final String artifactType;
    private final String artifactExtension;

    @Inject
    public DefaultArtifactMetadataSource(MutableModuleMetadataFactory<? extends MutableModuleComponentResolveMetadata> mutableModuleMetadataFactory) {
        this.artifactType = "jar";
        this.artifactExtension = "jar";
        this.mutableModuleMetadataFactory = mutableModuleMetadataFactory;
    }

    @Override
    public MutableModuleComponentResolveMetadata create(String repositoryName, ComponentResolvers componentResolvers, ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata prescribedMetaData, ExternalResourceArtifactResolver artifactResolver, BuildableModuleComponentMetaDataResolveResult result) {
        MutableModuleComponentResolveMetadata metaDataFromDefaultArtifact = createMetaDataFromDependencyArtifact(moduleComponentIdentifier, prescribedMetaData, artifactResolver, result);
        if (metaDataFromDefaultArtifact != null) {
            LOGGER.debug("Found artifact but no meta-data for module '{}' in repository '{}', using default meta-data.", moduleComponentIdentifier, repositoryName);
            metaDataFromDefaultArtifact.getSources()
                .add(new ModuleDescriptorHashModuleSource(getDescriptorHash(moduleComponentIdentifier), false));
            return metaDataFromDefaultArtifact;
        }
        return null;
    }

    private HashCode getDescriptorHash(ModuleComponentIdentifier moduleComponentIdentifier) {
        // For empty metadata, we use a hash based on the identifier
        return Hashing.md5().hashString(moduleComponentIdentifier.toString());
    }

    @Nullable
    private MutableModuleComponentResolveMetadata createMetaDataFromDependencyArtifact(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata overrideMetadata, ExternalResourceArtifactResolver artifactResolver, ResourceAwareResolveResult result) {
        List<IvyArtifactName> artifactNames = getArtifactNames(moduleComponentIdentifier, overrideMetadata);
        for (IvyArtifactName artifact : artifactNames) {
            if (artifactResolver.artifactExists(new DefaultModuleComponentArtifactMetadata(moduleComponentIdentifier, artifact), result)) {
                return mutableModuleMetadataFactory.missing(moduleComponentIdentifier);
            }
        }
        return null;
    }

    private List<IvyArtifactName> getArtifactNames(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata overrideMetadata) {
        if (!overrideMetadata.getArtifacts().isEmpty()) {
            return overrideMetadata.getArtifacts();
        }
        IvyArtifactName defaultArtifact = new DefaultIvyArtifactName(moduleComponentIdentifier.getModule(), artifactType, artifactExtension);
        return ImmutableList.of(defaultArtifact);
    }

    @Override
    public void listModuleVersions(ModuleDependencyMetadata dependency, ModuleIdentifier module, List<ResourcePattern> ivyPatterns, List<ResourcePattern> artifactPatterns, VersionLister versionLister, BuildableModuleVersionListingResolveResult result) {
        // List modules with missing metadata files
        IvyArtifactName dependencyArtifact = getPrimaryDependencyArtifact(dependency);
        versionLister.listVersions(module, dependencyArtifact, artifactPatterns, result);
    }

    static IvyArtifactName getPrimaryDependencyArtifact(ModuleDependencyMetadata dependency) {
        String moduleName = dependency.getSelector().getModule();
        List<IvyArtifactName> artifacts = dependency.getArtifacts();
        if (artifacts.isEmpty()) {
            return new DefaultIvyArtifactName(moduleName, "jar", "jar");
        }
        return artifacts.get(0);
    }

}
