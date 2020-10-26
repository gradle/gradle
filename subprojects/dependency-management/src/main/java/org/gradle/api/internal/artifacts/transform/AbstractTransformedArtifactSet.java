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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.util.Map;

/**
 * Transformed artifact set that performs the transformation itself when visited.
 */
public abstract class AbstractTransformedArtifactSet implements ResolvedArtifactSet, FileCollectionInternal.Source {
    private final ResolvedArtifactSet delegate;
    private final ImmutableAttributes targetVariantAttributes;
    private final ImmutableList<BoundTransformationStep> steps;

    public AbstractTransformedArtifactSet(
        ComponentIdentifier componentIdentifier,
        ResolvedArtifactSet delegate,
        ImmutableAttributes targetVariantAttributes,
        Transformation transformation,
        ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory
    ) {
        this.delegate = delegate;
        this.targetVariantAttributes = targetVariantAttributes;
        TransformUpstreamDependenciesResolver dependenciesResolver = dependenciesResolverFactory.create(componentIdentifier, transformation);
        ImmutableList.Builder<BoundTransformationStep> builder = ImmutableList.builder();
        transformation.visitTransformationSteps(transformationStep -> builder.add(new BoundTransformationStep(transformationStep, dependenciesResolver.dependenciesFor(transformationStep))));
        this.steps = builder.build();
    }

    public AbstractTransformedArtifactSet(
        ResolvedArtifactSet delegate,
        ImmutableAttributes targetVariantAttributes,
        ImmutableList<BoundTransformationStep> steps
    ) {
        this.delegate = delegate;
        this.targetVariantAttributes = targetVariantAttributes;
        this.steps = steps;
    }

    public ImmutableAttributes getTargetVariantAttributes() {
        return targetVariantAttributes;
    }

    public ImmutableList<BoundTransformationStep> getSteps() {
        return steps;
    }

    @Override
    public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
        FileCollectionStructureVisitor.VisitType visitType = listener.prepareForVisit(this);
        if (visitType == FileCollectionStructureVisitor.VisitType.NoContents) {
            return visitor -> visitor.endVisitCollection(this);
        }

        // Isolate the transformation parameters, if not already done
        for (BoundTransformationStep step : steps) {
            step.getTransformation().isolateParametersIfNotAlready();
            step.getUpstreamDependencies().finalizeIfNotAlready();
        }

        Map<ComponentArtifactIdentifier, TransformationResult> artifactResults = Maps.newConcurrentMap();
        Completion result = delegate.startVisit(actions, new TransformingAsyncArtifactListener(steps, actions, artifactResults));
        return new TransformCompletion(result, targetVariantAttributes, artifactResults);
    }

    @Override
    public void visitLocalArtifacts(LocalArtifactVisitor visitor) {
        // Should never be called
        throw new IllegalStateException();
    }

    @Override
    public void visitExternalArtifacts(Action<ResolvableArtifact> visitor) {
        // Should never be called
        throw new IllegalStateException();
    }
}
