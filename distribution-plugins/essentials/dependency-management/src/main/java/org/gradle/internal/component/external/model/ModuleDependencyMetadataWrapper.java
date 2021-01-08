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
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collection;
import java.util.List;

public class ModuleDependencyMetadataWrapper implements ModuleDependencyMetadata {
    private final DependencyMetadata delegate;
    private final boolean isTransitive;

    public ModuleDependencyMetadataWrapper(DependencyMetadata delegate) {
        this.delegate = delegate;
        this.isTransitive = delegate.isTransitive();
    }

    @Override
    public List<IvyArtifactName> getArtifacts() {
        return delegate.getArtifacts();
    }

    @Override
    public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
        ModuleComponentSelector selector = getSelector();
        ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(selector.getModuleIdentifier(), requestedVersion, selector.getAttributes(), selector.getRequestedCapabilities());
        return new ModuleDependencyMetadataWrapper(delegate.withTarget(newSelector));
    }

    @Override
    public ModuleDependencyMetadata withReason(String reason) {
        return new ModuleDependencyMetadataWrapper(delegate.withReason(reason));
    }

    @Override
    public ModuleDependencyMetadata withEndorseStrictVersions(boolean endorse) {
        if (delegate instanceof ModuleDependencyMetadata) {
            return new ModuleDependencyMetadataWrapper(((ModuleDependencyMetadata) delegate).withEndorseStrictVersions(endorse));
        }
        return this;
    }

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        return delegate.withTarget(target);
    }

    @Override
    public DependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts) {
        return delegate.withTargetAndArtifacts(target, artifacts);
    }

    @Override
    public ModuleComponentSelector getSelector() {
        return (ModuleComponentSelector) delegate.getSelector();
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        return delegate.getExcludes();
    }

    @Override
    public List<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema, Collection<? extends Capability> explicitRequestedCapabilities) {
        return delegate.selectConfigurations(consumerAttributes, targetComponent, consumerSchema, explicitRequestedCapabilities);
    }

    @Override
    public boolean isChanging() {
        return delegate.isChanging();
    }

    @Override
    public boolean isTransitive() {
        return isTransitive;
    }

    @Override
    public boolean isConstraint() {
        return delegate.isConstraint();
    }

    @Override
    public boolean isEndorsingStrictVersions() {
        return delegate.isEndorsingStrictVersions();
    }

    @Override
    public String getReason() {
        return delegate.getReason();
    }
}
