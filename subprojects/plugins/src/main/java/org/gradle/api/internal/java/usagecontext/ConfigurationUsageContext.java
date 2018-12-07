/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.java.usagecontext;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.model.ObjectFactory;

import java.util.Set;

public class ConfigurationUsageContext extends AbstractUsageContext {
    private final String name;
    private final String configurationName;
    private final ConfigurationContainer configurations;
    private DomainObjectSet<ModuleDependency> dependencies;
    private DomainObjectSet<DependencyConstraint> dependencyConstraints;
    private Set<? extends Capability> capabilities;
    private Set<ExcludeRule> excludeRules;


    public ConfigurationUsageContext(String usageName,
                                     String name,
                                     String configurationName,
                                     Set<PublishArtifact> artifacts,
                                     ConfigurationContainer configurations,
                                     ObjectFactory objectFactory,
                                     ImmutableAttributesFactory attributesFactory) {
        super(usageName, artifacts, objectFactory, attributesFactory);
        this.name = name;
        this.configurationName = configurationName;
        this.configurations = configurations;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<ModuleDependency> getDependencies() {
        if (dependencies == null) {
            dependencies = getConfiguration().getIncoming().getDependencies().withType(ModuleDependency.class);
        }
        return dependencies;
    }

    @Override
    public Set<? extends DependencyConstraint> getDependencyConstraints() {
        if (dependencyConstraints == null) {
            dependencyConstraints = getConfiguration().getIncoming().getDependencyConstraints();
        }
        return dependencyConstraints;
    }

    @Override
    public Set<? extends Capability> getCapabilities() {
        if (capabilities == null) {
            this.capabilities = ImmutableSet.copyOf(Configurations.collectCapabilities(getConfiguration(),
                Sets.<Capability>newHashSet(),
                Sets.<Configuration>newHashSet()));
        }
        return capabilities;
    }

    @Override
    public Set<ExcludeRule> getGlobalExcludes() {
        if (excludeRules == null) {
            this.excludeRules = ImmutableSet.copyOf(((ConfigurationInternal) getConfiguration()).getAllExcludeRules());
        }
        return excludeRules;
    }

    private Configuration getConfiguration() {
        return configurations.getByName(configurationName);
    }
}
