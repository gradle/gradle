/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class DefaultDependencyMetadata implements DependencyMetadata {
    private final ModuleVersionSelector requested;
    private final Set<IvyArtifactName> artifacts;
    private final List<Artifact> dependencyArtifacts;
    private final ModuleComponentSelector selector;

    protected DefaultDependencyMetadata(ModuleVersionSelector requested, List<Artifact> artifacts) {
        this.requested = requested;
        dependencyArtifacts = ImmutableList.copyOf(artifacts);
        this.artifacts = map(dependencyArtifacts);
        selector = DefaultModuleComponentSelector.newSelector(requested.getGroup(), requested.getName(), requested.getVersion());
    }

    private static Set<IvyArtifactName> map(List<Artifact> dependencyArtifacts) {
        if (dependencyArtifacts.isEmpty()) {
            return ImmutableSet.of();
        }
        Set<IvyArtifactName> result = Sets.newLinkedHashSetWithExpectedSize(dependencyArtifacts.size());
        for (Artifact artifact : dependencyArtifacts) {
            result.add(artifact.getArtifactName());
        }
        return result;
    }

    @Override
    public ModuleVersionSelector getRequested() {
        return requested;
    }

    @Override
    public Set<ComponentArtifactMetadata> getArtifacts(ConfigurationMetadata fromConfiguration, ConfigurationMetadata toConfiguration) {
        if (dependencyArtifacts.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> includedConfigurations = fromConfiguration.getHierarchy();
        Set<ComponentArtifactMetadata> artifacts = Sets.newLinkedHashSet();

        for (Artifact depArtifact : dependencyArtifacts) {
            IvyArtifactName ivyArtifactName = depArtifact.getArtifactName();
            Set<String> artifactConfigurations = depArtifact.getConfigurations();
            if (include(artifactConfigurations, includedConfigurations)) {
                ComponentArtifactMetadata artifact = toConfiguration.artifact(ivyArtifactName);
                artifacts.add(artifact);
            }
        }
        return artifacts;
    }

    protected boolean include(Iterable<String> configurations, Collection<String> acceptedConfigurations) {
        for (String configuration : configurations) {
            if (configuration.equals("*")) {
                return true;
            }
            if (acceptedConfigurations.contains(configuration)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<IvyArtifactName> getArtifacts() {
        return artifacts;
    }

    @Override
    public DependencyMetadata withRequestedVersion(String requestedVersion) {
        if (requestedVersion.equals(requested.getVersion())) {
            return this;
        }
        ModuleVersionSelector newRequested = DefaultModuleVersionSelector.newSelector(requested.getGroup(), requested.getName(), requestedVersion);
        return withRequested(newRequested);
    }

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        if (target instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleTarget = (ModuleComponentSelector) target;
            ModuleVersionSelector requestedVersion = DefaultModuleVersionSelector.newSelector(moduleTarget.getGroup(), moduleTarget.getModule(), moduleTarget.getVersion());
            if (requestedVersion.equals(requested)) {
                return this;
            }
            return withRequested(requestedVersion);
        } else if (target instanceof ProjectComponentSelector) {
            ProjectComponentSelector projectTarget = (ProjectComponentSelector) target;
            return new DefaultProjectDependencyMetadata(projectTarget, this);
        } else {
            throw new IllegalArgumentException("Unexpected selector provided: " + target);
        }
    }

    protected abstract DependencyMetadata withRequested(ModuleVersionSelector newRequested);

    @Override
    public ComponentSelector getSelector() {
        return selector;
    }

    public List<Artifact> getDependencyArtifacts() {
        return dependencyArtifacts;
    }
}
