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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleResolutionFilter;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.component.model.DefaultComponentUsage;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactSetResolveResult;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * An ArtifactSet that resolves the sets of artifacts on construction.
 * This is presently used to resolve artifacts for external modules, as we transition toward full separation of graph and artifact resolution.
 */
class EagerResolveConfigurationArtifactsSet extends AbstractArtifactSet {
    private final ResolvedConfigurationIdentifier configurationId;
    private final Set<ComponentArtifactMetaData> artifacts;

    public EagerResolveConfigurationArtifactsSet(ComponentResolveMetaData component, ResolvedConfigurationIdentifier configurationId, ModuleResolutionFilter selector, ArtifactResolver artifactResolver) {
        super(component.getId(), component.getSource(), artifactResolver);
        this.configurationId = configurationId;
        this.artifacts = doResolve(component, selector);
    }

    private Set<ComponentArtifactMetaData> doResolve(ComponentResolveMetaData component, ModuleResolutionFilter selector) {
        Set<ComponentArtifactMetaData> allArtifacts = resolveAllArtifacts(component);
        Set<ComponentArtifactMetaData> filteredArtifacts = new LinkedHashSet<ComponentArtifactMetaData>();

        ModuleIdentifier moduleId = configurationId.getId().getModule();
        for (ComponentArtifactMetaData artifact : allArtifacts) {
            IvyArtifactName artifactName = artifact.getName();
            if (!selector.acceptArtifact(moduleId, artifactName)) {
                continue;
            }
            filteredArtifacts.add(artifact);
        }

        return filteredArtifacts;
    }

    private Set<ComponentArtifactMetaData> resolveAllArtifacts(ComponentResolveMetaData component) {
        BuildableArtifactSetResolveResult result = new DefaultBuildableArtifactSetResolveResult();
        getArtifactResolver().resolveModuleArtifacts(component, new DefaultComponentUsage(configurationId.getConfiguration()), result);
        return result.getArtifacts();
    }

    @Override
    protected Set<ComponentArtifactMetaData> resolveComponentArtifacts() {
        return artifacts;
    }
}
