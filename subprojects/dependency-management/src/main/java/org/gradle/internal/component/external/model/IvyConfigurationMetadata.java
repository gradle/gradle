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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.ExcludeMetadata;

import java.util.List;

class IvyConfigurationMetadata extends DefaultConfigurationMetadata {
    private final ImmutableList<Exclude> excludes;
    private ModuleExclusion exclusions;

    IvyConfigurationMetadata(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableList<String> hierarchy, List<Exclude> excludes, ImmutableList<ModuleComponentArtifactMetadata> artifacts) {
        super(componentId, name, transitive, visible, hierarchy, artifacts);
        ImmutableList.Builder<Exclude> filtered = ImmutableList.builder();
        for (Exclude exclude : excludes) {
            for (String config : exclude.getConfigurations()) {
                if (hierarchy.contains(config)) {
                    filtered.add(exclude);
                    break;
                }
            }
        }
        this.excludes = filtered.build();
    }

    @Override
    public ImmutableList<ExcludeMetadata> getExcludes() {
        return ImmutableList.<ExcludeMetadata>copyOf(excludes);
    }
}
