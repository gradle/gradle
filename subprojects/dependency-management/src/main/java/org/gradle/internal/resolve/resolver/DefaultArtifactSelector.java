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
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.FileDependencyArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.internal.Describables;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DefaultArtifactSelector implements ArtifactSelector {
    private final Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts = Maps.newHashMap();
    private final List<OriginArtifactSelector> selectors;
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final ArtifactResolver artifactResolver;

    public DefaultArtifactSelector(List<OriginArtifactSelector> selectors, ArtifactResolver artifactResolver, ArtifactTypeRegistry artifactTypeRegistry) {
        this.selectors = selectors;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.artifactResolver = artifactResolver;
    }

    @Override
    public ArtifactSet resolveArtifacts(LocalFileDependencyMetadata fileDependencyMetadata) {
        return new FileDependencyArtifactSet(fileDependencyMetadata, artifactTypeRegistry);
    }

    @Override
    public ArtifactSet resolveArtifacts(ComponentResolveMetadata component, ConfigurationMetadata configuration, ModuleExclusion exclusions) {
        ArtifactSet artifacts = null;
        for (OriginArtifactSelector selector : selectors) {
            artifacts = selector.resolveArtifacts(component, configuration, artifactTypeRegistry, exclusions);
            if (artifacts != null) {
                break;
            }
        }
        if (artifacts == null) {
            throw new IllegalStateException("No artifacts selected.");
        }
        return artifacts;
    }

    @Override
    public ArtifactSet resolveArtifacts(ComponentResolveMetadata component, Collection<? extends ComponentArtifactMetadata> artifacts) {
        return DefaultArtifactSet.singleVariant(component.getComponentId(), component.getId(), Describables.of(component.getComponentId()), artifacts, component.getSource(), ModuleExclusions.excludeNone(), component.getAttributesSchema(), artifactResolver, allResolvedArtifacts, artifactTypeRegistry);
    }
}
