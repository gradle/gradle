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
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern;
import org.gradle.api.internal.artifacts.repositories.resolver.VersionLister;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Represents a source of metadata for a repository. Each implementation is responsible for a different metadata
 * format: for discovering the metadata artifact, parsing the metadata and constructing a `MutableModuleComponentResolveMetadata`.
 */
public interface MetadataSource<S extends MutableModuleComponentResolveMetadata> {

    @Nullable
    S create(String repositoryName,
             ComponentResolvers componentResolvers,
             ModuleComponentIdentifier moduleComponentIdentifier,
             ComponentOverrideMetadata prescribedMetaData,
             ExternalResourceArtifactResolver artifactResolver, // Required for MavenLocal to verify the presence of the artifact
             BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata> result);

    /**
     * Use the supplied patterns and version lister to list available versions for the supplied dependency/module.
     *
     * This method would encapsulates all version listing for a metadata source, supplying the result (if found) to the
     * {@link BuildableModuleVersionListingResolveResult} parameter.
     *
     * Ideally, the ivyPatterns + artifactPatterns + versionLister would be encapsulated into a single 'module resource accessor'.
     */
    void listModuleVersions(ModuleComponentSelector selector, ComponentOverrideMetadata overrideMetadata, List<ResourcePattern> ivyPatterns, List<ResourcePattern> artifactPatterns, VersionLister versionLister, BuildableModuleVersionListingResolveResult result);

}
