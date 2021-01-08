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

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;

import java.util.Collection;

public class DefaultTransformationNodeFactory implements TransformationNodeFactory {
    private final BuildOperationExecutor buildOperationExecutor;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    public DefaultTransformationNodeFactory(BuildOperationExecutor buildOperationExecutor, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    @Override
    public Collection<TransformationNode> create(ResolvedArtifactSet artifactSet, TransformationStep transformationStep, TransformUpstreamDependenciesResolver dependenciesResolver) {
        final ImmutableList.Builder<TransformationNode> builder = ImmutableList.builder();
        artifactSet.visitTransformSources(new ResolvedArtifactSet.TransformSourceVisitor() {
            @Override
            public void visitArtifact(ResolvableArtifact artifact) {
                TransformUpstreamDependencies upstreamDependencies = dependenciesResolver.dependenciesFor(transformationStep);
                TransformationNode transformationNode = TransformationNode.initial(transformationStep, artifact, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
                builder.add(transformationNode);
            }

            @Override
            public void visitTransform(TransformationNode source) {
                TransformUpstreamDependencies upstreamDependencies = dependenciesResolver.dependenciesFor(transformationStep);
                TransformationNode transformationNode = TransformationNode.chained(transformationStep, source, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
                builder.add(transformationNode);
            }
        });
        return builder.build();
    }
}
