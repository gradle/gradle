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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.VirtualPlatformState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

public class TestComponentResolutionState implements ComponentResolutionState {
    private ComponentIdentifier componentIdentifier;
    private ModuleIdentifier moduleId;
    private String version;
    private boolean rejected;

    public TestComponentResolutionState(ComponentIdentifier componentIdentifier, ModuleIdentifier moduleId, String version) {
        this.componentIdentifier = componentIdentifier;
        this.moduleId = moduleId;
        this.version = version;
    }

    public TestComponentResolutionState(ModuleVersionIdentifier id) {
        this.componentIdentifier = DefaultModuleComponentIdentifier.newId(id);
        this.moduleId = id.getModule();
        this.version = id.getVersion();
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public ComponentIdentifier getComponentId() {
        return componentIdentifier;
    }

    @Override
    public ModuleResolutionState getModule() {
        return new ModuleResolutionState() {
            @Override
            public ModuleIdentifier getId() {
                return moduleId;
            }
        };
    }

    @Nullable
    @Override
    public ComponentGraphResolveMetadata getMetadataOrNull() {
        return null;
    }

    @Override
    public void addCause(ComponentSelectionDescriptorInternal componentSelectionDescriptor) {
    }

    @Override
    public void reject() {
        rejected = true;
    }

    @Override
    public boolean isRejected() {
        return rejected;
    }

    @Override
    public Set<VirtualPlatformState> getPlatformOwners() {
        return Collections.emptySet();
    }

    @Override
    public VirtualPlatformState getPlatformState() {
        return null;
    }
}
