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

package org.gradle.internal.resolve.resolver;

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.FileDependencyArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.internal.Describables;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentArtifactsResolveResult;

import java.util.Map;
import java.util.Set;

public class DefaultArtifactSelector implements ArtifactSelector {
    private final Map<ComponentArtifactIdentifier, ResolvedArtifact> allResolvedArtifacts = Maps.newHashMap();
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final ArtifactResolver artifactResolver;

    public DefaultArtifactSelector(ArtifactResolver artifactResolver, ArtifactTypeRegistry artifactTypeRegistry) {
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.artifactResolver = artifactResolver;
    }

    @Override
    public ArtifactSet resolveArtifacts(LocalFileDependencyMetadata fileDependencyMetadata) {
        return new FileDependencyArtifactSet(fileDependencyMetadata, artifactTypeRegistry);
    }

    @Override
    public ArtifactSet resolveArtifacts(ComponentResolveMetadata component, ConfigurationMetadata configuration, ModuleExclusion exclusions) {
        BuildableComponentArtifactsResolveResult result = new DefaultBuildableComponentArtifactsResolveResult();
        artifactResolver.resolveArtifacts(component, result);
        return result.getResult().getArtifactsFor(component, configuration, artifactResolver, allResolvedArtifacts, artifactTypeRegistry, exclusions);
    }

    @Override
    public ArtifactSet resolveArtifacts(ComponentResolveMetadata component, Set<? extends ComponentArtifactMetadata> artifacts) {
        return DefaultArtifactSet.singleVariant(component.getComponentId(), component.getId(), Describables.of(component.getComponentId()), artifacts, component.getSource(), ModuleExclusions.excludeNone(), component.getAttributesSchema(), artifactResolver, allResolvedArtifacts, artifactTypeRegistry);
    }
}
