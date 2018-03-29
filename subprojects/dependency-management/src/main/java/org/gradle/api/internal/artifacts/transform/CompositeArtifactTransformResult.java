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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;

import java.util.List;

public class CompositeArtifactTransformResult implements ArtifactTransformResult {
    private final List<ArtifactTransformResult> results;

    public static ArtifactTransformResult create(List<ArtifactTransformResult> results) {
        if (results.size() == 1) {
            return results.iterator().next();
        }
        return new CompositeArtifactTransformResult(results);
    }

    private CompositeArtifactTransformResult(List<ArtifactTransformResult> results) {
        this.results = results;
    }

    @Override
    public ResolvedArtifactSet.Completion getResult(AttributeContainerInternal attributes) {
        return new CompositeResult(results, attributes);
    }

    private static class CompositeResult implements ResolvedArtifactSet.Completion {

        private final List<ArtifactTransformResult> results;
        private final AttributeContainerInternal attributes;

        CompositeResult(List<ArtifactTransformResult> results, AttributeContainerInternal attributes) {
            this.results = results;
            this.attributes = attributes;
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            for (ArtifactTransformResult result : results) {
                result.getResult(attributes).visit(visitor);
            }
        }
    }

}
