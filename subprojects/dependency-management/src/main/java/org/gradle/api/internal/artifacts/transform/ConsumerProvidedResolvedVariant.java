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

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildDependenciesVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.Map;

/**
 * Transformed artifact set that performs the transformation itself when requested.
 */
public class ConsumerProvidedResolvedVariant implements ResolvedArtifactSet {
    private final ResolvedArtifactSet delegate;
    private final AttributeContainerInternal attributes;
    private final ArtifactTransformation transform;
    private final ArtifactTransformListener transformListener;

    public ConsumerProvidedResolvedVariant(ResolvedArtifactSet delegate, AttributeContainerInternal target, ArtifactTransformation transform, ArtifactTransformListener transformListener) {
        this.delegate = delegate;
        this.attributes = target;
        this.transform = transform;
        this.transformListener = transformListener;
    }

    @Override
    public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
        Map<ComponentArtifactIdentifier, TransformArtifactOperation> artifactResults = Maps.newConcurrentMap();
        Map<File, TransformFileOperation> fileResults = Maps.newConcurrentMap();
        Completion result = delegate.startVisit(actions, new TransformingAsyncArtifactListener(transform, listener, actions, artifactResults, fileResults, transformListener));
        return new TransformCompletion(result, attributes, artifactResults, fileResults);
    }

    @Override
    public void collectBuildDependencies(BuildDependenciesVisitor visitor) {
        visitor.visitDependency(new DefaultArtifactTransformDependency(transform, delegate));
    }
}
