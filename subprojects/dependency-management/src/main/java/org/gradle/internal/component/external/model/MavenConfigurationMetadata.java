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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;

class MavenConfigurationMetadata extends DefaultConfigurationMetadata {
    MavenConfigurationMetadata(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableList<MavenConfigurationMetadata> parents, ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts) {
        super(componentId, name, transitive, visible, parents, artifacts);
    }

    @Override
    public ModuleExclusion getExclusions(ModuleExclusions moduleExclusions) {
        return ModuleExclusions.excludeNone();
    }
}
