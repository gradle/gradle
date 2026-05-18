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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;

public abstract class AbstractComponentGraphResolveState<T extends ComponentGraphResolveMetadata> implements ComponentGraphResolveState, ComponentArtifactResolveState {

    private final long instanceId;
    private final T graphMetadata;
    private final ImmutableCapability implicitCapability;

    public AbstractComponentGraphResolveState(long instanceId, T graphMetadata) {
        this.instanceId = instanceId;
        this.graphMetadata = graphMetadata;
        this.implicitCapability =  DefaultImmutableCapability.defaultCapabilityForComponent(graphMetadata.getModuleVersionId());
    }

    @Override
    public String toString() {
        return getId().toString();
    }

    @Override
    public long getInstanceId() {
        return instanceId;
    }

    @Override
    public ComponentIdentifier getId() {
        return graphMetadata.getId();
    }

    @Override
    public T getMetadata() {
        return graphMetadata;
    }

    @Override
    public boolean isAdHoc() {
        return false;
    }

    @Override
    public ComponentArtifactResolveState prepareForArtifactResolution() {
        return this;
    }

    @Override
    public ImmutableCapability getDefaultCapability() {
        return implicitCapability;
    }

}
