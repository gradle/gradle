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

package org.gradle.api.internal.component;

import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.AttributeContainerInternal;

import java.util.Collections;
import java.util.Set;

/**
 * Default implementation of {@link org.gradle.api.component.SoftwareComponentVariant}.
 */
public class DefaultSoftwareComponentVariant extends AbstractSoftwareComponentVariant {
    private final String name;
    private final Set<? extends ModuleDependency> dependencies;
    private final Set<? extends DependencyConstraint> dependencyConstraints;
    private final Set<? extends Capability> capabilities;
    private final Set<ExcludeRule> globalExcludes;

    public DefaultSoftwareComponentVariant(String name, AttributeContainer attributes) {
        this(name, attributes, Collections.emptySet());
    }

    public DefaultSoftwareComponentVariant(String name, AttributeContainer attributes, Set<? extends PublishArtifact> artifacts) {
        this(name, attributes, artifacts, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    }

    public DefaultSoftwareComponentVariant(
        String name,
        AttributeContainer attributes,
        Set<? extends PublishArtifact> artifacts,
        Set<? extends ModuleDependency> dependencies,
        Set<? extends DependencyConstraint> dependencyConstraints,
        Set<? extends Capability> capabilities,
        Set<ExcludeRule> globalExcludes
    ) {
        super(((AttributeContainerInternal)attributes).asImmutable(), artifacts);

        assert dependencies != null;
        assert dependencyConstraints != null;

        this.name = name;
        this.dependencies = dependencies;
        this.dependencyConstraints = dependencyConstraints;
        this.capabilities = capabilities;
        this.globalExcludes = globalExcludes;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<? extends ModuleDependency> getDependencies() {
        return dependencies;
    }

    @Override
    public Set<? extends DependencyConstraint> getDependencyConstraints() {
        return dependencyConstraints;
    }

    @Override
    public Set<? extends Capability> getCapabilities() {
        return capabilities;
    }

    @Override
    public Set<ExcludeRule> getGlobalExcludes() {
        return globalExcludes;
    }
}
