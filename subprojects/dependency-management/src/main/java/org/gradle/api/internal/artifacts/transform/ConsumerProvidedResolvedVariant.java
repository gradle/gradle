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
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Transformed artifact set that performs the transformation itself when requested.
 */
public class ConsumerProvidedResolvedVariant implements ResolvedArtifactSet, ConsumerProvidedVariantFiles {
    private final ComponentIdentifier componentIdentifier;
    private final ResolvedArtifactSet delegate;
    private final AttributeContainerInternal attributes;
    private final Transformation transformation;
    private final ExtraExecutionGraphDependenciesResolverFactory resolverFactory;
    private final TransformationNodeRegistry transformationNodeRegistry;

    public ConsumerProvidedResolvedVariant(
        ComponentIdentifier componentIdentifier,
        ResolvedArtifactSet delegate,
        AttributeContainerInternal target,
        Transformation transformation,
        ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory,
        TransformationNodeRegistry transformationNodeRegistry
    ) {
        this.componentIdentifier = componentIdentifier;
        this.delegate = delegate;
        this.attributes = target;
        this.transformation = transformation;
        this.resolverFactory = dependenciesResolverFactory;
        this.transformationNodeRegistry = transformationNodeRegistry;
    }

    @Override
    public ImmutableAttributes getTargetVariantAttributes() {
        return attributes.asImmutable();
    }

    @Override
    public DisplayName getTargetVariantName() {
        return Describables.of(componentIdentifier, attributes);
    }

    @Override
    public String toString() {
        return getTargetVariantName().getCapitalizedDisplayName();
    }

    @Override
    public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
        FileCollectionStructureVisitor.VisitType visitType = listener.prepareForVisit(this);
        if (visitType == FileCollectionStructureVisitor.VisitType.NoContents) {
            return visitor -> visitor.endVisitCollection(ConsumerProvidedResolvedVariant.this);
        }
        Map<ComponentArtifactIdentifier, TransformationResult> artifactResults = Maps.newConcurrentMap();
        Completion result = delegate.startVisit(actions, new TransformingAsyncArtifactListener(transformation, actions, artifactResults, getDependenciesResolver(), transformationNodeRegistry));
        return new TransformCompletion(result, attributes, artifactResults);
    }

    @Override
    public void visitLocalArtifacts(LocalArtifactVisitor listener) {
        // Cannot visit local artifacts until transform has been executed
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        Collection<TransformationNode> scheduledNodes = transformationNodeRegistry.getOrCreate(delegate, transformation, getDependenciesResolver());
        if (!scheduledNodes.isEmpty()) {
            context.add(new DefaultTransformationDependency(scheduledNodes));
        }
    }

    @Override
    public Collection<TransformationNode> getScheduledNodes() {
        // Only care about transformed project outputs. For everything else, calculate the value eagerly
        AtomicReference<Boolean> hasProjectArtifacts = new AtomicReference<>(false);
        delegate.visitLocalArtifacts(artifact -> {
            if (artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier) {
                hasProjectArtifacts.set(true);
            }
        });
        if (hasProjectArtifacts.get()) {
            return transformationNodeRegistry.getOrCreate(delegate, transformation, getDependenciesResolver());
        } else {
            return Collections.emptySet();
        }
    }

    private ExecutionGraphDependenciesResolver getDependenciesResolver() {
        return resolverFactory.create(componentIdentifier);
    }
}
