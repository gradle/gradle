/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DefaultModuleResolutionFilter;
import org.gradle.internal.component.model.ComponentArtifactIdentifier;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.resolver.ArtifactResolver;

import java.util.Map;
import java.util.Set;

/**
 * A set of artifacts that is defined by a dependency declaration.
 */
class DependencyArtifactSet extends AbstractArtifactSet {
    private final Set<ComponentArtifactMetaData> artifacts;

    public DependencyArtifactSet(ModuleVersionIdentifier ownerId, ModuleSource moduleSource, Set<ComponentArtifactMetaData> artifacts,
                                 ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvedArtifact> allResolvedArtifacts,
                                 long id) {
        super(ownerId, moduleSource, DefaultModuleResolutionFilter.all(), artifactResolver, allResolvedArtifacts, id);
        this.artifacts = artifacts;
    }

    @Override
    protected Set<ComponentArtifactMetaData> resolveComponentArtifacts() {
        return artifacts;
    }

}
