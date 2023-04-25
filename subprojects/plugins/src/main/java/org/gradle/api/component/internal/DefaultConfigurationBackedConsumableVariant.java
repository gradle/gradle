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

package org.gradle.api.component.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.component.external.model.ImmutableCapabilities;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;

/**
 * Default implementation of {@link ConfigurationBackedConsumableVariant}.
 */
public abstract class DefaultConfigurationBackedConsumableVariant implements ConfigurationBackedConsumableVariant {

    private final Configuration configuration;

    @Inject
    public DefaultConfigurationBackedConsumableVariant(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void configuration(Action<? super Configuration> action) {
        action.execute(configuration);
    }

    @Override
    public String getName() {
        return configuration.getName();
    }

    @Override
    public AttributeContainer getAttributes() {
        return ((AttributeContainerInternal) configuration.getAttributes()).asImmutable();
    }

    @Override
    public FileCollection getArtifacts() {
        return configuration.getArtifacts().getFiles();
    }

    @Override
    public Set<? extends Dependency> getDependencies() {
        return Collections.unmodifiableSet(configuration.getAllDependencies());
    }

    @Override
    public Set<? extends DependencyConstraint> getDependencyConstraints() {
        return Collections.unmodifiableSet(configuration.getAllDependencyConstraints());
    }

    @Override
    public CapabilitiesMetadata getCapabilities() {
        return ImmutableCapabilities.of(configuration.getOutgoing().getCapabilities());
    }
}
