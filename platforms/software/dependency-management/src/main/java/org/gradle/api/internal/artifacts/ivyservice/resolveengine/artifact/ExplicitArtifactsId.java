/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.model.VariantResolveMetadata;

/**
 * Identifier for a set of artifacts resolved directly from a component.
 */
class ExplicitArtifactsId implements VariantResolveMetadata.Identifier {

    private final ComponentIdentifier componentId;
    private final ImmutableList<ComponentArtifactIdentifier> artifactIds;
    private final int hashCode;

    public ExplicitArtifactsId(
        ComponentIdentifier componentId,
        ImmutableList<ComponentArtifactIdentifier> artifactIds
    ) {
        this.componentId = componentId;
        this.artifactIds = artifactIds;
        this.hashCode = computeHashCode(componentId, artifactIds);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ExplicitArtifactsId that = (ExplicitArtifactsId) o;
        return componentId.equals(that.componentId) && artifactIds.equals(that.artifactIds);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private static int computeHashCode(
        ComponentIdentifier componentIdentifier,
        ImmutableList<ComponentArtifactIdentifier> artifactIds
    ) {
        int result = componentIdentifier.hashCode();
        result = 31 * result + artifactIds.hashCode();
        return result;
    }

}
