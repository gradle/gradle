/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern;
import org.gradle.api.internal.artifacts.repositories.resolver.VersionLister;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;

import java.util.List;

/**
 * A module metadata source which is a pure performance optimization. Because today in the wild there
 * are very few Gradle metadata sources, this source will first try to get a POM file (or an Ivy file)
 * and if it finds a marker in the POM (Ivy), it will use Gradle metadata instead.
 *
 * It also means that we're going to pay a small price if Gradle metadata is present: we would fetch
 * a POM file and parse it, then fetch Gradle metadata and parse it (doing twice the work).
 */
public class RedirectingGradleMetadataModuleMetadataSource implements MetadataSource<MutableModuleComponentResolveMetadata> {
    private final MetadataSource<?> delegate;
    private final MetadataSource<MutableModuleComponentResolveMetadata> gradleModuleMetadataSource;

    public RedirectingGradleMetadataModuleMetadataSource(MetadataSource<?> delegate, MetadataSource<MutableModuleComponentResolveMetadata> gradleModuleMetadataSource) {
        this.delegate = delegate;
        this.gradleModuleMetadataSource = gradleModuleMetadataSource;
    }

    @Override
    public MutableModuleComponentResolveMetadata create(String repositoryName, ComponentResolvers componentResolvers, ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata prescribedMetaData, ExternalResourceArtifactResolver artifactResolver, BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata> result) {
        MutableModuleComponentResolveMetadata metadata = delegate.create(repositoryName, componentResolvers, moduleComponentIdentifier, prescribedMetaData, artifactResolver, result);
        if (result.shouldUseGradleMetatada()) {
            MutableModuleComponentResolveMetadata resolveMetadata = gradleModuleMetadataSource.create(repositoryName, componentResolvers, moduleComponentIdentifier, prescribedMetaData, artifactResolver, result);
            if (resolveMetadata != null) {
                return resolveMetadata;
            }
        }
        return metadata;
    }

    @Override
    public void listModuleVersions(ModuleComponentSelector selector, ComponentOverrideMetadata overrideMetadata, List<ResourcePattern> ivyPatterns, List<ResourcePattern> artifactPatterns, VersionLister versionLister, BuildableModuleVersionListingResolveResult result) {
        delegate.listModuleVersions(selector, overrideMetadata, ivyPatterns, artifactPatterns, versionLister, result);
    }
}
