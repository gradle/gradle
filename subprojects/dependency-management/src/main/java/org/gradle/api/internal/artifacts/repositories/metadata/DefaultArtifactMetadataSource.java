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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A metadata source which simply verifies the existence of a given artifact and does not
 * attempt to fetch any further metadata from other external sources.
 */
public class DefaultArtifactMetadataSource extends AbstractMetadataSource<MutableModuleComponentResolveMetadata> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalResourceResolver.class);
    private final MutableModuleMetadataFactory<? extends MutableModuleComponentResolveMetadata> mutableModuleMetadataFactory;
    private final ArtifactSupplier artifactSupplier;

    public DefaultArtifactMetadataSource(MutableModuleMetadataFactory<? extends MutableModuleComponentResolveMetadata> mutableModuleMetadataFactory, ArtifactSupplier artifactSupplier) {
        this.mutableModuleMetadataFactory = mutableModuleMetadataFactory;
        this.artifactSupplier = artifactSupplier;
    }

    @Override
    public MutableModuleComponentResolveMetadata create(String repositoryName, ComponentResolvers componentResolvers, ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata prescribedMetaData, ExternalResourceArtifactResolver artifactResolver, BuildableModuleComponentMetaDataResolveResult result) {
        IvyArtifactName artifact = artifactSupplier.getArtifactName(moduleComponentIdentifier, prescribedMetaData);
        if (artifact == null || !artifactResolver.artifactExists(new DefaultModuleComponentArtifactMetadata(moduleComponentIdentifier, artifact), result)) {
            return null;
        }

        LOGGER.debug("Using default metadata for artifact in module '{}' and repository '{}'.", moduleComponentIdentifier, repositoryName);

        MutableModuleComponentResolveMetadata metadata = mutableModuleMetadataFactory.missing(moduleComponentIdentifier);

        // For empty metadata, we use a hash based on the identifier
        HashCode descriptorHash = Hashing.md5().hashString(moduleComponentIdentifier.toString());
        metadata.getSources().add(new ModuleDescriptorHashModuleSource(descriptorHash, false));
        return metadata;
    }

    @Override
    public void listModuleVersions(ModuleDependencyMetadata dependency, ModuleIdentifier module, List<ResourcePattern> ivyPatterns, List<ResourcePattern> artifactPatterns, VersionLister versionLister, BuildableModuleVersionListingResolveResult result) {
        // List modules with missing metadata files
        IvyArtifactName dependencyArtifact = artifactSupplier.getPrimaryDependencyArtifact(dependency);
        if (dependencyArtifact != null) {
            versionLister.listVersions(module, dependencyArtifact, artifactPatterns, result);
        }
    }

    /**
     * Specifies which artifact should be used as the metadata definition.
     */
    public interface ArtifactSupplier {

        /**
         * @param moduleComponentIdentifier The module ID of the artifact to fetch metadata for.
         * @param prescribedMetaData The metadata of the artifact to resolve, as defined by the dependency descriptor.
         *
         * @return The name of the artifact for which metadata should be resolved. Or null if metadata should not be resolved for this artifact.
         */
        @Nullable
        IvyArtifactName getArtifactName(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata prescribedMetaData);

        /**
         * @param dependency The dependency to fetch the primary artifact for.
         *
         * @return The name of the primary artifact for {@code dependency}, or null if one does not exist.
         */
        @Nullable
        IvyArtifactName getPrimaryDependencyArtifact(ModuleDependencyMetadata dependency);
    }

    /**
     * If there is prescribed metadata and it has an artifact, return it. Otherwise, default to using the jar artifact.
     */
    public static class JarDefaultingArtifactSupplier implements ArtifactSupplier {
        @Override
        public IvyArtifactName getArtifactName(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata prescribedMetaData)  {
            if (prescribedMetaData.getArtifact() != null) {
                return prescribedMetaData.getArtifact();
            } else {
                return new DefaultIvyArtifactName(moduleComponentIdentifier.getModule(), "jar", "jar");
            }
        }

        @Override
        public IvyArtifactName getPrimaryDependencyArtifact(ModuleDependencyMetadata dependency) {
            List<IvyArtifactName> artifacts = dependency.getArtifacts();
            if (artifacts.isEmpty()) {
                return new DefaultIvyArtifactName(dependency.getSelector().getModule(), "jar", "jar");
            }
            return artifacts.get(0);
        }
    }

    /**
     * If the prescribed metadata defines an artifact with an explicit extension, use that artifact. Otherwise, resolve no metadata.
     */
    public static class ExplicitExtensionArtifactSupplier implements ArtifactSupplier {
        @Nullable
        @Override
        public IvyArtifactName getArtifactName(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata prescribedMetaData) {
            if (prescribedMetaData.getArtifact() == null || prescribedMetaData.getArtifact().getExtension() == null) {
                return null;
            }
            return prescribedMetaData.getArtifact();
        }

        @Nullable
        @Override
        public IvyArtifactName getPrimaryDependencyArtifact(ModuleDependencyMetadata dependency) {
            if (dependency.getArtifacts().isEmpty()) {
                return null;
            }
            IvyArtifactName artifact = dependency.getArtifacts().get(0);
            if (artifact.getExtension() == null) {
                return null;
            }
            return artifact;
        }
    }
}
