/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.publish.internal.component;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;

import javax.annotation.Nullable;

/**
 * A {@link ConfigurationSoftwareComponentVariant} which is aware of both Maven and Ivy publishing, and can optionally
 * be backed by resolution during publication.
 */
public class FeatureConfigurationVariant extends ConfigurationSoftwareComponentVariant implements MavenPublishingAwareVariant, IvyPublishingAwareVariant, ResolutionBackedVariant {
    private final ScopeMapping scopeMapping;
    private final boolean optional;
    DependencyMappingDetailsInternal dependencyMapping;

    public FeatureConfigurationVariant(
        String name,
        Configuration configuration,
        ConfigurationVariant variant,
        String mavenScope,
        boolean optional,
        @Nullable DependencyMappingDetailsInternal dependencyMapping
    ) {
        super(name, ((AttributeContainerInternal)variant.getAttributes()).asImmutable(), variant.getArtifacts(), configuration);
        this.scopeMapping = ScopeMapping.of(mavenScope, optional);
        this.optional = optional;
        this.dependencyMapping = dependencyMapping;
    }

    @Override
    public ScopeMapping getScopeMapping() {
        return scopeMapping;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    @Override
    public boolean getPublishResolvedCoordinates() {
        return dependencyMapping != null && dependencyMapping.getPublishResolvedCoordinates().getOrElse(false);
    }

    @Nullable
    public Configuration getResolutionConfiguration() {
        return dependencyMapping != null ? dependencyMapping.getResolutionConfiguration() : null;
    }
}
