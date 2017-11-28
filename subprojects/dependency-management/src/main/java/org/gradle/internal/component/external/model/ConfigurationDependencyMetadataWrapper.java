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
package org.gradle.internal.component.external.model;

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.List;

public class ConfigurationDependencyMetadataWrapper implements ModuleDependencyMetadata {
    private final ConfigurationMetadata configuration;
    private final ModuleComponentIdentifier componentId;
    private final ExternalDependencyDescriptor delegate;

    public ConfigurationDependencyMetadataWrapper(ConfigurationMetadata configuration, ModuleComponentIdentifier componentId, ExternalDependencyDescriptor delegate) {
        this.configuration = configuration;
        this.componentId = componentId;
        this.delegate = delegate;
    }

    @Override
    public List<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema) {
        return delegate.getMetadataForConfigurations(consumerAttributes, consumerSchema, componentId, configuration, targetComponent);
    }

    @Override
    public List<IvyArtifactName> getArtifacts() {
        return delegate.getConfigurationArtifacts(configuration);
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        return delegate.getConfigurationExcludes(configuration.getHierarchy());
    }

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        if (target instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleTarget = (ModuleComponentSelector) target;
            ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(moduleTarget.getGroup(), moduleTarget.getModule(), moduleTarget.getVersionConstraint());
            if (newSelector.equals(getSelector())) {
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

    @Override
    public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
        ModuleComponentSelector selector = getSelector();
        if (requestedVersion.equals(selector.getVersionConstraint())) {
            return this;
        }
        ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(selector.getGroup(), selector.getModule(), requestedVersion);
        return withRequested(newSelector);
    }

    private ModuleDependencyMetadata withRequested(ModuleComponentSelector newSelector) {
        ExternalDependencyDescriptor newDelegate = delegate.withRequested(newSelector);
        return new ConfigurationDependencyMetadataWrapper(configuration, componentId, newDelegate);
    }

    @Override
    public ModuleComponentSelector getSelector() {
        return delegate.getSelector();
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
    public boolean isPending() {
        return delegate.isOptional();
    }
}
