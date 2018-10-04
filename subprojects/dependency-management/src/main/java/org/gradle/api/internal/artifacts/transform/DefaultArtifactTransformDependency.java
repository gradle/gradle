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

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;

@NonNullApi
public class DefaultArtifactTransformDependency implements ArtifactTransformDependency {
    private final ArtifactTransformation transform;
    private final ResolvedArtifactSet artifacts;

    public DefaultArtifactTransformDependency(ArtifactTransformation transform, ResolvedArtifactSet artifacts) {
        this.transform = transform;
        this.artifacts = artifacts;
    }

    public ArtifactTransformation getTransform() {
        return transform;
    }

    public ResolvedArtifactSet getArtifacts() {
        return artifacts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultArtifactTransformDependency that = (DefaultArtifactTransformDependency) o;

        if (!transform.equals(that.transform)) {
            return false;
        }
        return artifacts.equals(that.artifacts);
    }

    @Override
    public int hashCode() {
        int result = transform.hashCode();
        result = 31 * result + artifacts.hashCode();
        return result;
    }
}
