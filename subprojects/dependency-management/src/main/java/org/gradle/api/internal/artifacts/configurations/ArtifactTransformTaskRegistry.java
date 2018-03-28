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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformTask;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformer;
import org.gradle.api.internal.attributes.AttributeContainerInternal;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@NonNullApi
public class ArtifactTransformTaskRegistry {

    private final Map<ArtifactTransformTaskKey, ArtifactTransformTask> tasks = new HashMap<ArtifactTransformTaskKey, ArtifactTransformTask>();

    public void register(ResolvedArtifactSet delegate, AttributeContainerInternal attributes, ArtifactTransformer transform, ArtifactTransformTask task) {
        tasks.put(new ArtifactTransformTaskKey(delegate, attributes, transform), task);
    }

    @Nullable
    public ArtifactTransformTask get(ResolvedArtifactSet delegate, AttributeContainerInternal attributes, ArtifactTransformer transform) {
        return tasks.get(new ArtifactTransformTaskKey(delegate, attributes, transform));
    }

    private static class ArtifactTransformTaskKey {
        private final ResolvedArtifactSet delegate;
        private final AttributeContainerInternal attributes;
        private final ArtifactTransformer transform;

        public ArtifactTransformTaskKey(ResolvedArtifactSet delegate, AttributeContainerInternal attributes, ArtifactTransformer transform) {
            this.delegate = delegate;
            this.attributes = attributes;
            this.transform = transform;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ArtifactTransformTaskKey that = (ArtifactTransformTaskKey) o;

            if (!delegate.equals(that.delegate)) {
                return false;
            }
            if (!attributes.equals(that.attributes)) {
                return false;
            }
            return transform.equals(that.transform);
        }

        @Override
        public int hashCode() {
            int result = delegate.hashCode();
            result = 31 * result + attributes.hashCode();
            result = 31 * result + transform.hashCode();
            return result;
        }
    }
}
