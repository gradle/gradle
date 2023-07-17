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

package org.gradle.internal.component.external.model;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.DefaultComponentGraphResolveState;

/**
 * <p>The aim is to create only a single instance of this type per component and reuse that for all resolution that happens in a build tree. This isn't quite the case yet.
 */
public class DefaultModuleComponentGraphResolveState extends DefaultComponentGraphResolveState<ModuleComponentGraphResolveMetadata, ModuleComponentResolveMetadata> implements ModuleComponentGraphResolveState {
    public DefaultModuleComponentGraphResolveState(long instanceId, ModuleComponentResolveMetadata metadata, AttributeDesugaring attributeDesugaring, ComponentIdGenerator idGenerator) {
        super(instanceId, metadata, metadata, attributeDesugaring, idGenerator);
    }

    @Override
    public ModuleComponentIdentifier getId() {
        return getArtifactMetadata().getId();
    }

    @Override
    public ModuleComponentResolveMetadata getModuleResolveMetadata() {
        return getArtifactMetadata();
    }
}
