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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata;
import org.gradle.internal.component.model.AttributeConfigurationSelector;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collections;
import java.util.List;

public class GradleDependencyMetadata implements ModuleDependencyMetadata {
    private final ModuleComponentSelector selector;
    private final List<ExcludeMetadata> excludes;
    private final boolean pending;

    public GradleDependencyMetadata(ModuleComponentSelector selector, boolean pending) {
        this.selector = selector;
        this.excludes = Collections.emptyList();
        this.pending = pending;
    }

    public GradleDependencyMetadata(ModuleComponentSelector selector, List<ExcludeMetadata> excludes) {
        this.selector = selector;
        this.excludes = excludes;
        this.pending = false;
    }

    @Override
    public List<IvyArtifactName> getArtifacts() {
        return ImmutableList.of();
    }

    @Override
    public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
        if (requestedVersion.equals(selector.getVersionConstraint())) {
            return this;
        }
        return new GradleDependencyMetadata(DefaultModuleComponentSelector.newSelector(selector.getGroup(), selector.getModule(), requestedVersion), pending);
    }

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        if (target instanceof ModuleComponentSelector) {
            return new GradleDependencyMetadata((ModuleComponentSelector) target, pending);
        }
        return new DefaultProjectDependencyMetadata((ProjectComponentSelector) target, this);
    }

    @Override
    public ModuleComponentSelector getSelector() {
        return selector;
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        return excludes;
    }

    @Override
    public List<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema) {
        return ImmutableList.of(AttributeConfigurationSelector.selectConfigurationUsingAttributeMatching(consumerAttributes, targetComponent, consumerSchema));
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
    public boolean isPending() {
        return pending;
    }

}
