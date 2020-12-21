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

import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DefaultTransformationNodeRegistry implements TransformationNodeRegistry {
    private final Map<ArtifactTransformKey, TransformationNode> transformations = Maps.newConcurrentMap();
    private final BuildOperationExecutor buildOperationExecutor;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    public DefaultTransformationNodeRegistry(BuildOperationExecutor buildOperationExecutor, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    @Override
    public Collection<TransformationNode> getOrCreate(ResolvedArtifactSet artifactSet, Transformation transformation, TransformUpstreamDependenciesResolver dependenciesResolver) {
        final List<Equivalence.Wrapper<TransformationStep>> transformationChain = unpackTransformation(transformation);
        final ImmutableList.Builder<TransformationNode> builder = ImmutableList.builder();
        artifactSet.visitTransformSources(new ResolvedArtifactSet.TransformSourceVisitor() {
            @Override
            public void visitArtifact(ResolvableArtifact artifact) {
                TransformationNode transformationNode = getOrCreateInternal(artifact, transformationChain, dependenciesResolver);
                builder.add(transformationNode);
            }

            @Override
            public void visitTransform(TransformationNode source) {
                throw new UnsupportedOperationException();
            }
        });
        return builder.build();
    }

    private TransformationNode getOrCreateInternal(ResolvableArtifact artifact, List<Equivalence.Wrapper<TransformationStep>> transformationChain, TransformUpstreamDependenciesResolver dependenciesResolver) {
        ArtifactTransformKey key = new ArtifactTransformKey(artifact.getId(), transformationChain);
        TransformationNode transformationNode = transformations.get(key);
        if (transformationNode == null) {
            if (transformationChain.size() == 1) {
                TransformationStep transformationStep = transformationChain.get(0).get();
                TransformUpstreamDependencies upstreamDependencies = dependenciesResolver.dependenciesFor(transformationStep);
                transformationNode = TransformationNode.initial(transformationStep, artifact, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
            } else {
                TransformationNode previous = getOrCreateInternal(artifact, transformationChain.subList(0, transformationChain.size() - 1), dependenciesResolver);
                TransformationStep transformationStep = transformationChain.get(transformationChain.size() - 1).get();
                TransformUpstreamDependencies upstreamDependencies = dependenciesResolver.dependenciesFor(transformationStep);
                transformationNode = TransformationNode.chained(transformationStep, previous, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
            }
            transformations.put(key, transformationNode);
        }
        return transformationNode;
    }

    private static List<Equivalence.Wrapper<TransformationStep>> unpackTransformation(Transformation transformation) {
        final ImmutableList.Builder<Equivalence.Wrapper<TransformationStep>> builder = ImmutableList.builder();
        transformation.visitTransformationSteps(transformation1 -> builder.add(TransformationStep.FOR_SCHEDULING.wrap(transformation1)));
        return builder.build();
    }

    private static class ArtifactTransformKey {
        private final Object artifactSetId;
        private final List<Equivalence.Wrapper<TransformationStep>> transformations;

        private ArtifactTransformKey(Object artifactSetId, List<Equivalence.Wrapper<TransformationStep>> transformations) {
            this.artifactSetId = artifactSetId;
            this.transformations = transformations;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ArtifactTransformKey that = (ArtifactTransformKey) o;

            if (!artifactSetId.equals(that.artifactSetId)) {
                return false;
            }
            return transformations.equals(that.transformations);
        }

        @Override
        public int hashCode() {
            int result = artifactSetId.hashCode();
            result = 31 * result + transformations.hashCode();
            return result;
        }
    }
}
