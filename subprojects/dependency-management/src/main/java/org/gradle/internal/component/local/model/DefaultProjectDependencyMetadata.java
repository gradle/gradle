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

package org.gradle.internal.component.local.model;

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ForcingDependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DefaultProjectDependencyMetadata implements ForcingDependencyMetadata {
    private final ProjectComponentSelector selector;
    private final DependencyMetadata delegate;
    private final boolean isTransitive;

    public DefaultProjectDependencyMetadata(ProjectComponentSelector selector, DependencyMetadata delegate) {
        this.selector = selector;
        this.delegate = delegate;
        this.isTransitive = delegate.isTransitive();
    }

    @Override
    public ProjectComponentSelector getSelector() {
        return selector;
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        return Collections.emptyList();
    }

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        if (target.equals(selector)) {
            return this;
        }
        return delegate.withTarget(target);
    }

    @Override
    public DependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts) {
        if (target.equals(selector) && delegate.getArtifacts().equals(artifacts)) {
            return this;
        }
        return delegate.withTargetAndArtifacts(target, artifacts);
    }

    @Override
    public boolean isChanging() {
        return delegate.isChanging();
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

    @Override
    public boolean isTransitive() {
        return isTransitive;
    }

    @Override
    public List<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema, Collection<? extends Capability> explicitRequestedCapabilities) {
        return delegate.selectConfigurations(consumerAttributes, targetComponent, consumerSchema, explicitRequestedCapabilities);
    }

    @Override
    public List<IvyArtifactName> getArtifacts() {
        return delegate.getArtifacts();
    }

    @Override
    public DependencyMetadata withReason(String reason) {
        return delegate.withReason(reason);
    }

    @Override
    public boolean isForce() {
        if (delegate instanceof ForcingDependencyMetadata) {
            return ((ForcingDependencyMetadata) delegate).isForce();
        }
        return false;
    }

    @Override
    public ForcingDependencyMetadata forced() {
        if (delegate instanceof ForcingDependencyMetadata) {
            return ((ForcingDependencyMetadata) delegate).forced();
        }
        return this;
    }
}
