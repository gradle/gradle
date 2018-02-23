/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifacts;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.resolve.resolver.ArtifactResolver;

import java.util.List;
import java.util.Map;

/**
 * Uses a fixed set of artifacts for all configurations.
 */
public class FixedComponentArtifacts implements ComponentArtifacts {
    private final List<ComponentArtifactMetadata> artifacts;

    public FixedComponentArtifacts(Iterable<? extends ComponentArtifactMetadata> artifacts) {
        this.artifacts = ImmutableList.copyOf(artifacts);
    }

    public List<ComponentArtifactMetadata> getArtifacts() {
        return artifacts;
    }

    @Override
    public ArtifactSet getArtifactsFor(ComponentResolveMetadata component, ConfigurationMetadata configuration, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, ArtifactTypeRegistry artifactTypeRegistry, ModuleExclusion exclusions) {
        return DefaultArtifactSet.singleVariant(component.getComponentId(), component.getId(), configuration.asDescribable(), artifacts, component.getSource(), exclusions, component.getAttributesSchema(), artifactResolver, allResolvedArtifacts, artifactTypeRegistry);
    }
}
