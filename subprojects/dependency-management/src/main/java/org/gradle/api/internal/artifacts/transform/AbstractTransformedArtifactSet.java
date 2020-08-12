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
    private final Transformation transformation;
    private final TransformationNodeRegistry transformationNodeRegistry;
    private final ExecutionGraphDependenciesResolver dependenciesResolver;

    public AbstractTransformedArtifactSet(
        ComponentIdentifier componentIdentifier,
        ResolvedArtifactSet delegate,
        ImmutableAttributes targetVariantAttributes,
        Transformation transformation,
        ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory,
        TransformationNodeRegistry transformationNodeRegistry
    ) {
        this.delegate = delegate;
        this.targetVariantAttributes = targetVariantAttributes;
        this.transformation = transformation;
        this.transformationNodeRegistry = transformationNodeRegistry;
        this.dependenciesResolver = dependenciesResolverFactory.create(componentIdentifier);
    }

    public ImmutableAttributes getTargetVariantAttributes() {
        return targetVariantAttributes;
    }

    public Transformation getTransformation() {
        return transformation;
    }

    public ExecutionGraphDependenciesResolver getDependenciesResolver() {
        return dependenciesResolver;
    }

    @Override
    public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
        FileCollectionStructureVisitor.VisitType visitType = listener.prepareForVisit(this);
        if (visitType == FileCollectionStructureVisitor.VisitType.NoContents) {
            return visitor -> visitor.endVisitCollection(this);
        }

        // Isolate the transformation parameters, if not already done
        transformation.isolateParameters();

        Map<ComponentArtifactIdentifier, TransformationResult> artifactResults = Maps.newConcurrentMap();
        Completion result = delegate.startVisit(actions, new TransformingAsyncArtifactListener(transformation, actions, artifactResults, dependenciesResolver, transformationNodeRegistry));
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
