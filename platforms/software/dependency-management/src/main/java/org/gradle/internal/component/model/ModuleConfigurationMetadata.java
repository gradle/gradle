/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ExternalModuleVariantGraphResolveMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.UrlBackedArtifactMetadata;

import java.util.List;

public interface ModuleConfigurationMetadata extends ConfigurationMetadata, ConfigurationGraphResolveMetadata, ExternalModuleVariantGraphResolveMetadata, VariantResolveMetadata {

    @Override
    List<? extends ModuleDependencyMetadata> getDependencies();

    ModuleComponentIdentifier getComponentId();

    @Override
    default ModuleComponentArtifactMetadata artifact(IvyArtifactName artifact) {
        ModuleComponentIdentifier componentId = getComponentId();
        String fileName = DefaultModuleComponentArtifactIdentifier.fileName(componentId, artifact);
        for (ComponentArtifactMetadata declared : getArtifacts()) {
            if (declared instanceof UrlBackedArtifactMetadata urlBacked) {
                if (urlBacked.getName().equals(artifact) && urlBacked.getId().getFileName().equals(fileName)) {
                    return urlBacked;
                }
            }
        }
        return new DefaultModuleComponentArtifactMetadata(componentId, artifact);
    }

}
