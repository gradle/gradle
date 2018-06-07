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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildDependenciesVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.Map;

/**
 * Transformed artifact set that uses the result of an already executed transformation.
 */
public class AlreadyTransformedResolvedVariant implements ResolvedArtifactSet {
    private final ResolvedArtifactSet delegate;
    private final AttributeContainerInternal attributes;
    private final ArtifactTransformer transform;
    private final Map<ComponentArtifactIdentifier, ArtifactTransformResult> preCalculatedResults;

    public AlreadyTransformedResolvedVariant(ResolvedArtifactSet delegate, AttributeContainerInternal attributes, ArtifactTransformer transform, Map<ComponentArtifactIdentifier, ArtifactTransformResult> preCalculatedResults) {
        this.delegate = delegate;
        this.attributes = attributes;
        this.transform = transform;
        this.preCalculatedResults = preCalculatedResults;
    }

    @Override
    public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
        Completion delegateCompletion = delegate.startVisit(actions, listener);
        return new TransformCompletion(delegateCompletion, attributes, preCalculatedResults, ImmutableMap.<File, TransformFileOperation>of());
    }

    @Override
    public void collectBuildDependencies(BuildDependenciesVisitor visitor) {
        visitor.visitDependency(new ArtifactTransformDependency(transform, delegate));
    }
}
