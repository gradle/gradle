/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.component.local.model;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;

/**
 * Implementation of {@link ComponentGraphResolveMetadata} for local components.
 */
public final class LocalComponentGraphResolveMetadata implements ComponentGraphResolveMetadata {

    private final ComponentIdentifier componentId;
    private final ModuleIdentifier moduleId;
    private final String version;
    private final String status;
    private final ImmutableAttributesSchema attributesSchema;


    public LocalComponentGraphResolveMetadata(
        ModuleIdentifier moduleId,
        String version,
        ComponentIdentifier componentId,
        String status,
        ImmutableAttributesSchema attributesSchema
    ) {
        this.moduleId = moduleId;
        this.version = version;
        this.componentId = componentId;
        this.status = status;
        this.attributesSchema = attributesSchema;
    }

    @Override
    public ComponentIdentifier getId() {
        return componentId;
    }

    @Override
    public ModuleIdentifier getModuleId() {
        return moduleId;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return componentId.getDisplayName();
    }

    @Override
    public boolean isChanging() {
        return false;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public ImmutableList<? extends VirtualComponentIdentifier> getPlatformOwners() {
        return ImmutableList.of();
    }

    @Override
    public ImmutableAttributesSchema getAttributesSchema() {
        return attributesSchema;
    }

}
