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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;

/**
 * Provides access to a repository of components that are identified by a ModuleComponentIdentifier.
 *
 * The plan is to eventually sync this with
 * {@link org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver},
 * {@link org.gradle.internal.resolve.resolver.ComponentMetaDataResolver} and
 * {@link org.gradle.internal.resolve.resolver.ArtifactResolver}.
 */
public interface ModuleComponentRepositoryAccess {
    /**
     * Resolves the given dependency to a list of module versions.
     */
    void listModuleVersions(ModuleDependencyMetadata dependency, BuildableModuleVersionListingResolveResult result);

    /**
     * Resolves the metadata for a module component.
     */
    void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult result);

    /**
     * Resolves a set of artifacts belonging to the given component, with the type specified. Any failures are packaged up in the result.
     */
    void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result);

    /**
     * Resolves the given artifact. Any failures are packaged up in the result.
     */
    void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactFileResolveResult result);

    MetadataFetchingCost estimateMetadataFetchingCost(ModuleComponentIdentifier moduleComponentIdentifier);
}
