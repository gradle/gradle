/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildDependenciesVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;

class ConsumerProvidedResolvedVariant implements ResolvedArtifactSet {
    private final ResolvedArtifactSet delegate;
    private final AttributeContainerInternal attributes;
    private final ArtifactTransformer transform;
    private final Map<ComponentArtifactIdentifier, TransformOperation> preCalculatedResults;

    ConsumerProvidedResolvedVariant(ResolvedArtifactSet delegate, AttributeContainerInternal target, ArtifactTransformer transform, @Nullable Map<ComponentArtifactIdentifier, TransformOperation> preCalculatedResults) {
        this.delegate = delegate;
        this.attributes = target;
        this.transform = transform;
        this.preCalculatedResults = preCalculatedResults;
    }

    @Override
    public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
        if (preCalculatedResults != null) {
            Completion result = delegate.startVisit(actions, listener);
            return new TransformingResult(result, preCalculatedResults, ImmutableMap.<File, TransformFileOperation>of());
        }
        Map<ComponentArtifactIdentifier, TransformArtifactOperation> artifactResults = Maps.newConcurrentMap();
        Map<File, TransformFileOperation> fileResults = Maps.newConcurrentMap();
        Completion result = delegate.startVisit(actions, new TransformingAsyncArtifactListener(transform, listener, actions, artifactResults, fileResults));
        return new TransformingResult(result, artifactResults, fileResults);
    }

    @Override
    public void collectBuildDependencies(BuildDependenciesVisitor visitor) {
        visitor.visitDependency(new ArtifactTransformDependency(transform, delegate));
    }

    private class TransformingResult implements Completion {
        private final Completion result;
        private final Map<ComponentArtifactIdentifier, ? extends TransformOperation> artifactResults;
        private final Map<File, ? extends TransformOperation> fileResults;

        TransformingResult(Completion result, Map<ComponentArtifactIdentifier, ? extends TransformOperation> artifactResults, Map<File, ? extends TransformOperation> fileResults) {
            this.result = result;
            this.artifactResults = artifactResults;
            this.fileResults = fileResults;
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            result.visit(new ArtifactTransformingVisitor(visitor, attributes, artifactResults, fileResults));
        }
    }
}
