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

package org.gradle.internal.resolve.resolver;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult;

import java.util.List;
import java.util.Set;

/**
 * Default implementation of {@link ComponentArtifactResolver}.
 */
public class DefaultComponentArtifactResolver implements ComponentArtifactResolver {

    private final ComponentArtifactResolveMetadata component;
    private final ArtifactResolver artifactResolver;

    public DefaultComponentArtifactResolver(ComponentArtifactResolveMetadata component, ArtifactResolver artifactResolver) {
        this.component = component;
        this.artifactResolver = artifactResolver;
    }

    @Override
    public Set<ResolvableArtifact> resolveArtifacts(List<? extends ComponentArtifactMetadata> artifacts) {
        ImmutableSet.Builder<ResolvableArtifact> resolvedArtifacts = ImmutableSet.builder();
        for (ComponentArtifactMetadata artifact : artifacts) {
            DefaultBuildableArtifactResolveResult result = new DefaultBuildableArtifactResolveResult();
            artifactResolver.resolveArtifact(component, artifact, result);
            if (artifact.isOptionalArtifact()) {
                try {
                    // probe if the artifact exists
                    result.getResult().getFile();
                } catch (Exception e) {
                    // Optional artifact is not available
                    continue;
                }
            }
            resolvedArtifacts.add(result.getResult());
        }
        return resolvedArtifacts.build();
    }
}
