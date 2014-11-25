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
import org.gradle.api.artifacts.result.*;
import org.gradle.api.component.Artifact;

import java.io.File;
import java.util.Set;

public class DefaultArtifactResolutionResult implements ArtifactResolutionResult {
    private final Set<ComponentResult> componentResults;

    public DefaultArtifactResolutionResult(Set<ComponentResult> componentResults) {
        this.componentResults = componentResults;
    }

    public Set<ComponentResult> getComponents() {
        return componentResults;
    }

    public Set<ComponentArtifactsResult> getResolvedComponents() {
        Set<ComponentArtifactsResult> resolvedComponentResults = Sets.newLinkedHashSet();
        for (ComponentResult componentResult : componentResults) {
            if (componentResult instanceof ComponentArtifactsResult) {
                resolvedComponentResults.add((ComponentArtifactsResult) componentResult);
            }
        }
        return resolvedComponentResults;
    }

    public Set<File> getArtifactFiles() {
        Set<File> artifactFiles = Sets.newLinkedHashSet();

        for (ComponentArtifactsResult componentArtifactsResult : getResolvedComponents()) {
            Set<ArtifactResult> artifactResults = componentArtifactsResult.getArtifacts(Artifact.class);

            for(ArtifactResult artifactResult : artifactResults) {
                if(artifactResult instanceof ResolvedArtifactResult) {
                    artifactFiles.add(((ResolvedArtifactResult)artifactResult).getFile());
                } else if(artifactResult instanceof UnresolvedArtifactResult) {
                    throw new UnresolvedArtifactFileException("Failed to resolve artifact file", ((UnresolvedArtifactResult)artifactResult).getFailure());
                }
            }
        }

        return artifactFiles;
    }
}
