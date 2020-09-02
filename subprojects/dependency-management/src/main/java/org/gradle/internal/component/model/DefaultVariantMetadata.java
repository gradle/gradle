/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.DisplayName;

import javax.annotation.Nullable;

public class DefaultVariantMetadata implements VariantResolveMetadata {
    private final String name;
    private final Identifier identifier;
    private final DisplayName displayName;
    private final AttributeContainerInternal attributes;
    private final ImmutableList<? extends ComponentArtifactMetadata> artifacts;
    private final CapabilitiesMetadata capabilitiesMetadata;

    public DefaultVariantMetadata(String name, @Nullable Identifier identifier, DisplayName displayName, AttributeContainerInternal attributes, ImmutableList<? extends ComponentArtifactMetadata> artifacts, @Nullable CapabilitiesMetadata capabilitiesMetadata) {
        this.name = name;
        this.identifier = identifier;
        this.displayName = displayName;
        this.attributes = attributes;
        this.artifacts = artifacts;
        this.capabilitiesMetadata = capabilitiesMetadata;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    @Override
    public DisplayName asDescribable() {
        return displayName;
    }

    @Override
    public AttributeContainerInternal getAttributes() {
        return attributes;
    }

    @Override
    public ImmutableList<? extends ComponentArtifactMetadata> getArtifacts() {
        return artifacts;
    }

    @Override
    public CapabilitiesMetadata getCapabilities() {
        return capabilitiesMetadata;
    }

    @Override
    public boolean isExternalVariant() {
        return false;
    }
}
