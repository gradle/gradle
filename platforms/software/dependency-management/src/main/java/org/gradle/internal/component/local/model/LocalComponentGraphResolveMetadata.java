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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;

/**
 * Implementation of {@link ComponentGraphResolveMetadata} for local components.
 */
public final class LocalComponentGraphResolveMetadata implements ComponentGraphResolveMetadata {

    private final ComponentIdentifier componentId;
    private final ModuleVersionIdentifier moduleVersionId;
    private final String status;
    private final AttributesSchemaInternal attributesSchema;

    public LocalComponentGraphResolveMetadata(
        ModuleVersionIdentifier moduleVersionId,
        ComponentIdentifier componentId,
        String status,
        AttributesSchemaInternal attributesSchema
    ) {
        this.moduleVersionId = moduleVersionId;
        this.componentId = componentId;
        this.status = status;
        this.attributesSchema = attributesSchema;
    }

    @Override
    public ComponentIdentifier getId() {
        return componentId;
    }

    @Override
    public ModuleVersionIdentifier getModuleVersionId() {
        return moduleVersionId;
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
    public AttributesSchemaInternal getAttributesSchema() {
        return attributesSchema;
    }

}
