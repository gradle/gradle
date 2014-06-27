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
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;
import org.gradle.api.internal.component.ArtifactType;

/**
 * Provides access to a repository of components that are identified by a ModuleComponentIdentifier.
 */
public interface ModuleComponentRepositoryAccess {
    /**
     * Resolves the given dependency to a list of module versions.
     */
    void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result);

    /**
     * Resolves the metadata for a module component.
     */
    void resolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result);

    /**
     * Resolves a set of artifacts belonging to the given component, based on the supplied usage. Any failures are packaged up in the result.
     */
    void resolveModuleArtifacts(ComponentMetaData component, ComponentUsage componentUsage, BuildableArtifactSetResolveResult result);

    /**
     * Resolves a set of artifacts belonging to the given component, with the type specified. Any failures are packaged up in the result.
     */
    void resolveModuleArtifacts(ComponentMetaData component, ArtifactType artifactType, BuildableArtifactSetResolveResult result);

    /**
     * Resolves the given artifact. Any failures are packaged up in the result.
     */
    void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result);
}
