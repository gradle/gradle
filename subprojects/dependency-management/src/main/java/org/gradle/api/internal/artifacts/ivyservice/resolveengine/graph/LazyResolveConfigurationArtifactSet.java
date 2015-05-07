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
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleResolutionFilter;
import org.gradle.internal.component.model.*;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * An ArtifactSet implementation that resolves the artifacts for a component on demand.
 * Currently, this implementation lacks context (ModuleSource, ComponentOverrideMetadata) to allow it to correctly and efficiently
 * resolve artifacts for external components. Thus it is currently only used to resolve artifacts for project components.
 */
class LazyResolveConfigurationArtifactSet extends AbstractArtifactSet {
    private final ResolvedConfigurationIdentifier configurationId;
    private final ModuleResolutionFilter selector;
    private final ComponentIdentifier componentIdentifier;
    private final ComponentMetaDataResolver componentResolver;
    private Set<ComponentArtifactMetaData> artifacts;

    public LazyResolveConfigurationArtifactSet(ComponentResolveMetaData component, ResolvedConfigurationIdentifier configurationId, ModuleResolutionFilter selector,
                                               ComponentMetaDataResolver componentResolver, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvedArtifact> allResolvedArtifacts) {
        super(component.getId(), component.getSource(), artifactResolver, allResolvedArtifacts);
        this.componentIdentifier = component.getComponentId();
        this.componentResolver = componentResolver;
        this.configurationId = configurationId;
        this.selector = selector;
    }

    @Override
    protected Set<ComponentArtifactMetaData> resolveComponentArtifacts() {
        if (artifacts == null) {
            // TODO:DAZ For this to work with external components, we'll need to use the correct ModuleSource and ComponentOverrideMetadata to resolve the component
            BuildableComponentResolveResult moduleResolveResult = new DefaultBuildableComponentResolveResult();
            componentResolver.resolve(componentIdentifier, new DefaultComponentOverrideMetadata(), moduleResolveResult);
            ComponentResolveMetaData component = moduleResolveResult.getMetaData();

            BuildableArtifactSetResolveResult result = new DefaultBuildableArtifactSetResolveResult();
            getArtifactResolver().resolveModuleArtifacts(component, new DefaultComponentUsage(configurationId.getConfiguration()), result);
            artifacts = result.getArtifacts();
        }

        Set<ComponentArtifactMetaData> result = new LinkedHashSet<ComponentArtifactMetaData>();
        ModuleIdentifier moduleId = configurationId.getId().getModule();
        for (ComponentArtifactMetaData artifact : artifacts) {
            IvyArtifactName artifactName = artifact.getName();
            if (!selector.acceptArtifact(moduleId, artifactName)) {
                continue;
            }
            result.add(artifact);
        }

        return result;
    }
}
