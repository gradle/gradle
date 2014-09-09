/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.result;

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.artifacts.result.ArtifactResult;

import java.util.Set;

public class DefaultComponentArtifactsResult implements ComponentArtifactsResult {
    private final ComponentIdentifier componentIdentifier;
    private final Set<ArtifactResult> artifactResults = Sets.newHashSet();

    public DefaultComponentArtifactsResult(ComponentIdentifier componentIdentifier) {
        this.componentIdentifier = componentIdentifier;
    }

    public ComponentIdentifier getId() {
        return componentIdentifier;
    }

    public Set<ArtifactResult> getArtifacts(Class<? extends Artifact> type) {
        Set<ArtifactResult> matching = Sets.newLinkedHashSet();
        for (ArtifactResult artifactResult : artifactResults) {
            if (type.isAssignableFrom(artifactResult.getType())) {
                matching.add(artifactResult);
            }
        }
        return matching;
    }

    public void addArtifact(ArtifactResult artifact) {
        artifactResults.add(artifact);
    }
}
