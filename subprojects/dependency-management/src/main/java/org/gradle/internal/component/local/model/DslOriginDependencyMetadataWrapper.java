/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.component.local.model;

import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DslOriginDependencyMetadataWrapper implements DslOriginDependencyMetadata {
    private final DependencyMetadata delegate;
    private final ModuleDependency source;

    public DslOriginDependencyMetadataWrapper(DependencyMetadata delegate, ModuleDependency source) {
        this.delegate = delegate;
        this.source = source;
    }

    public ModuleDependency getSource() {
        return source;
    }

    public ModuleVersionSelector getRequested() {
        return delegate.getRequested();
    }

    @Override
    public String[] getModuleConfigurations() {
        return delegate.getModuleConfigurations();
    }

    @Override
    public String[] getDependencyConfigurations(String moduleConfiguration, String requestedConfiguration) {
        return delegate.getDependencyConfigurations(moduleConfiguration, requestedConfiguration);
    }

    public List<Exclude> getExcludes(Collection<String> configurations) {
        return delegate.getExcludes(configurations);
    }

    public String getDynamicConstraintVersion() {
        return delegate.getDynamicConstraintVersion();
    }

    public boolean isChanging() {
        return delegate.isChanging();
    }

    public boolean isTransitive() {
        return delegate.isTransitive();
    }

    public boolean isForce() {
        return delegate.isForce();
    }

    public Set<ComponentArtifactMetadata> getArtifacts(ConfigurationMetadata fromConfiguration, ConfigurationMetadata toConfiguration) {
        return delegate.getArtifacts(fromConfiguration, toConfiguration);
    }

    public Set<IvyArtifactName> getArtifacts() {
        return delegate.getArtifacts();
    }

    public DependencyMetadata withRequestedVersion(String requestedVersion) {
        return delegate.withRequestedVersion(requestedVersion);
    }

    public DependencyMetadata withTarget(ComponentSelector target) {
        return delegate.withTarget(target);
    }

    public DependencyMetadata withChanging() {
        return delegate.withChanging();
    }

    public ComponentSelector getSelector() {
        return delegate.getSelector();
    }
}
