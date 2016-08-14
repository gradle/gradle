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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifacts;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class InMemoryArtifactsCache {
    private final Map<ComponentArtifactIdentifier, File> artifacts = new HashMap<ComponentArtifactIdentifier, File>();
    private final Map<ComponentIdentifier, ComponentArtifacts> componentArtifacts = new HashMap<ComponentIdentifier, ComponentArtifacts>();
    private final Map<TypedArtifactsKey, Set<ComponentArtifactMetadata>> typedArtifacts = new HashMap<TypedArtifactsKey, Set<ComponentArtifactMetadata>>();

    public boolean supplyArtifact(ComponentArtifactIdentifier id, BuildableArtifactResolveResult result) {
        File fromCache = artifacts.get(id);
        if (fromCache != null) {
            result.resolved(fromCache);
            return true;
        }
        return false;
    }

    public void newArtifact(ComponentArtifactIdentifier id, BuildableArtifactResolveResult result) {
        if (result.isSuccessful()) {
            artifacts.put(id, result.getResult());
        }
    }

    public boolean supplyArtifacts(ComponentIdentifier component, ArtifactType type, BuildableArtifactSetResolveResult result) {
        Set<ComponentArtifactMetadata> artifacts = typedArtifacts.get(new TypedArtifactsKey(component, type));
        if (artifacts != null) {
            result.resolved(artifacts);
            return true;
        }
        return false;
    }

    public void newArtifacts(ComponentIdentifier component, ArtifactType type, BuildableArtifactSetResolveResult result) {
        if (result.isSuccessful()) {
            this.typedArtifacts.put(new TypedArtifactsKey(component, type), ImmutableSet.copyOf(result.getResult()));
        }
    }

    public boolean supplyArtifacts(ComponentIdentifier component, BuildableComponentArtifactsResolveResult result) {
        ComponentArtifacts artifacts = this.componentArtifacts.get(component);
        if (artifacts != null) {
            result.resolved(artifacts);
            return true;
        }
        return false;
    }

    public void newArtifacts(ComponentIdentifier component, BuildableComponentArtifactsResolveResult result) {
        if (result.isSuccessful()) {
            componentArtifacts.put(component, result.getResult());
        }
    }

    private static class TypedArtifactsKey {
        private final ComponentIdentifier componentId;
        private final ArtifactType type;

        public TypedArtifactsKey(ComponentIdentifier componentId, ArtifactType type) {
            this.componentId = componentId;
            this.type = type;
        }

        @Override
        public boolean equals(Object obj) {
            TypedArtifactsKey other = (TypedArtifactsKey) obj;
            return componentId.equals(other.componentId) && type.equals(other.type);
        }

        @Override
        public int hashCode() {
            return componentId.hashCode() ^ type.hashCode();
        }
    }
}
