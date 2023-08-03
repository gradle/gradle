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

package org.gradle.internal.component.local.model;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.CalculatedValue;

/**
 * Implementation of {@link VariantResolveMetadata} which allows variant artifacts to be calculated lazily
 * while holding a project lock.
 */
public final class LocalVariantMetadata extends DefaultVariantMetadata {
    private final CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> artifacts;

    public LocalVariantMetadata(String name, Identifier identifier, DisplayName displayName, ImmutableAttributes attributes, ImmutableCapabilities capabilities, CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> artifacts) {
        super(name, identifier, displayName, attributes, ImmutableList.of(), capabilities);
        this.artifacts = artifacts;
    }

    public LocalVariantMetadata prepareToResolveArtifacts() {
        artifacts.finalizeIfNotAlready();
        return this;
    }

    @Override
    public boolean isEligibleForCaching() {
        return true;
    }

    @Override
    public ImmutableList<LocalComponentArtifactMetadata> getArtifacts() {
        return artifacts.get();
    }
}
