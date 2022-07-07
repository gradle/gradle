/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

import javax.annotation.Nullable;

public class DefaultComponentGraphResolveState<T extends ComponentResolveMetadata> implements ComponentGraphResolveState {
    private final T metadata;

    public DefaultComponentGraphResolveState(T metadata) {
        this.metadata = metadata;
    }

    @Override
    public ComponentIdentifier getId() {
        return metadata.getId();
    }

    @Nullable
    @Override
    public ModuleSources getSources() {
        return metadata.getSources();
    }

    @Override
    public ComponentGraphResolveMetadata getMetadata() {
        return metadata;
    }

    @Override
    public T getArtifactResolveMetadata() {
        return metadata;
    }

    @Override
    public VariantArtifactsGraphResolveMetadata resolveArtifactsFor(VariantGraphResolveMetadata variant) {
        return (VariantArtifactsGraphResolveMetadata) variant;
    }

    @Nullable
    @Override
    public ComponentGraphResolveState maybeAsLenientPlatform(ModuleComponentIdentifier componentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier) {
        return null;
    }
}
