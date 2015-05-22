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

import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleResolutionFilter;
import org.gradle.internal.component.model.ComponentArtifactIdentifier;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.component.model.DefaultComponentUsage;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactSetResolveResult;

import java.util.Map;
import java.util.Set;

/**
 * An ArtifactSet implementation that resolves the artifacts for a component on demand.
 * Currently, this implementation lacks context (ModuleSource, ComponentOverrideMetadata) to allow it to correctly and efficiently
 * resolve artifacts for external components. Thus it is currently only used to resolve artifacts for project components.
 */
class LazyResolveConfigurationArtifactSet extends AbstractArtifactSet {
    // TODO:DAZ We are holding onto the resolved component metadata, rather than re-resolving
    // While this means we're holding more in memory, the overhead of re-resolving is too great, at present
    // For now we'll just minimise the size of the metadata, and perhaps later cut down on the work required to perform a component resolve
    private final ComponentResolveMetaData component;
    private final ResolvedConfigurationIdentifier configurationId;

    public LazyResolveConfigurationArtifactSet(ComponentResolveMetaData component, ResolvedConfigurationIdentifier configurationId, ModuleResolutionFilter selector,
                                               ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvedArtifact> allResolvedArtifacts,
                                               long id) {
        super(component.getId(), component.getSource(), selector, artifactResolver, allResolvedArtifacts, id);
        this.component = component;
        this.configurationId = configurationId;
    }

    @Override
    protected Set<ComponentArtifactMetaData> resolveComponentArtifacts() {
        BuildableArtifactSetResolveResult artifactSetResolveResult = new DefaultBuildableArtifactSetResolveResult();
        getArtifactResolver().resolveModuleArtifacts(component, new DefaultComponentUsage(configurationId.getConfiguration()), artifactSetResolveResult);
        return artifactSetResolveResult.getArtifacts();
    }
}
