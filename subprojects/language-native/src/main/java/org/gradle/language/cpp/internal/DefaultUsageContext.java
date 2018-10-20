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

package org.gradle.language.cpp.internal;

import org.gradle.api.Named;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.component.UsageContext;

import java.util.Collections;
import java.util.Set;

public class DefaultUsageContext implements UsageContext, Named {
    private final String name;
    private final Usage usage;
    private final AttributeContainer attributes;

    private final Set<? extends PublishArtifact> artifacts;
    private final Set<? extends ModuleDependency> dependencies;
    private final Set<? extends DependencyConstraint> dependencyConstraints;
    private final Set<ExcludeRule> globalExcludes;

    DefaultUsageContext(String name, Usage usage, Set<? extends PublishArtifact> artifacts, Configuration configuration) {
        this(name, usage, configuration.getAttributes(), artifacts, configuration);
    }

    public DefaultUsageContext(UsageContext usageContext, Set<? extends PublishArtifact> artifacts, Configuration configuration) {
        this(usageContext.getName(), usageContext.getUsage(), usageContext.getAttributes(), artifacts, configuration);
    }

    public DefaultUsageContext(String name, Usage usage, AttributeContainer attributes) {
        this(name, usage, attributes, null, null);
    }

    private DefaultUsageContext(String name, Usage usage, AttributeContainer attributes, Set<? extends PublishArtifact> artifacts, Configuration configuration) {
        this.name = name;
        this.usage = usage;
        this.attributes = attributes;
        this.artifacts = artifacts;
        if (configuration != null) {
            this.dependencies = configuration.getAllDependencies().withType(ModuleDependency.class);
            this.dependencyConstraints = configuration.getAllDependencyConstraints();
            this.globalExcludes = ((ConfigurationInternal) configuration).getAllExcludeRules();
        } else {
            this.dependencies = null;
            this.dependencyConstraints = null;
            this.globalExcludes = Collections.emptySet();
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Usage getUsage() {
        return usage;
    }

    @Override
    public AttributeContainer getAttributes() {
        return attributes;
    }

    @Override
    public Set<? extends PublishArtifact> getArtifacts() {
        assert artifacts != null;
        return artifacts;
    }

    @Override
    public Set<? extends ModuleDependency> getDependencies() {
        assert dependencies != null;
        return dependencies;
    }

    @Override
    public Set<? extends DependencyConstraint> getDependencyConstraints() {
        assert dependencyConstraints != null;
        return dependencyConstraints;
    }

    @Override
    public Set<? extends Capability> getCapabilities() {
        return Collections.emptySet();
    }

    @Override
    public Set<ExcludeRule> getGlobalExcludes() {
        return globalExcludes;
    }
}
