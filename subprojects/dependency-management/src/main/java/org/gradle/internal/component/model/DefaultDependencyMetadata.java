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
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class DefaultDependencyMetadata extends AbstractDependencyMetadata implements ModuleDependencyMetadata {
    private final Set<IvyArtifactName> artifacts;
    private final List<Artifact> dependencyArtifacts;
    private final ModuleComponentSelector selector;
    private final boolean optional;

    protected DefaultDependencyMetadata(ModuleComponentSelector selector, List<Artifact> artifacts, boolean optional) {
        this.selector = selector;
        dependencyArtifacts = ImmutableList.copyOf(artifacts);
        this.optional = optional;
        this.artifacts = map(dependencyArtifacts);
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
    public Set<ComponentArtifactMetadata> getArtifacts(ConfigurationMetadata fromConfiguration, ConfigurationMetadata toConfiguration) {
        if (dependencyArtifacts.isEmpty()) {
            return Collections.emptySet();
        }

        Collection<String> includedConfigurations = fromConfiguration.getHierarchy();
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

    protected static boolean include(Iterable<String> configurations, Collection<String> acceptedConfigurations) {
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
    public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
        if (requestedVersion.equals(selector.getVersionConstraint())) {
            return this;
        }
        ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(selector.getGroup(), selector.getModule(), requestedVersion);
        return withRequested(newSelector);
    }

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        if (target instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleTarget = (ModuleComponentSelector) target;
            ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(moduleTarget.getGroup(), moduleTarget.getModule(), moduleTarget.getVersionConstraint());
            if (newSelector.equals(selector)) {
                return this;
            }
            return withRequested(newSelector);
        } else if (target instanceof ProjectComponentSelector) {
            ProjectComponentSelector projectTarget = (ProjectComponentSelector) target;
            return new DefaultProjectDependencyMetadata(projectTarget, this);
        } else {
            throw new IllegalArgumentException("Unexpected selector provided: " + target);
        }
    }

    protected abstract ModuleDependencyMetadata withRequested(ModuleComponentSelector newRequested);

    @Override
    public ModuleComponentSelector getSelector() {
        return selector;
    }

    public List<Artifact> getDependencyArtifacts() {
        return dependencyArtifacts;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }
}
