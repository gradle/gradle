/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * A {@link DependencyMetadata} implementation which delegates all method calls to a provided {@code delegate}.
 */
public abstract class DelegatingDependencyMetadata implements DependencyMetadata {

    private final DependencyMetadata delegate;

    public DelegatingDependencyMetadata(DependencyMetadata delegate) {
        this.delegate = delegate;
    }

    @Override
    public ComponentSelector getSelector() {
        return delegate.getSelector();
    }

    @Override
    public VariantSelectionResult selectVariants(AttributeMatchingConfigurationSelector configurationSelector, ImmutableAttributes consumerAttributes, ComponentGraphResolveState targetComponentState, AttributesSchemaInternal consumerSchema, Collection<? extends Capability> explicitRequestedCapabilities) {
        return delegate.selectVariants(configurationSelector, consumerAttributes, targetComponentState, consumerSchema, explicitRequestedCapabilities);
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        return delegate.getExcludes();
    }

    @Override
    public List<IvyArtifactName> getArtifacts() {
        return delegate.getArtifacts();
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
    public boolean isChanging() {
        return delegate.isChanging();
    }

    @Override
    public boolean isTransitive() {
        return delegate.isTransitive();
    }

    @Override
    public boolean isConstraint() {
        return delegate.isConstraint();
    }

    @Override
    public boolean isEndorsingStrictVersions() {
        return delegate.isEndorsingStrictVersions();
    }

    @Nullable
    @Override
    public String getReason() {
        return delegate.getReason();
    }

    @Override
    public DependencyMetadata withReason(String reason) {
        return delegate.withReason(reason);
    }

}
