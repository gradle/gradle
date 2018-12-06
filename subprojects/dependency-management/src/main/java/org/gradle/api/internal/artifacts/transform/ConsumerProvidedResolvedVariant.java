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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.Map;

/**
 * Transformed artifact set that performs the transformation itself when requested.
 */
public class ConsumerProvidedResolvedVariant implements ResolvedArtifactSet {
    private final ComponentIdentifier componentIdentifier;
    private final ResolvedArtifactSet delegate;
    private final AttributeContainerInternal attributes;
    private final Transformation transformation;
    private final ExtraExecutionGraphDependenciesResolverFactory resolverFactory;

    public ConsumerProvidedResolvedVariant(ComponentIdentifier componentIdentifier, ResolvedArtifactSet delegate, AttributeContainerInternal target, Transformation transformation, ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory) {
        this.componentIdentifier = componentIdentifier;
        this.delegate = delegate;
        this.attributes = target;
        this.transformation = transformation;
        this.resolverFactory = dependenciesResolverFactory;
    }

    @Override
    public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
        Map<ComponentArtifactIdentifier, TransformationOperation> artifactResults = Maps.newConcurrentMap();
        Map<File, TransformationOperation> fileResults = Maps.newConcurrentMap();
        Completion result = delegate.startVisit(actions, new TransformingAsyncArtifactListener(transformation, listener, actions, artifactResults, fileResults, getDependenciesResolver()));
        return new TransformCompletion(result, attributes, artifactResults, fileResults);
    }

    @Override
    public void visitLocalArtifacts(LocalArtifactVisitor listener) {
        // Cannot visit local artifacts until transform has been executed
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(new DefaultTransformationDependency(transformation, delegate, getDependenciesResolver()));
    }

    private ExecutionGraphDependenciesResolver getDependenciesResolver() {
        return resolverFactory.create(componentIdentifier);
    }
}
