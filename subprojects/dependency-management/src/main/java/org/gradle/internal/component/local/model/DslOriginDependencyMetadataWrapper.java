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

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;

import java.util.Collection;
import java.util.List;

public class DslOriginDependencyMetadataWrapper implements DslOriginDependencyMetadata, LocalOriginDependencyMetadata {
    private final LocalOriginDependencyMetadata delegate;
    private final Dependency source;
    private final boolean isTransitive;
    private List<IvyArtifactName> artifacts;

    public DslOriginDependencyMetadataWrapper(LocalOriginDependencyMetadata delegate, Dependency source) {
        this.delegate = delegate;
        this.source = source;
        this.isTransitive = delegate.isTransitive();
        this.artifacts = delegate.getArtifacts();
    }

    private DslOriginDependencyMetadataWrapper(LocalOriginDependencyMetadata delegate, Dependency source, List<IvyArtifactName> artifacts) {
        this.delegate = delegate;
        this.source = source;
        this.isTransitive = delegate.isTransitive();
        this.artifacts = artifacts;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public Dependency getSource() {
        return source;
    }

    @Override
    public String getModuleConfiguration() {
        return delegate.getModuleConfiguration();
    }

    @Override
    public List<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema, Collection<? extends Capability> explicitRequestedCapabilities) {
        return delegate.selectConfigurations(consumerAttributes, targetComponent, consumerSchema, explicitRequestedCapabilities);
    }

    @Override
    public String getDependencyConfiguration() {
        return delegate.getDependencyConfiguration();
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        return delegate.getExcludes();
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
    public boolean isForce() {
        return delegate.isForce();
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
    public boolean isFromLock() {
        return delegate.isFromLock();
    }

    @Override
    public String getReason() {
        return delegate.getReason();
    }

    @Override
    public List<IvyArtifactName> getArtifacts() {
        return artifacts;
    }

    @Override
    public LocalOriginDependencyMetadata withTarget(ComponentSelector target) {
        return new DslOriginDependencyMetadataWrapper(delegate.withTarget(target), source);
    }

    @Override
    public LocalOriginDependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts) {
        return new DslOriginDependencyMetadataWrapper(delegate.withTarget(target), source, artifacts);
    }

    @Override
    public DependencyMetadata withReason(String reason) {
        return delegate.withReason(reason);
    }

    @Override
    public ComponentSelector getSelector() {
        return delegate.getSelector();
    }

    @Override
    public LocalOriginDependencyMetadata forced() {
        return new DslOriginDependencyMetadataWrapper(delegate.forced(), source);
    }
}
