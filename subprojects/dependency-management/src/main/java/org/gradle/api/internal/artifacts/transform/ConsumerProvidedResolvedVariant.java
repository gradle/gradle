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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactBackedResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildDependenciesVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.Map;

class ConsumerProvidedResolvedVariant implements ResolvedArtifactSet {
    private final ResolvedArtifactSet delegate;
    private final AttributeContainerInternal attributes;
    private final ArtifactTransformer transform;
    private ArtifactTransformDependency artifactTransformDependency;

    ConsumerProvidedResolvedVariant(ResolvedArtifactSet delegate, AttributeContainerInternal target, ArtifactTransformer transform) {
        this.delegate = delegate;
        this.attributes = target;
        this.transform = transform;
    }

    @Override
    public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
        return artifactTransformDependency.getTransformTask().getResult();
//        Map<ResolvableArtifact, TransformArtifactOperation> artifactResults = new ConcurrentHashMap<ResolvableArtifact, TransformArtifactOperation>();
//        Map<File, TransformFileOperation> fileResults = new ConcurrentHashMap<File, TransformFileOperation>();
//        Completion result = delegate.startVisit(actions, new TransformingAsyncArtifactListener(transform, listener, actions, artifactResults, fileResults));
//        return new TransformingResult(result, artifactResults, fileResults, attributes);
    }

    @Override
    public void collectBuildDependencies(BuildDependenciesVisitor visitor) {
        System.out.println(delegate.getClass());
        if (delegate instanceof ArtifactBackedResolvedVariant.SingleArtifactSet) {
            System.out.println(((ArtifactBackedResolvedVariant.SingleArtifactSet) delegate).artifact.getId().getDisplayName());
            System.out.println(((ArtifactBackedResolvedVariant.SingleArtifactSet) delegate).artifact.getId().getComponentIdentifier());
        }
        artifactTransformDependency = new ArtifactTransformDependency(transform, delegate, attributes);
        visitor.visitDependency(artifactTransformDependency);
        // FIXME: Task needs to collect the delegate dependencies and create tranforms for them.
//        delegate.collectBuildDependencies(visitor);
    }

    public static class TransformingResult implements Completion {
        private final Completion result;
        private final Map<ResolvableArtifact, TransformArtifactOperation> artifactResults;
        private final Map<File, TransformFileOperation> fileResults;
        private final AttributeContainerInternal attributes;

        public TransformingResult(Completion result, Map<ResolvableArtifact, TransformArtifactOperation> artifactResults, Map<File, TransformFileOperation> fileResults, AttributeContainerInternal attributes) {
            this.result = result;
            this.artifactResults = artifactResults;
            this.fileResults = fileResults;
            this.attributes = attributes;
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            result.visit(new ArtifactTransformingVisitor(visitor, attributes, artifactResults, fileResults));
        }
    }
}
