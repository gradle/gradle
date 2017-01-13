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

import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;

import java.util.List;
import java.util.Set;

public class DslOriginDependencyMetadataWrapper implements DslOriginDependencyMetadata {
    private final LocalOriginDependencyMetadata delegate;
    private final ModuleDependency source;

    public DslOriginDependencyMetadataWrapper(LocalOriginDependencyMetadata delegate, ModuleDependency source) {
        this.delegate = delegate;
        this.source = source;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public ModuleDependency getSource() {
        return source;
    }

    @Override
    public ModuleVersionSelector getRequested() {
        return delegate.getRequested();
    }

    @Override
    public String getModuleConfiguration() {
        return delegate.getModuleConfiguration();
    }

    @Override
    public Set<ConfigurationMetadata> selectConfigurations(ComponentResolveMetadata fromComponent, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent, AttributesSchema attributesSchema) {
        return delegate.selectConfigurations(fromComponent, fromConfiguration, targetComponent, attributesSchema);
    }

    @Override
    public Set<String> getModuleConfigurations() {
        return delegate.getModuleConfigurations();
    }

    @Override
    public String getDependencyConfiguration() {
        return delegate.getDependencyConfiguration();
    }

    @Override
    public ModuleExclusion getExclusions(ConfigurationMetadata fromConfiguration) {
        return delegate.getExclusions(fromConfiguration);
    }

    @Override
    public List<Exclude> getExcludes() {
        return delegate.getExcludes();
    }

    @Override
    public String getDynamicConstraintVersion() {
        return delegate.getDynamicConstraintVersion();
    }

    @Override
    public boolean isChanging() {
        return delegate.isChanging();
    }

    @Override
    public boolean isTransitive() {
        return delegate.isTransitive();
    }

    @Override
    public boolean isForce() {
        return delegate.isForce();
    }

    @Override
    public Set<ComponentArtifactMetadata> getArtifacts(ConfigurationMetadata fromConfiguration, ConfigurationMetadata toConfiguration) {
        return delegate.getArtifacts(fromConfiguration, toConfiguration);
    }

    @Override
    public Set<IvyArtifactName> getArtifacts() {
        return delegate.getArtifacts();
    }

    @Override
    public LocalOriginDependencyMetadata withRequestedVersion(String requestedVersion) {
        return delegate.withRequestedVersion(requestedVersion);
    }

    @Override
    public LocalOriginDependencyMetadata withTarget(ComponentSelector target) {
        return delegate.withTarget(target);
    }

    @Override
    public ComponentSelector getSelector() {
        return delegate.getSelector();
    }
}
