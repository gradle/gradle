/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.plugins.internal;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.component.AbstractSoftwareComponentVariant;

import java.util.Set;

/**
 * A {@link SoftwareComponentVariant} based on a consumable {@link Configuration}.
 */
public class ConfigurationSoftwareComponentVariant extends AbstractSoftwareComponentVariant {
    protected final String name;
    private final Configuration configuration;
    private DomainObjectSet<ModuleDependency> dependencies;
    private DomainObjectSet<DependencyConstraint> dependencyConstraints;
    private Set<? extends Capability> capabilities;
    private Set<ExcludeRule> excludeRules;

    public ConfigurationSoftwareComponentVariant(SoftwareComponentVariant base, Set<? extends PublishArtifact> artifacts, Configuration configuration) {
        this(base.getName(), base.getAttributes(), artifacts, configuration);
    }

    public ConfigurationSoftwareComponentVariant(String name, AttributeContainer attributes, Set<? extends PublishArtifact> artifacts, Configuration configuration) {
        super(((AttributeContainerInternal) attributes).asImmutable(), artifacts);
        this.configuration = configuration;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<ModuleDependency> getDependencies() {
        if (dependencies == null) {
            dependencies = configuration.getIncoming().getDependencies().withType(ModuleDependency.class);
        }
        return dependencies;
    }

    @Override
    public Set<? extends DependencyConstraint> getDependencyConstraints() {
        if (dependencyConstraints == null) {
            dependencyConstraints = configuration.getIncoming().getDependencyConstraints();
        }
        return dependencyConstraints;
    }

    @Override
    public Set<? extends Capability> getCapabilities() {
        if (capabilities == null) {
            this.capabilities = ImmutableSet.copyOf(Configurations.collectCapabilities(configuration,
                Sets.newHashSet(),
                Sets.newHashSet()));
        }
        return capabilities;
    }

    @Override
    public Set<ExcludeRule> getGlobalExcludes() {
        if (excludeRules == null) {
            this.excludeRules = ImmutableSet.copyOf(((ConfigurationInternal) configuration).getAllExcludeRules());
        }
        return excludeRules;
    }
}
