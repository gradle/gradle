/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.component;

import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.SoftwareComponentVariant;

import java.util.Set;

/**
 * A {@link SoftwareComponentVariant} which delegates all methods to a provided delegate instance.
 */
public abstract class DelegatingSoftwareComponentVariant implements SoftwareComponentVariant {

    private final SoftwareComponentVariant delegate;

    public DelegatingSoftwareComponentVariant(SoftwareComponentVariant delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public AttributeContainer getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Set<? extends PublishArtifact> getArtifacts() {
        return delegate.getArtifacts();
    }

    @Override
    public Set<? extends ModuleDependency> getDependencies() {
        return delegate.getDependencies();
    }

    @Override
    public Set<? extends DependencyConstraint> getDependencyConstraints() {
        return delegate.getDependencyConstraints();
    }

    @Override
    public Set<? extends Capability> getCapabilities() {
        return delegate.getCapabilities();
    }

    @Override
    public Set<ExcludeRule> getGlobalExcludes() {
        return delegate.getGlobalExcludes();
    }
}
