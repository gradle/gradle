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

package org.gradle.internal.component.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class GradleDependencyMetadata extends AbstractDependencyMetadata implements ModuleDependencyMetadata {
    private final ModuleComponentSelector selector;

    public GradleDependencyMetadata(ModuleComponentSelector selector) {
        this.selector = selector;
    }

    @Override
    public Set<ComponentArtifactMetadata> getArtifacts(ConfigurationMetadata fromConfiguration, ConfigurationMetadata toConfiguration) {
        return ImmutableSet.of();
    }

    @Override
    public Set<IvyArtifactName> getArtifacts() {
        return ImmutableSet.of();
    }

    @Override
    public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
        if (requestedVersion.equals(selector.getVersionConstraint())) {
            return this;
        }
        return new GradleDependencyMetadata(DefaultModuleComponentSelector.newSelector(selector.getGroup(), selector.getModule(), requestedVersion));
    }

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        if (target instanceof ModuleComponentSelector) {
            return new GradleDependencyMetadata((ModuleComponentSelector) target);
        }
        return new DefaultProjectDependencyMetadata((ProjectComponentSelector) target, this);
    }

    @Override
    public ModuleComponentSelector getSelector() {
        return selector;
    }

    @Override
    public List<Exclude> getExcludes() {
        return ImmutableList.of();
    }

    @Override
    public List<Exclude> getExcludes(Collection<String> configurations) {
        return ImmutableList.of();
    }

    @Override
    public Set<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata fromComponent, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema) {
        return ImmutableSet.of(selectConfigurationUsingAttributeMatching(consumerAttributes, targetComponent, consumerSchema));
    }

    @Override
    public Set<String> getModuleConfigurations() {
        return ImmutableSet.of();
    }

    @Override
    public boolean isChanging() {
        return false;
    }

    @Override
    public boolean isTransitive() {
        return true;
    }

    @Override
    public boolean isForce() {
        return false;
    }

    @Override
    public boolean isOptional() {
        return false;
    }

    @Override
    public String getDynamicConstraintVersion() {
        return getSelector().getVersionConstraint().getPreferredVersion();
    }

}
