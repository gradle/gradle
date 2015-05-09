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

import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ConfigurationMetaData;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collection;
import java.util.Set;

public class DslOriginDependencyMetaDataWrapper implements DslOriginDependencyMetaData {
    private final DependencyMetaData delegate;
    private final ModuleDependency source;

    public DslOriginDependencyMetaDataWrapper(DependencyMetaData delegate, ModuleDependency source) {
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

    public ExcludeRule[] getExcludeRules(Collection<String> configurations) {
        return delegate.getExcludeRules(configurations);
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

    public Set<ComponentArtifactMetaData> getArtifacts(ConfigurationMetaData fromConfiguration, ConfigurationMetaData toConfiguration) {
        return delegate.getArtifacts(fromConfiguration, toConfiguration);
    }

    public Set<IvyArtifactName> getArtifacts() {
        return delegate.getArtifacts();
    }

    public DependencyMetaData withRequestedVersion(String requestedVersion) {
        return delegate.withRequestedVersion(requestedVersion);
    }

    public DependencyMetaData withTarget(ComponentSelector target) {
        return delegate.withTarget(target);
    }

    public DependencyMetaData withChanging() {
        return delegate.withChanging();
    }

    public ComponentSelector getSelector() {
        return delegate.getSelector();
    }
}
