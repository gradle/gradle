/*
 * Copyright 2018 the original author or authors.
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
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.ExcludeMetadata;

import java.util.List;

public class RealisedConfigurationMetadata extends AbstractConfigurationMetadata {

    private final boolean addedByRule;

    public RealisedConfigurationMetadata(
            ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible,
            ImmutableSet<String> hierarchy, ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts,
            ImmutableList<ExcludeMetadata> excludes,
            ImmutableAttributes componentLevelAttributes,
            ImmutableCapabilities capabilities,
            boolean addedByRule,
            boolean externalVariant
    ) {
        this(componentId, name, transitive, visible, hierarchy, artifacts, excludes, componentLevelAttributes, capabilities, null, addedByRule, externalVariant);
    }

    public RealisedConfigurationMetadata(
            ModuleComponentIdentifier componentId,
            String name,
            boolean transitive,
            boolean visible,
            ImmutableSet<String> hierarchy,
            ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts,
            ImmutableList<ExcludeMetadata> excludes,
            ImmutableAttributes attributes,
            ImmutableCapabilities capabilities,
            ImmutableList<ModuleDependencyMetadata> configDependencies,
            boolean addedByRule,
            boolean externalVariant
    ) {
        super(componentId, name, transitive, visible, artifacts, hierarchy, excludes, attributes, configDependencies, capabilities, externalVariant);
        this.addedByRule = addedByRule;
    }

    @Override
    public List<? extends ModuleDependencyMetadata> getDependencies() {
        return getConfigDependencies();
    }

    public RealisedConfigurationMetadata withDependencies(ImmutableList<ModuleDependencyMetadata> dependencies) {
        return new RealisedConfigurationMetadata(
            getComponentId(),
            getName(),
            isTransitive(),
            isVisible(),
            getHierarchy(),
            getArtifacts(),
            getExcludes(),
            getAttributes(),
            ImmutableCapabilities.of(getCapabilities().getCapabilities()),
            dependencies,
            addedByRule,
            isExternalVariant()
        );
    }

    public boolean isAddedByRule() {
        return addedByRule;
    }
}
