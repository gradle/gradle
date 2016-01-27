/*
 * Copyright 2015 the original author or authors.
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

import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class LocalComponentDependencyMetaData implements DependencyMetaData {
    private final ComponentSelector selector;
    private final ModuleVersionSelector requested;
    private final String moduleConfiguration;
    private final String dependencyConfiguration;
    private final ExcludeRule[] excludeRules;
    private final Set<IvyArtifactName> artifactNames;
    private final boolean force;
    private final boolean changing;
    private final boolean transitive;

    public LocalComponentDependencyMetaData(ComponentSelector selector, ModuleVersionSelector requested, String moduleConfiguration, String dependencyConfiguration,
                                            Set<IvyArtifactName> artifactNames, ExcludeRule[] excludeRules,
                                            boolean force, boolean changing, boolean transitive) {
        this.selector = selector;
        this.requested = requested;
        this.moduleConfiguration = moduleConfiguration;
        this.dependencyConfiguration = dependencyConfiguration;
        this.artifactNames = artifactNames;
        this.excludeRules = excludeRules;
        this.force = force;
        this.changing = changing;
        this.transitive = transitive;
    }

    @Override
    public String toString() {
        return "dependency: " + requested + " " + moduleConfiguration;
    }

    public ModuleVersionSelector getRequested() {
        return requested;
    }

    public ComponentSelector getSelector() {
        return selector;
    }

    public String[] getModuleConfigurations() {
        return new String[] {
            moduleConfiguration
        };
    }

    public String[] getDependencyConfigurations(String moduleConfiguration, String requestedConfiguration) {
        if (this.moduleConfiguration.equals(moduleConfiguration)) {
            return new String[] {
                dependencyConfiguration
            };
        }
        return new String[0];
    }

    public ExcludeRule[] getExcludeRules(Collection<String> configurations) {
        if (configurations.contains(moduleConfiguration)) {
            return excludeRules;
        }
        return new ExcludeRule[0];
    }

    public boolean isChanging() {
        return changing;
    }

    public boolean isTransitive() {
        return transitive;
    }

    public boolean isForce() {
        return force;
    }

    public String getDynamicConstraintVersion() {
        return requested.getVersion();
    }

    public Set<ComponentArtifactMetaData> getArtifacts(ConfigurationMetaData fromConfiguration, ConfigurationMetaData toConfiguration) {
        if (artifactNames.isEmpty()) {
            return Collections.emptySet();
        }
        Set<ComponentArtifactMetaData> artifacts = new LinkedHashSet<ComponentArtifactMetaData>();
        for (IvyArtifactName artifactName : artifactNames) {
            artifacts.add(toConfiguration.artifact(artifactName));
        }
        return artifacts;
    }

    public Set<IvyArtifactName> getArtifacts() {
        return artifactNames;
    }

    public DependencyMetaData withRequestedVersion(String requestedVersion) {
        if (requestedVersion.equals(requested.getVersion())) {
            return this;
        }
        ModuleVersionSelector newRequested = DefaultModuleVersionSelector.newSelector(requested.getGroup(), requested.getName(), requestedVersion);
        ComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(newRequested);
        return copyWithTarget(newSelector, newRequested);
    }

    @Override
    public DependencyMetaData withTarget(ComponentSelector target) {
        if (target instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleTarget = (ModuleComponentSelector) target;
            ModuleVersionSelector requestedVersion = DefaultModuleVersionSelector.newSelector(moduleTarget.getGroup(), moduleTarget.getModule(), moduleTarget.getVersion());
            return copyWithTarget(moduleTarget, requestedVersion);
        } else if (target instanceof ProjectComponentSelector) {
            return copyWithTarget(target, requested);
        } else {
            throw new AssertionError("Invalid component selector type for substitution: " + target);
        }
    }

    private DependencyMetaData copyWithTarget(ComponentSelector selector, ModuleVersionSelector requested) {
        return new LocalComponentDependencyMetaData(selector, requested, moduleConfiguration, dependencyConfiguration, artifactNames, excludeRules, force, changing, transitive);
    }

    public DependencyMetaData withChanging() {
        if (isChanging()) {
            return this;
        }
        return new LocalComponentDependencyMetaData(selector, requested, moduleConfiguration, dependencyConfiguration, artifactNames, excludeRules, force, true, transitive);
    }
}
