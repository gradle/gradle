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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.transform.ArtifactTransformDependencies;
import org.gradle.api.internal.attributes.ImmutableAttributes;

class ArtifactTransformDependenciesProvider {

    static final ArtifactTransformDependenciesProvider EMPTY = new ArtifactTransformDependenciesProvider(null, null) {
        @Override
        ArtifactTransformDependencies forAttributes(ImmutableAttributes attributes) {
            return ArtifactTransformDependencies.EMPTY;
        }
    };

    private final ComponentArtifactIdentifier artifactId;
    private final ResolvableDependencies resolvableDependencies;

    ArtifactTransformDependenciesProvider(ComponentArtifactIdentifier artifactId, ResolvableDependencies resolvableDependencies) {
        this.artifactId = artifactId;
        this.resolvableDependencies = resolvableDependencies;
    }

    ArtifactTransformDependencies forAttributes(ImmutableAttributes attributes) {
        return new DefaultArtifactTransformDependencies(artifactId, resolvableDependencies, attributes);
    }
}
