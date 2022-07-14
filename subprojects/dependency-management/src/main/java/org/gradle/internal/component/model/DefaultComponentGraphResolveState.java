/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.component.model;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.resolve.resolver.ArtifactSelector;

public class DefaultComponentGraphResolveState<T extends ComponentResolveMetadata> extends AbstractComponentGraphResolveState<T> {
    public DefaultComponentGraphResolveState(T metadata) {
        super(metadata);
    }

    @Override
    public VariantArtifactGraphResolveMetadata resolveArtifactsFor(VariantGraphResolveMetadata variant) {
        return (VariantArtifactGraphResolveMetadata) variant;
    }

    @Override
    public VariantArtifactResolveState prepareForArtifactResolution(VariantGraphResolveMetadata variant) {
        ConfigurationMetadata configurationMetadata = (ConfigurationMetadata) variant;
        return new DefaultVariantArtifactResolveState(getMetadata(), configurationMetadata);
    }

    private static class DefaultVariantArtifactResolveState implements VariantArtifactResolveState {
        private final ComponentResolveMetadata component;
        private final ConfigurationMetadata configuration;

        public DefaultVariantArtifactResolveState(ComponentResolveMetadata componentMetadata, ConfigurationMetadata configuration) {
            this.component = componentMetadata;
            this.configuration = configuration;
        }

        @Override
        public ComponentArtifactMetadata resolveArtifact(IvyArtifactName artifact) {
            return configuration.artifact(artifact);
        }

        @Override
        public ArtifactSet resolveArtifacts(ArtifactSelector artifactSelector, ArtifactTypeRegistry artifactTypeRegistry, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
            return artifactSelector.resolveArtifacts(component, configuration, exclusions, overriddenAttributes);
        }
    }
}
