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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;

/**
 * Metadata for a lenient platform component.
 */
public class LenientPlatformResolveMetadata implements ComponentGraphResolveMetadata {

    private final ModuleComponentIdentifier moduleComponentIdentifier;

    LenientPlatformResolveMetadata(ModuleComponentIdentifier moduleComponentIdentifier) {
        this.moduleComponentIdentifier = moduleComponentIdentifier;
    }

    @Override
    public ModuleComponentIdentifier getId() {
        return moduleComponentIdentifier;
    }

    @Override
    public ModuleIdentifier getModuleId() {
        return moduleComponentIdentifier.getModuleIdentifier();
    }

    @Override
    public String getVersion() {
        return moduleComponentIdentifier.getVersion();
    }

    @Override
    public ImmutableAttributesSchema getAttributesSchema() {
        return ImmutableAttributesSchema.EMPTY;
    }

    @Override
    public boolean isChanging() {
        return false;
    }

    @Override
    public String getStatus() {
        return null;
    }

    @Override
    public ImmutableList<? extends VirtualComponentIdentifier> getPlatformOwners() {
        return ImmutableList.of();
    }

}
